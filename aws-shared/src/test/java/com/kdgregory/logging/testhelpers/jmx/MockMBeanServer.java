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

package com.kdgregory.logging.testhelpers.jmx;

import java.util.HashMap;
import java.util.Map;

import javax.management.MBeanRegistration;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import net.sf.kdgcommons.test.SelfMock;


/**
 *  A simple stand-in for a JMX server that allows bean creation and invokes
 *  the registration and deregistration life-cycles.
 */
public class MockMBeanServer
extends SelfMock<MBeanServer>
{
    private MBeanServer serverInstance = null;

    public int createMBeanInvocationCount;
    public int registerMBeanInvocationCount;
    public int unregisterMBeanInvocationCount;

    public Map<ObjectName,Object> registeredBeansByName = new HashMap<ObjectName,Object>();


    public MockMBeanServer()
    {
        super(MBeanServer.class);
    }


    @Override
    public MBeanServer getInstance()
    {
        serverInstance = super.getInstance();
        return serverInstance;
    }


    public ObjectInstance createMBean(String className, ObjectName name)
    throws ReflectionException, MBeanRegistrationException
    {
        createMBeanInvocationCount++;
        Object bean = null;
        try
        {
            Class<?> beanClass = Class.forName(className);
            bean = beanClass.newInstance();
        }
        catch (Exception ex)
        {
            throw new ReflectionException(ex);
        }

        return registerMBean(bean, name);
    }


    public ObjectInstance registerMBean(Object bean, ObjectName name)
    throws MBeanRegistrationException
    {
        registerMBeanInvocationCount++;
        try
        {
            if (bean instanceof MBeanRegistration)
                ((MBeanRegistration)bean).preRegister(serverInstance, name);

            registeredBeansByName.put(name, bean);

            if (bean instanceof MBeanRegistration)
                ((MBeanRegistration)bean).postRegister(Boolean.TRUE);
        }
        catch (Exception ex)
        {
            throw new MBeanRegistrationException(ex);
        }

        return new ObjectInstance(name, bean.getClass().getName());
    }


    public void unregisterMBean(ObjectName name)
    throws MBeanRegistrationException
    {
        unregisterMBeanInvocationCount++;
        try
        {
            Object bean = registeredBeansByName.remove(name);
            if (bean instanceof MBeanRegistration)
            {
                ((MBeanRegistration)bean).preDeregister();
                ((MBeanRegistration)bean).postDeregister();
            }
        }
        catch (Exception ex)
        {
            throw new MBeanRegistrationException(ex);
        }
    }
}