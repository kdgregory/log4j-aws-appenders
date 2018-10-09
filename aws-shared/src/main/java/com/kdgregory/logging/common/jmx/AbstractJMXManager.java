// Copyright (c) Keith D Gregory
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.kdgregory.logging.common.jmx;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.StandardMBean;

import com.kdgregory.logging.aws.internal.AbstractWriterStatistics;
import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  This class manages the relationships between the writer statistics beans
 *  and the JMX server(s) where they should be registered. Servers and beans
 *  may be added or removed in any order; this class ensures that beans will
 *  be registered with any new servers, and any new beans will be registered
 *  with all existing servers.
 *  <p>
 *  This works by using a "marker bean" that is  registered with an MBean server
 *  and also with the singleton instance of this class. Statistics beans are
 *  added to the singleton instance by their appender. Either type of addition
 *  is a trigger to ensure that the statistics beans are registered with all
 *  known servers.
 *  <p>
 *  Subclasses are expected to manage the singleton instance, and to implement
 *  the {@link #toObjectName} method.
 */
public abstract class AbstractJMXManager
{
    // all members are marked protected so that they can be inspected by tests
    
    protected InternalLogger logger;

    protected Map<Object,List<MBeanServer>> knownServers
        = new IdentityHashMap<Object,List<MBeanServer>>();

    protected Map<String,AbstractWriterStatistics> statsBeans
        = new HashMap<String,AbstractWriterStatistics>();

    protected Map<String,Class<?>> statsBeanTypes
        = new HashMap<String,Class<?>>();


    protected AbstractJMXManager(InternalLogger logger)
    {
        this.logger = logger;
    }


//----------------------------------------------------------------------------
//  Methods called by StatisticsMBean
//----------------------------------------------------------------------------

    /**
     *  Adds relationship between a marker bean and an MBeanServer. If any
     *  writer beans are known, they will be registered with the server at
     *  this time.
     *  <p>
     *  Attempting to add the same bean/server more than once is silently
     *  ignored (it should never happen in the real world, so does not rate
     *  a check).
     */
    public synchronized void addMarkerBean(Object bean, MBeanServer server, ObjectName name)
    {
        List<MBeanServer> servers = knownServers.get(bean);
        if (servers == null)
        {
            servers = new ArrayList<MBeanServer>();
            knownServers.put(bean, servers);
        }

        servers.add(server);

        for (String appenderName :  statsBeans.keySet())
        {
            registerAppenderBean(appenderName, server);
        }
    }


    /**
     *  Will deregister a StatisticsMBean instance from all known servers.
     */
    public synchronized void removeMarkerBean(Object bean)
    {
        List<MBeanServer> servers = knownServers.remove(bean);
        if (servers == null)
        {
            logger.warn("JMXManager: attempt to remove unregistered StatisticsMBean");
            return;
        }

        for (MBeanServer server : servers)
        {
            for (String appenderName : statsBeans.keySet())
            {
                unregisterAppenderBean(appenderName, server);
            }
        }
    }

//----------------------------------------------------------------------------
//  Methods called by appender
//----------------------------------------------------------------------------

    /**
     *  Adds a writer's statsbean and its class to the internal tracking tables.
     *  If there are known StatisticsMBean/MBeanServer associations, the stats
     *  bean will also be registered with those servers.
     *  <p>
     *  These tables are managed by appender name. Calling with the same name and
     *  same appender bean is silently ignored. Calling with the same name but a
     *  different appender bean is an error. In the real world, neither of these
     *  cases should happen, because the appender is registered during initialization.
     */
    public synchronized void addStatsBean(String appenderName, AbstractWriterStatistics statsBean, Class<?> statsBeanClass)
    {
        statsBeans.put(appenderName, statsBean);
        statsBeanTypes.put(appenderName, statsBeanClass);

        for (List<MBeanServer> servers : knownServers.values())
        {
            for (MBeanServer server : servers)
            {
                registerAppenderBean(appenderName, server);
            }
        }
    }


    /**
     *  Removes information about a stats bean from the internal tables and deregisters
     *  it from any MBeanServers. This is normally called when the appender is closed.
     */
    public synchronized void removeStatsBean(String appenderName)
    {
        statsBeans.remove(appenderName);
        statsBeanTypes.remove(appenderName);

        for (List<MBeanServer> servers : knownServers.values())
        {
            for (MBeanServer server : servers)
            {
                unregisterAppenderBean(appenderName, server);
            }
        }
    }


//----------------------------------------------------------------------------
//  Internals -- protected so they can be overridden
//----------------------------------------------------------------------------

    /**
     *  Registers an appender's stats bean with a server.
     */
    @SuppressWarnings("rawtypes")
    protected void registerAppenderBean(String appenderName, MBeanServer mbeanServer)
    {
        if (appenderName == null)
        {
            logger.error("JMXManager: attempted to register null appender", null);
            return;
        }

        if (mbeanServer == null)
        {
            logger.error("log4j-aws-appenders JMXManager: attempted to register null server", null);
            return;
        }

        Object statsBean = statsBeans.get(appenderName);
        Class statsBeanClass = statsBeanTypes.get(appenderName);
        if ((statsBean == null) || (statsBeanClass == null))
        {
            logger.error("JMXManager: don't know bean or class for appender: " + appenderName, null);
            return;
        }

        try
        {
            StandardMBean mbean = new StandardMBean(statsBeanClass.cast(statsBean), statsBeanClass, false);
            mbeanServer.registerMBean(mbean, toObjectName(appenderName));
        }
        catch (Exception ex)
        {
            logger.error("JMXManager: failed to register appender statistics with JMX", ex);
        }
    }


    /**
     *  Deregisters an appender's stats bean from a server.
     */
    protected void unregisterAppenderBean(String appenderName, MBeanServer mbeanServer)
    {
        try
        {
            mbeanServer.unregisterMBean(toObjectName(appenderName));
        }
        catch (Exception ex)
        {
            logger.error("JMXManager: failed to unregister appender statistics with JMX", ex);
        }
    }


    /**
     *  Returns a standard JMX ObjectName based on an appender name. This name
     *  follows the format used by the Log4J <code>AppenderDynamicMBean</code>
     *  so that the statistics will appear underneath the appender in the bean
     *  hierarchy.
     */
    protected abstract ObjectName toObjectName(String appenderName)
    throws MalformedObjectNameException;
}
