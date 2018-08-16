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

package com.kdgregory.log4j.aws;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InvalidAttributeValueException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanConstructorInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanNotificationInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import com.kdgregory.log4j.aws.internal.shared.JMXManager;

/**
 *  This class provides a bridge between an appenders and an MBeanServer. When
 *  an instance of this class has been registered, it will in turn register all
 *  appenders loaded by the same classloader hierarchy.
 *  <p>
 *  <h1>Example</strong>
 *  <pre>
 *      ManagementFactory.getPlatformMBeanServer().createMBean(
 *              HierarchyDynamicMBean.class.getName(),
 *              new ObjectName("log4j:name=Config"));
 *
 *      ManagementFactory.getPlatformMBeanServer().createMBean(
 *              StatisticsMBean.class.getName(),
 *              new ObjectName("log4j:name=StatisticsEnabled"));
 *  </pre>
 *  Note that the object names for both beans can be whatever you want. The
 *  names that I show here are consistent with the hardcoded names produced
 *  by both the Log4J appender/layout beans and the appender statistics beans
 *  from this package, so if you use them you'll see everything Log4J in one
 *  place.
 *  <p>
 *  <strong>Support for multiple Log4J hierarchies</strong>
 *  <p>
 *  For a stand-alone application, you would typically register both the Log4J
 *  <code>HierarchyDynamicMBean</code> and this bean with the platform MBean
 *  server. For a multi-container deployment such as an app-server, however,
 *  you may have a container-specific MBean server. However, there's no simple
 *  way to identify which of possibly many MBean servers may be the one we use
 *  to register, or if indeed we should register.
 *  <p>
 *  The solution I've chosen is to maintain two static collections: one holds
 *  registrations for instances of this class, the other holds references to
 *  each of the configured appenders. When one or the other of these collections
 *  changes then the appropriate JMX registrations/deregistrations are performed.
 *  <p>
 *  If you never register an instance of this class, then JMX is effectively
 *  disabled.
 */
public class StatisticsMBean
implements DynamicMBean, MBeanRegistration
{
    private MBeanServer myServer;
    private ObjectName myName;

//----------------------------------------------------------------------------
//  Supported Attributes/Operations (at this time there are none)
//----------------------------------------------------------------------------

//----------------------------------------------------------------------------
//  Implementation of DynamicMBean
//----------------------------------------------------------------------------

    @Override
    public Object getAttribute(String attribute)
    throws AttributeNotFoundException, MBeanException, ReflectionException
    {
        throw new UnsupportedOperationException("FIXME - implement");
    }

    @Override
    public void setAttribute(Attribute attribute)
    throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException,
    ReflectionException
    {
        throw new UnsupportedOperationException("FIXME - implement");
    }

    @Override
    public AttributeList getAttributes(String[] attributes)
    {
        return new AttributeList();
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes)
    {
        throw new UnsupportedOperationException("FIXME - implement");
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature)
    throws MBeanException, ReflectionException
    {
        throw new UnsupportedOperationException("FIXME - implement");
    }

    @Override
    public MBeanInfo getMBeanInfo()
    {
        return new MBeanInfo(
            getClass().getName(),
            "Enables reporting of appender statistics via JMX",
            new MBeanAttributeInfo[0],
            new MBeanConstructorInfo[0],
            new MBeanOperationInfo[0],
            new MBeanNotificationInfo[0]);
    }

//----------------------------------------------------------------------------
//  Implementation of MBeanRegistration
//----------------------------------------------------------------------------

    @Override
    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception
    {
        if (name == null)
        {
            name = new ObjectName("log4j:name=AWSAppendersStats");
        }

        myServer = server;
        myName = name;

        return myName;
    }


    @Override
    public void postRegister(Boolean registrationDone)
    {
        if (registrationDone == Boolean.TRUE)
        {
            JMXManager.registerStatisticsMBean(this, myServer, myName);
        }
    }


    @Override
    public void preDeregister() throws Exception
    {
        // we don't need to do anything
    }


    @Override
    public void postDeregister()
    {
        JMXManager.deregisterStatisticsMBean(this);
    }

}
