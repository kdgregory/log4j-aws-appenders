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
import javax.management.StandardMBean;

import org.junit.After;
import org.junit.Before;
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
 *  This set of tests uses real appenders and a semi-functional mock MBean
 *  Server to exercise the full behavior of JMX registration.
 */
public class TestJMXIntegration
{

//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

    private static class TestableJMXManager
    extends JMXManager
    {
        // nothing here; just used to expose protected fields
    }


    private static class MockMBeanServer
    extends SelfMock<MBeanServer>
    {
        private MBeanServer serverInstance = null;

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

            return registerMBean(bean, name);
        }

        public ObjectInstance registerMBean(Object bean, ObjectName name)
        throws MBeanRegistrationException
        {
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

        @SuppressWarnings("unused")
        public void unregisterMBean(ObjectName name)
        throws MBeanRegistrationException
        {
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


    private static void loadLoggingConfig()
    {
        URL config = ClassLoader.getSystemResource("internal/TestJMXIntegration.properties");
        assertNotNull("was able to retrieve config", config);
        PropertyConfigurator.configure(config);
    }

//----------------------------------------------------------------------------
//  Setup/Teardown/state
//----------------------------------------------------------------------------

    private TestableJMXManager jmxManager;

    @Before
    public void setUp()
    {
        LogManager.resetConfiguration();
        jmxManager = new TestableJMXManager();
        TestableJMXManager.reset(jmxManager);
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

        StatisticsMBean bean = (StatisticsMBean)mock.registeredBeansByName.get(beanName);

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

        assertSame("JMXManager associates server with bean",        server, jmxManager.knownServers.get(bean));

        server.unregisterMBean(beanName);

        assertTrue("unregistering bean removes it from JMXManager", jmxManager.knownServers.isEmpty());
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

        assertNull("before first message, JMXManager doesn't know about CloudWatch stats bean",  jmxManager.knownAppenders.get("cloudwatch"));
        assertNull("before first message, JMXManager doesn't know about CloudWatch stats class", jmxManager.statsBeanTypes.get("cloudwatch"));
        assertNull("before first message, JMXManager doesn't know about Kinesis stats bean",     jmxManager.knownAppenders.get("kinesis"));
        assertNull("before first message, JMXManager doesn't know about Kinesis stats class",    jmxManager.statsBeanTypes.get("kinesis"));
        assertNull("before first message, JMXManager doesn't know about SNS stats bean",         jmxManager.knownAppenders.get("sns"));
        assertNull("before first message, JMXManager doesn't know about stats class",            jmxManager.statsBeanTypes.get("sns"));

        logger.info("test message");

        assertSame("after message, JMXManager knows about CloudWatch stats bean",   cloudwatchAppender.getAppenderStatistics(), jmxManager.knownAppenders.get("cloudwatch"));
        assertSame("after message, JMXManager knows about CloudWatch stats class",  CloudWatchAppenderStatisticsMXBean.class,   jmxManager.statsBeanTypes.get("cloudwatch"));
        assertSame("after message, JMXManager knows about Kinesis stats bean",      kinesisAppender.getAppenderStatistics(),    jmxManager.knownAppenders.get("kinesis"));
        assertSame("after message, JMXManager knows about Kinesis stats class",     KinesisAppenderStatisticsMXBean.class,      jmxManager.statsBeanTypes.get("kinesis"));
        assertSame("after message, JMXManager knows about SNS stats bean",          snsAppender.getAppenderStatistics(),        jmxManager.knownAppenders.get("sns"));
        assertSame("after message, JMXManager knows about SNS stats class",         SNSAppenderStatisticsMXBean.class,          jmxManager.statsBeanTypes.get("sns"));

        snsAppender.close();

        assertNull("closing appender removes bean from JMXManager",                 jmxManager.knownAppenders.get("sns"));
        assertNull("closing appender removes stats class from JMXManager",          jmxManager.statsBeanTypes.get("sns"));
    }


    @Test
    public void testBeanEnabledBeforeAppender() throws Exception
    {
        MockMBeanServer mock = new MockMBeanServer();
        MBeanServer server = mock.getInstance();

        ObjectName statisticsMBeanName = new ObjectName("Testing:name=example");
        server.createMBean(StatisticsMBean.class.getName(), statisticsMBeanName);

        assertNotNull("StatisticsMBean registered with server", mock.registeredBeansByName.get(statisticsMBeanName));

        loadLoggingConfig();
        Logger logger = Logger.getLogger("cloudwatchOnly");
        ObjectName appenderMBeanName = new ObjectName("log4j:appender=cloudwatch,statistics=statistics");

        TestableCloudWatchAppender appender = (TestableCloudWatchAppender)logger.getAppender("cloudwatch");
        appender.setThreadFactory(new InlineThreadFactory());
        appender.setWriterFactory(new MockCloudWatchWriterFactory(appender));

        assertEquals("before first message, number of registered beans", 1, mock.registeredBeansByName.size());

        logger.info("some message");

        assertEquals("after first message, number of registered beans",  2, mock.registeredBeansByName.size());

        StandardMBean registeredAppenderBean = (StandardMBean)mock.registeredBeansByName.get(appenderMBeanName);
        assertSame("appender bean registered with server", appender.getAppenderStatistics(), registeredAppenderBean.getImplementation());
    }


    @Test
    public void testBeanEnabledAfterAppender() throws Exception
    {
        MockMBeanServer mock = new MockMBeanServer();
        MBeanServer server = mock.getInstance();

        loadLoggingConfig();
        Logger logger = Logger.getLogger("cloudwatchOnly");
        ObjectName appenderMBeanName = new ObjectName("log4j:appender=cloudwatch,statistics=statistics");

        TestableCloudWatchAppender appender = (TestableCloudWatchAppender)logger.getAppender("cloudwatch");
        appender.setThreadFactory(new InlineThreadFactory());
        appender.setWriterFactory(new MockCloudWatchWriterFactory(appender));

        assertEquals("before first message, number of registered beans", 0, mock.registeredBeansByName.size());

        logger.info("some message");

        assertEquals("after first message, number of registered beans",  0, mock.registeredBeansByName.size());

        ObjectName statisticsMBeanName = new ObjectName("Testing:name=example");
        server.createMBean(StatisticsMBean.class.getName(), statisticsMBeanName);

        assertNotNull("StatisticsMBean registered with server", mock.registeredBeansByName.get(statisticsMBeanName));
        assertEquals("after registering StatisticsMBean, number of registered beans",  2, mock.registeredBeansByName.size());

        StandardMBean registeredAppenderBean = (StandardMBean)mock.registeredBeansByName.get(appenderMBeanName);
        assertSame("appender bean registered with server", appender.getAppenderStatistics(), registeredAppenderBean.getImplementation());
    }


    @Test
    public void testMultipleServers() throws Exception
    {
        MockMBeanServer mock1 = new MockMBeanServer();
        MBeanServer server1 = mock1.getInstance();

        MockMBeanServer mock2 = new MockMBeanServer();
        MBeanServer server2 = mock2.getInstance();

        ObjectName statisticsMBeanName = new ObjectName("Testing:name=example");

        server1.createMBean(StatisticsMBean.class.getName(), statisticsMBeanName);
        assertNotNull("StatisticsMBean registered with server 1", mock1.registeredBeansByName.get(statisticsMBeanName));

        server2.createMBean(StatisticsMBean.class.getName(), statisticsMBeanName);
        assertNotNull("StatisticsMBean registered with server 2", mock2.registeredBeansByName.get(statisticsMBeanName));

        loadLoggingConfig();
        Logger logger = Logger.getLogger("cloudwatchOnly");
        ObjectName appenderMBeanName = new ObjectName("log4j:appender=cloudwatch,statistics=statistics");

        TestableCloudWatchAppender appender = (TestableCloudWatchAppender)logger.getAppender("cloudwatch");
        appender.setThreadFactory(new InlineThreadFactory());
        appender.setWriterFactory(new MockCloudWatchWriterFactory(appender));

        assertEquals("before first message, number of registered beans in server 1", 1, mock1.registeredBeansByName.size());
        assertEquals("before first message, number of registered beans in server 2", 1, mock2.registeredBeansByName.size());

        logger.info("some message");

        assertEquals("after first message, number of registered beans in server 1", 2, mock1.registeredBeansByName.size());
        assertEquals("after first message, number of registered beans in server 2", 2, mock2.registeredBeansByName.size());

        StandardMBean registeredAppenderBean1 = (StandardMBean)mock1.registeredBeansByName.get(appenderMBeanName);
        assertSame("appender bean registered with server 1", appender.getAppenderStatistics(), registeredAppenderBean1.getImplementation());

        StandardMBean registeredAppenderBean2 = (StandardMBean)mock2.registeredBeansByName.get(appenderMBeanName);
        assertSame("appender bean registered with server 2", appender.getAppenderStatistics(), registeredAppenderBean2.getImplementation());
    }

}
