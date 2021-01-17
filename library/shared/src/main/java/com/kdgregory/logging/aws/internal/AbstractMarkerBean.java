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

package com.kdgregory.logging.aws.internal;

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


/**
 *  This class serves as the base "marker bean" for JMX integration: it
 *  implements the MBean interfaces, and acts as a listener for registration
 *  and deregistration notifications. Subclasses implement the behavior for
 *  these notifications, typically by registering or deregistering with a
 *  framework-specific subclass of {@link AbstractJMXManager}.
 */
public abstract class AbstractMarkerBean
implements DynamicMBean, MBeanRegistration
{
    protected MBeanServer myServer;
    protected ObjectName myName;

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
        throw new UnsupportedOperationException("this bean has no attributes");
    }

    @Override
    public void setAttribute(Attribute attribute)
    throws AttributeNotFoundException, InvalidAttributeValueException, MBeanException,
    ReflectionException
    {
        throw new UnsupportedOperationException("this bean has no attributes");
    }

    @Override
    public AttributeList getAttributes(String[] attributes)
    {
        throw new UnsupportedOperationException("this bean has no attributes");
    }

    @Override
    public AttributeList setAttributes(AttributeList attributes)
    {
        throw new UnsupportedOperationException("this bean has no attributes");
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature)
    throws MBeanException, ReflectionException
    {
        throw new UnsupportedOperationException("this bean has no operations");
    }

    @Override
    public MBeanInfo getMBeanInfo()
    {
        return new MBeanInfo(
            getClass().getName(),
            "Enables reporting of writer statistics via JMX",
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
            name = new ObjectName("com.kdgregory.logging.common.jmx:name=" + getClass().getSimpleName());
        }

        myServer = server;
        myName = name;

        return myName;
    }


    @Override
    public void postRegister(Boolean registrationDone)
    {
        if (Boolean.TRUE.equals(registrationDone))
        {
            onRegister(myServer, myName);
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
        onDeregister(myServer, myName);
    }

//----------------------------------------------------------------------------
//  Subclasses override these methods
//----------------------------------------------------------------------------

    /**
     *  Called when registration is complete, to allow the subclass to register
     *  with the JMXManager.
     *
     *  @param  server      The MBeanServer that it was registered with.
     *  @param  beanName    The name that it was registered with on that server.
     */
    protected abstract void onRegister(MBeanServer server, ObjectName beanName);


    /**
     *  Called when the marker bean has been deregistered from a server.
     *
     *  @param  server      The MBeanServer that it was registered with.
     *  @param  beanName    The name that it was registered with on that server.
     */
    protected abstract void onDeregister(MBeanServer server, ObjectName beanName);
}
