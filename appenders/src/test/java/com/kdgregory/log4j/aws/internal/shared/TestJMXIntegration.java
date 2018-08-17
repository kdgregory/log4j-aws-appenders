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

import javax.management.MBeanInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.collections.CollectionUtil;
import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.test.SelfMock;
import static net.sf.kdgcommons.test.StringAsserts.*;

import com.kdgregory.log4j.aws.StatisticsMBean;


/**
 *  This test is used to verify the behavior of both StatisticsMBean and
 *  JMXManager.
 */
public class TestJMXIntegration
{
//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

    private static class TestableJMXManager
    extends JMXManager
    {
        public static void reset()
        {
            knownServers.clear();
            knownAppenders.clear();
            statsBeanTypes.clear();
        }
    }

    
    private static class MockMBeanServer
    extends SelfMock<MBeanServer>
    {
        private MBeanServer serverInstance = null;

        public Map<ObjectName,Object> registeredBeans = new HashMap<ObjectName,Object>();

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

        @SuppressWarnings("unused")
        public ObjectInstance createMBean(String className, ObjectName name)
        throws ReflectionException, MBeanRegistrationException
        {
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

            try
            {
                ((MBeanRegistration)bean).preRegister(serverInstance, name);
                registeredBeans.put(name, bean);
                ((MBeanRegistration)bean).postRegister(Boolean.TRUE);
            }
            catch (Exception ex)
            {
                throw new MBeanRegistrationException(ex);
            }

            return new ObjectInstance(name, className);
        }
    }


//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        TestableJMXManager.reset();
    }


    @Test
    public void testStatisticsMBeanInterface() throws Exception
    {
        ObjectName beanName = new ObjectName("Testing:name=example");

        MockMBeanServer mock = new MockMBeanServer();
        MBeanServer server = mock.getInstance();

        server.createMBean(StatisticsMBean.class.getName(), beanName);

        StatisticsMBean bean = (StatisticsMBean)mock.registeredBeans.get(beanName);

        assertNotNull("bean exists",         bean);
        assertSame("bean retained server",   server,    ClassUtil.getFieldValue(bean, "myServer", MBeanServer.class));
        assertEquals("bean retained name",   beanName,  ClassUtil.getFieldValue(bean, "myName", ObjectName.class));

        MBeanInfo beanInfo = bean.getMBeanInfo();

        assertEquals("bean info provides classname",                StatisticsMBean.class.getName(), beanInfo.getClassName());
        assertNotEmpty("bean info provides description",            beanInfo.getDescription());
        assertEquals("bean info does not indicate attributes",      0,  beanInfo.getAttributes().length);
        assertEquals("bean info does not indicate operations",      0,  beanInfo.getOperations().length);
        assertEquals("bean info does not indicate notifications",   0,  beanInfo.getNotifications().length);
        assertEquals("bean info does not indicate constructors",    0,  beanInfo.getConstructors().length);
    }


    @Test
    public void testBeanEnabledBeforeAppender() throws Exception
    {        
        ObjectName beanName = new ObjectName("Testing:name=example");

        MockMBeanServer mock = new MockMBeanServer();
        MBeanServer server = mock.getInstance();

        server.createMBean(StatisticsMBean.class.getName(), beanName);
        
        StatisticsMBean bean = (StatisticsMBean)mock.registeredBeans.get(beanName);
        
        assertEquals("JMX manager knows of bean", CollectionUtil.asMap(bean, server), TestableJMXManager.knownServers);
    }


    @Test
    public void testBeanEnabledAfterAppender() throws Exception
    {
    }


    @Test
    public void testMultipleServers() throws Exception
    {
    }

}
