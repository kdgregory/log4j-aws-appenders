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

package com.kdgregory.log4j.aws.internal.shared;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.StandardMBean;

import org.apache.log4j.helpers.LogLog;

import com.kdgregory.log4j.aws.StatisticsMBean;


/**
 *  This class ensures that appenders will be registered with any MBean servers
 *  where a StatisticsMBean instance has also been registered, regardless of
 *  the order that those objects are initialized/registered.
 *  <p>
 *  This object is implemented as an eagerly-instantiated singleton; for testing
 *  the implementation can be replaced. All methods are synchronized, to avoid
 *  race conditions between appender and StatisticsMBean registration.
 */
public class JMXManager
{
//----------------------------------------------------------------------------
//  Singleton
//----------------------------------------------------------------------------

    private volatile static JMXManager singleton = new JMXManager();

    public static JMXManager getInstance()
    {
        return singleton;
    }


    /**
     *  Replaces the singleton instance with a new instance. This method is
     *  exposed for testing; it should never be called by application code.
     */
    public static void reset(JMXManager newManager)
    {
        singleton = newManager;
    }

//----------------------------------------------------------------------------
//  Data members -- all are marked protected so they can be examined by tests
//----------------------------------------------------------------------------

    protected Map<StatisticsMBean,List<MBeanServer>> knownServers
        = new IdentityHashMap<StatisticsMBean,List<MBeanServer>>();

    protected Map<String,AbstractAppenderStatistics> appenderStatsBeans
        = new HashMap<String,AbstractAppenderStatistics>();

    protected Map<String,Class<?>> appenderStatsBeanTypes
        = new HashMap<String,Class<?>>();


//----------------------------------------------------------------------------
//  Methods called by StatisticsMBean
//----------------------------------------------------------------------------

    /**
     *  Adds relationship between a StatisticsMBean and an MBeanServer to the
     *  internal tables. If any appender beans are known, will register those
     *  beans with the same server.
     *  <p>
     *  The StatisticsMBean table is based on object identity. Attempting to
     *  add the same bean/server more than once is silently ignored (should
     *  never happen in the real world).
     */
    public synchronized void addStatisticsMBean(StatisticsMBean bean, MBeanServer server, ObjectName name)
    {
        List<MBeanServer> servers = knownServers.get(bean);
        if (servers == null)
        {
            servers = new ArrayList<MBeanServer>();
            knownServers.put(bean, servers);
        }

        servers.add(server);

        for (String appenderName :  appenderStatsBeans.keySet())
        {
            registerAppenderBean(appenderName, server);
        }
    }


    /**
     *  Will deregister a StatisticsMBean instance from all known servers.
     */
    public synchronized void removeStatisticsMBean(StatisticsMBean bean)
    {
        List<MBeanServer> servers = knownServers.remove(bean);
        if (servers == null)
        {
            LogLog.warn("JMXManager: attempt to remove unregistered StatisticsMBean");
            return;
        }

        for (MBeanServer server : servers)
        {
            for (String appenderName : appenderStatsBeans.keySet())
            {
                unregisterAppenderBean(appenderName, server);
            }
        }
    }

//----------------------------------------------------------------------------
//  Methods called by AbstractAppender
//----------------------------------------------------------------------------

    /**
     *  Adds an appender's statsbean and its class to the internal tracking tables.
     *  If there are known StatisticsMBean/MBeanServer associations, the appender's
     *  statistics bean will also be registered with those servers.
     *  <p>
     *  These tables are managed by appender name. Calling with the same name and
     *  same appender bean is silently ignored. Calling with the same name but a
     *  different appender bean is an error. In the real world, neither of these
     *  cases should happen, because the appender is registered during initialization.
     */
    public synchronized void addAppender(String appenderName, AbstractAppenderStatistics statsBean, Class<?> statsBeanClass)
    {
        appenderStatsBeans.put(appenderName, statsBean);
        appenderStatsBeanTypes.put(appenderName, statsBeanClass);

        for (List<MBeanServer> servers : knownServers.values())
        {
            for (MBeanServer server : servers)
            {
                registerAppenderBean(appenderName, server);
            }
        }
    }


    /**
     *  Removes information about an appender from the internal tables and deregisters
     *  it from any MBeanServers. This is normally called when the appender is closed.
     */
    public synchronized void removeAppender(String appenderName)
    {
        appenderStatsBeans.remove(appenderName);
        appenderStatsBeanTypes.remove(appenderName);

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
            LogLog.error("log4j-aws-appenders: attempted to register null appender");
            return;
        }

        if (mbeanServer == null)
        {
            LogLog.error("log4j-aws-appenders: attempted to register with null server");
            return;
        }

        Object statsBean = appenderStatsBeans.get(appenderName);
        Class statsBeanClass = appenderStatsBeanTypes.get(appenderName);
        if ((statsBean == null) || (statsBeanClass == null))
        {
            LogLog.error("log4j-aws-appenders: don't know bean or class for appender: " + appenderName);
            return;
        }

        try
        {
            StandardMBean mbean = new StandardMBean(statsBeanClass.cast(statsBean), statsBeanClass, false);
            mbeanServer.registerMBean(mbean, toObjectName(appenderName));
        }
        catch (Exception ex)
        {
            LogLog.warn("failed to register appender statistics with JMX", ex);
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
            LogLog.warn("failed to unregister appender statistics with JMX", ex);
        }
    }


    /**
     *  Returns a standard JMX ObjectName based on an appender name. This name
     *  follows the format used by the Log4J <code>AppenderDynamicMBean</code>
     *  so that the statistics will appear underneath the appender in the bean
     *  hierarchy.
     */
    private static ObjectName toObjectName(String appenderName)
    throws MalformedObjectNameException
    {
        return new ObjectName("log4j:appender=" + appenderName + ",statistics=statistics");
    }
}
