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

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.management.MBeanInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.LogLog;

import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.test.SelfMock;
import static net.sf.kdgcommons.test.StringAsserts.*;

import com.kdgregory.log4j.aws.StatisticsMBean;
import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchAppenderStatisticsMXBean;
import com.kdgregory.log4j.aws.internal.kinesis.KinesisAppenderStatisticsMXBean;
import com.kdgregory.log4j.aws.internal.sns.SNSAppenderStatisticsMXBean;
import com.kdgregory.log4j.testhelpers.InlineThreadFactory;
import com.kdgregory.log4j.testhelpers.aws.cloudwatch.MockCloudWatchWriterFactory;
import com.kdgregory.log4j.testhelpers.aws.cloudwatch.TestableCloudWatchAppender;
import com.kdgregory.log4j.testhelpers.aws.kinesis.MockKinesisWriterFactory;
import com.kdgregory.log4j.testhelpers.aws.kinesis.TestableKinesisAppender;
import com.kdgregory.log4j.testhelpers.aws.sns.MockSNSWriterFactory;
import com.kdgregory.log4j.testhelpers.aws.sns.TestableSNSAppender;


/**
 *  This test is used to verify the behavior of both StatisticsMBean and JMXManager.
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


    private static void loadLoggingConfig()
    {
        URL config = ClassLoader.getSystemResource("internal/TestJMXIntegration.properties");
        assertNotNull("was able to retrieve config", config);
        PropertyConfigurator.configure(config);
    }

//----------------------------------------------------------------------------
//  Setup/Teardown
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        LogManager.resetConfiguration();
        LogLog.setQuietMode(true);
        TestableJMXManager.reset();
    }


    @After
    public void tearDown()
    {
        LogLog.setQuietMode(false);
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testStatisticsMBeanRegistration() throws Exception
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

        assertSame("JMXManager associates server with bean",        server, TestableJMXManager.knownServers.get(bean));
    }


    @Test
    public void testAppenderRegistration() throws Exception
    {
        loadLoggingConfig();
        Logger logger = Logger.getLogger("allDestinations");

        TestableCloudWatchAppender cloudwatchAppender = (TestableCloudWatchAppender)logger.getAppender("cloudwatch");
        cloudwatchAppender.setThreadFactory(new InlineThreadFactory());
        cloudwatchAppender.setWriterFactory(new MockCloudWatchWriterFactory(cloudwatchAppender));

        TestableKinesisAppender kinesisAppender = (TestableKinesisAppender)logger.getAppender("kinesis");
        kinesisAppender.setThreadFactory(new InlineThreadFactory());
        kinesisAppender.setWriterFactory(new MockKinesisWriterFactory(kinesisAppender));

        TestableSNSAppender snsAppender = (TestableSNSAppender)logger.getAppender("sns");
        snsAppender.setThreadFactory(new InlineThreadFactory());
        snsAppender.setWriterFactory(new MockSNSWriterFactory());

        logger.info("test message");

        assertSame("JMXManager knows about CloudWatch stats bean",  cloudwatchAppender.getAppenderStatistics(), TestableJMXManager.knownAppenders.get("cloudwatch"));
        assertSame("JMXManager knows about CloudWatch stats class", CloudWatchAppenderStatisticsMXBean.class,   TestableJMXManager.statsBeanTypes.get("cloudwatch"));

        assertSame("JMXManager knows about Kinesis stats bean",     kinesisAppender.getAppenderStatistics(),    TestableJMXManager.knownAppenders.get("kinesis"));
        assertSame("JMXManager knows about Kinesis stats class",    KinesisAppenderStatisticsMXBean.class,      TestableJMXManager.statsBeanTypes.get("kinesis"));

        assertSame("JMXManager knows about SNS stats bean",         snsAppender.getAppenderStatistics(),        TestableJMXManager.knownAppenders.get("sns"));
        assertSame("JMXManager knows about SNS stats class",        SNSAppenderStatisticsMXBean.class,          TestableJMXManager.statsBeanTypes.get("sns"));
    }


    @Test
    public void testBeanEnabledBeforeAppender() throws Exception
    {
//        ObjectName beanName = new ObjectName("Testing:name=example");
//
//        MockMBeanServer mock = new MockMBeanServer();
//        MBeanServer server = mock.getInstance();
//
//        server.createMBean(StatisticsMBean.class.getName(), beanName);
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
