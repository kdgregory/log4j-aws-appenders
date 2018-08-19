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

import java.util.HashMap;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.StandardMBean;

import org.apache.log4j.helpers.LogLog;

import com.kdgregory.log4j.aws.StatisticsMBean;


/**
 *  This class maintains the relationships between appenders and MBeanServers.
 *  All methods/data are static, and all public methods are synchronized to
 *  avoid race conditions (since they're only called at application startup,
 *  this should not be an issue).
 */
public class JMXManager
{
    // the maps that identify who we know; all are marked protected so that
    // they can be examined during testing

    protected static Map<StatisticsMBean,MBeanServer> knownServers
        = new HashMap<StatisticsMBean,MBeanServer>();

    protected static Map<String,AbstractAppenderStatistics> knownAppenders
        = new HashMap<String,AbstractAppenderStatistics>();

    protected static Map<String,Class<?>> statsBeanTypes
        = new HashMap<String,Class<?>>();


//----------------------------------------------------------------------------
//  Methods called by StatisticsMBean
//----------------------------------------------------------------------------

    public static synchronized void registerStatisticsMBean(StatisticsMBean bean, MBeanServer server, ObjectName name)
    {
        knownServers.put(bean, server);

        for (String appenderName :  knownAppenders.keySet())
        {
            registerBean(appenderName, server);
        }
    }


    public static synchronized void deregisterStatisticsMBean(StatisticsMBean bean)
    {
        knownServers.remove(bean);
    }

//----------------------------------------------------------------------------
//  Methods called by AbstractAppender
//----------------------------------------------------------------------------

    public static synchronized void registerAppender(String appenderName, AbstractAppenderStatistics statsBean, Class<?> statsBeanClass)
    {
        // TODO - check for appender already being registered
        knownAppenders.put(appenderName, statsBean);
        statsBeanTypes.put(appenderName, statsBeanClass);

        for (MBeanServer server : knownServers.values())
        {
            registerBean(appenderName, server);
        }
    }


    public static synchronized void unregisterAppender(String appenderName)
    {
        knownAppenders.remove(appenderName);
        statsBeanTypes.remove(appenderName);

        for (MBeanServer server : knownServers.values())
        {
            unregisterBean(appenderName, server);
        }
    }


//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    /**
     *  Registers an appender's stats bean with a server.
     */
    @SuppressWarnings("rawtypes")
    private static void registerBean(String appenderName, MBeanServer mbeanServer)
    {
        // TODO - verify that we're not already registered

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

        Object statsBean = knownAppenders.get(appenderName);
        Class statsBeanClass = statsBeanTypes.get(appenderName);
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
    private static void unregisterBean(String appenderName, MBeanServer mbeanServer)
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
