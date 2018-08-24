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
import java.util.List;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.test.SelfMock;

import com.kdgregory.log4j.aws.StatisticsMBean;
import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchAppenderStatistics;
import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchAppenderStatisticsMXBean;


/**
 *  This class tests the table management code in JMXManager. See {@link
 *  TestJMXIntegration} for application-level behavior.
 */
public class TestJMXManager
{

//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

    // represents a single bean registration, with type
    private static class BeanRegistration
    {
        public String appenderName;
        public MBeanServer server;

        public BeanRegistration(String appenderName, MBeanServer server)
        {
            this.appenderName = appenderName;
            this.server = server;
        }
    }

    // exposes protected static methods to the test class, and also records
    // but doesn't execute actual bean registration
    private static class TestableJMXManager
    extends JMXManager
    {
        public List<BeanRegistration> registrations = new ArrayList<TestJMXManager.BeanRegistration>();
        public List<BeanRegistration> unregistrations = new ArrayList<TestJMXManager.BeanRegistration>();

        @Override
        protected void registerAppenderBean(String appenderName, MBeanServer mbeanServer)
        {
            registrations.add(new BeanRegistration(appenderName, mbeanServer));
        }

        @Override
        protected void unregisterAppenderBean(String appenderName, MBeanServer mbeanServer)
        {
            unregistrations.add(new BeanRegistration(appenderName, mbeanServer));
        }
    }

    // this will throw if any methods are invoked
    private static class MockMBeanServer
    extends SelfMock<MBeanServer>
    {
        public MockMBeanServer()
        {
            super(MBeanServer.class);
        }
    }

//----------------------------------------------------------------------------
//  Setup/Teardown/state
//----------------------------------------------------------------------------

    private TestableJMXManager jmxManager;
    private MockMBeanServer mock;
    private MBeanServer server;

    @Before
    public void setUp()
    {
        jmxManager = new TestableJMXManager();
        TestableJMXManager.reset(jmxManager);

        mock = new MockMBeanServer();
        server = mock.getInstance();
    }


//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testStatisticsMBeanAdditionAndRemoval() throws Exception
    {
        assertTrue("test start, JMXManager knows no beans",             jmxManager.knownServers.isEmpty());
        assertTrue("test start, JMXManager knows no appenders",         jmxManager.appenderStatsBeans.isEmpty());
        assertTrue("test start, JMXManager knows no stat bean types",   jmxManager.appenderStatsBeanTypes.isEmpty());

        StatisticsMBean statisticsMBean = new StatisticsMBean();
        ObjectName statisticsMBeanName = new ObjectName("Testing:name=example");

        JMXManager.getInstance().addStatisticsMBean(statisticsMBean, server, statisticsMBeanName);

        assertSame("added bean/server relationship",        server, jmxManager.knownServers.get(statisticsMBean).get(0));
        assertTrue("JMXManager knows no appender beans",    jmxManager.appenderStatsBeans.isEmpty());
        assertTrue("JMXManager knows no stat bean types",   jmxManager.appenderStatsBeanTypes.isEmpty());
        assertTrue("JMXManager did not register appender",  jmxManager.registrations.isEmpty());

        JMXManager.getInstance().removeStatisticsMBean(statisticsMBean);

        assertNull("removed bean/server registration",      jmxManager.knownServers.get(statisticsMBean));
    }


    @Test
    public void testAppenderAdditionAndRemoval() throws Exception
    {
        assertTrue("test start, JMXManager knows no beans",             jmxManager.knownServers.isEmpty());
        assertTrue("test start, JMXManager knows no appenders",         jmxManager.appenderStatsBeans.isEmpty());
        assertTrue("test start, JMXManager knows no stat bean types",   jmxManager.appenderStatsBeanTypes.isEmpty());

        String appenderName = "example";
        AbstractAppenderStatistics appenderStats = new CloudWatchAppenderStatistics();
        Class<?> appenderStatsType = CloudWatchAppenderStatisticsMXBean.class;

        JMXManager.getInstance().addAppender(appenderName, appenderStats, appenderStatsType);

        assertSame("added appender/bean relationship",      appenderStats,     jmxManager.appenderStatsBeans.get(appenderName));
        assertSame("added appender/type relationship",      appenderStatsType, jmxManager.appenderStatsBeanTypes.get(appenderName));
        assertTrue("JMXManager knows no servers",           jmxManager.knownServers.isEmpty());
        assertTrue("JMXManager did not register appender",  jmxManager.registrations.isEmpty());

        JMXManager.getInstance().removeAppender(appenderName);

        assertNull("removed stats bean",                    jmxManager.appenderStatsBeans.get(appenderName));
        assertNull("removed stats bean type",               jmxManager.appenderStatsBeanTypes.get(appenderName));
    }


    @Test
    public void testStatisticsMBeanAddedBeforeAppender() throws Exception
    {
        assertTrue("test start, JMXManager knows no beans",             jmxManager.knownServers.isEmpty());
        assertTrue("test start, JMXManager knows no appenders",         jmxManager.appenderStatsBeans.isEmpty());
        assertTrue("test start, JMXManager knows no stat bean types",   jmxManager.appenderStatsBeanTypes.isEmpty());

        StatisticsMBean statisticsMBean = new StatisticsMBean();
        ObjectName statisticsMBeanName = new ObjectName("Testing:name=example");

        JMXManager.getInstance().addStatisticsMBean(statisticsMBean, server, statisticsMBeanName);

        assertTrue("before adding appender, JMXManager did not register anything", jmxManager.registrations.isEmpty());

        String appenderName = "example";
        AbstractAppenderStatistics appenderStats = new CloudWatchAppenderStatistics();
        Class<?> appenderStatsType = CloudWatchAppenderStatisticsMXBean.class;

        JMXManager.getInstance().addAppender(appenderName, appenderStats, appenderStatsType);

        assertEquals("after adding appender, number of registrations",  1,              jmxManager.registrations.size());
        assertEquals("registered appender name",                        appenderName,   jmxManager.registrations.get(0).appenderName);
        assertSame("registered server",                                 server,         jmxManager.registrations.get(0).server);

        JMXManager.getInstance().removeStatisticsMBean(statisticsMBean);

        assertEquals("after removing StatisticsMBean, number of unregistrations",   1,              jmxManager.unregistrations.size());
        assertEquals("unregistered appender name",                                  appenderName,   jmxManager.registrations.get(0).appenderName);
        assertSame("unregistered server",                                           server,         jmxManager.registrations.get(0).server);
    }


    @Test
    public void testStatisticsMBeanAddedAfterAppender() throws Exception
    {
        assertTrue("test start, JMXManager knows no beans",             jmxManager.knownServers.isEmpty());
        assertTrue("test start, JMXManager knows no appenders",         jmxManager.appenderStatsBeans.isEmpty());
        assertTrue("test start, JMXManager knows no stat bean types",   jmxManager.appenderStatsBeanTypes.isEmpty());

        String appenderName = "example";
        AbstractAppenderStatistics appenderStats = new CloudWatchAppenderStatistics();
        Class<?> appenderStatsType = CloudWatchAppenderStatisticsMXBean.class;

        JMXManager.getInstance().addAppender(appenderName, appenderStats, appenderStatsType);

        assertTrue("before adding statistics MBean, JMXManager did not register anything", jmxManager.registrations.isEmpty());

        StatisticsMBean statisticsMBean = new StatisticsMBean();
        ObjectName statisticsMBeanName = new ObjectName("Testing:name=example");

        JMXManager.getInstance().addStatisticsMBean(statisticsMBean, server, statisticsMBeanName);

        assertEquals("after adding statistics MBean, number of registrations",  1,                  jmxManager.registrations.size());
        assertEquals("registered appender name",                                appenderName,       jmxManager.registrations.get(0).appenderName);
        assertSame("registered server",                                         server,             jmxManager.registrations.get(0).server);

        JMXManager.getInstance().removeAppender(appenderName);

        assertEquals("after removing appender, number of deregistrations",      1,                  jmxManager.unregistrations.size());
        assertSame("after removing appender, stats bean remains",               statisticsMBean,    jmxManager.knownServers.keySet().iterator().next());
    }


    @Test
    public void testStatisticsMBeanAddedToMultipleServers() throws Exception
    {
        assertTrue("test start, JMXManager knows no beans",             jmxManager.knownServers.isEmpty());
        assertTrue("test start, JMXManager knows no appenders",         jmxManager.appenderStatsBeans.isEmpty());
        assertTrue("test start, JMXManager knows no stat bean types",   jmxManager.appenderStatsBeanTypes.isEmpty());

        MBeanServer server2 = new MockMBeanServer().getInstance();

        StatisticsMBean statisticsMBean = new StatisticsMBean();
        ObjectName statisticsMBeanName = new ObjectName("Testing:name=example");

        JMXManager.getInstance().addStatisticsMBean(statisticsMBean, server, statisticsMBeanName);
        JMXManager.getInstance().addStatisticsMBean(statisticsMBean, server2, statisticsMBeanName);

        assertEquals("JMXManager knows both servers (checking size)",   2,      jmxManager.knownServers.get(statisticsMBean).size());
        assertSame("JMXManager knows both servers (checking server1)",  server, jmxManager.knownServers.get(statisticsMBean).get(0));
        assertSame("JMXManager knows both servers (checking server2)",  server2, jmxManager.knownServers.get(statisticsMBean).get(1));

        String appenderName = "example";
        AbstractAppenderStatistics appenderStats = new CloudWatchAppenderStatistics();
        Class<?> appenderStatsType = CloudWatchAppenderStatisticsMXBean.class;

        assertEquals("before adding appender, number of registrations", 0, jmxManager.registrations.size());

        JMXManager.getInstance().addAppender(appenderName, appenderStats, appenderStatsType);

        assertEquals("after adding appender, number of registrations",  2, jmxManager.registrations.size());

    }
}
