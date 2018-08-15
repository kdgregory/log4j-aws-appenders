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

import java.lang.management.ManagementFactory;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.StandardMBean;

import org.apache.log4j.helpers.LogLog;

/**
 *  This class maintains the relationships between appenders and MBeanServers.
 *  All methods/data are static
 */
public class JMXManager
{



//----------------------------------------------------------------------------
//  Methods called by StatisticsMBean
//----------------------------------------------------------------------------

//----------------------------------------------------------------------------
//  Methods called by AbstractAppender
//----------------------------------------------------------------------------

    public static <BeanType,BeanMXType> void registerAppender(String appenderName, BeanType statsBean, Class<BeanMXType> statsBeanClass)
    {
        try
        {
            MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            StandardMBean mbean = new StandardMBean(statsBeanClass.cast(statsBean), statsBeanClass, false);
            mbeanServer.registerMBean(mbean, toObjectName(appenderName));
        }
        catch (Exception ex)
        {
            LogLog.warn("failed to register appender statistics with JMX", ex);
        }
    }


    public static void unregisterAppender(String appenderName)
    {
        try
        {
            MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            mbeanServer.unregisterMBean(toObjectName(appenderName));
        }
        catch (Exception ex)
        {
            LogLog.warn("failed to unregister appender statistics with JMX", ex);
        }
    }


//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

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
