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

package com.kdgregory.logging.jmx;

import java.util.List;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatisticsMXBean;
import com.kdgregory.logging.aws.internal.AbstractWriterStatistics;
import com.kdgregory.logging.common.jmx.AbstractJMXManager;
import com.kdgregory.logging.common.jmx.AbstractMarkerBean;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;
import com.kdgregory.logging.testhelpers.jmx.MockMBeanServer;


/**
 *  Tests the interactions of AbstractJMXManager and AbstractMarkerBean.
 */
public class TestJMXManager
{
    private final static String MARKER_BEAN_NAME    = "Testing:name=example";
    private final static String MARKER_BEAN_NAME_2  = "Testing:name=example2";

//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

    private static class TestableJMXManager
    extends AbstractJMXManager
    {
        public TestableJMXManager(TestableInternalLogger logger)
        {
            super(logger);
        }

        public Map<Object,List<MBeanServer>>        getKnownServers()       { return regisrations; }
        public Map<MBeanServer,String>              getRegistrationNames()  { return registrationNames; }
        public Map<String,AbstractWriterStatistics> getStatsBeans()         { return statsBeans; }
        public Map<String,Class<?>>                 getStatsBeanTypes()     { return statsBeanTypes; }
    }


    private static class ConcreteMarkerBean
    extends AbstractMarkerBean
    {
        private TestableJMXManager jmxManager;

        public ConcreteMarkerBean(TestableJMXManager jmxManager)
        {
            this.jmxManager = jmxManager;
        }

        @Override
        protected void onRegister(MBeanServer server, ObjectName beanName)
        {
            jmxManager.addMarkerBean(this, server, beanName);
        }

        @Override
        protected void onDeregister(MBeanServer server, ObjectName beanName)
        {
            jmxManager.removeMarkerBean(this);
        }
    }


    // this is an independent rewrite of AbstractJMXManager.toObjectName()
    // changes to one or the other will cause the test to fail

    private static ObjectName objectNameForAppender(ObjectName markerName, String appenderName)
    throws MalformedObjectNameException
    {
        String baseName = markerName.getCanonicalName();
        return new ObjectName(baseName + ",appender=" + appenderName);
    }

//----------------------------------------------------------------------------
//  Setup/Teardown/state
//----------------------------------------------------------------------------

    private TestableInternalLogger internalLogger;
    private TestableJMXManager jmxManager;
    private ConcreteMarkerBean markerBean;
    private ObjectName markerBeanName;

    private MockMBeanServer mock;
    private MBeanServer server;


    @Before
    public void setUp()
    throws Exception
    {
        internalLogger = new TestableInternalLogger();
        jmxManager = new TestableJMXManager(internalLogger);

        markerBean = new ConcreteMarkerBean(jmxManager);
        markerBeanName = new ObjectName(MARKER_BEAN_NAME);

        mock = new MockMBeanServer();
        server = mock.getInstance();
    }


//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testMarkerBeanAdditionAndRemoval() throws Exception
    {
        assertEquals("test start, known marker associations",                   0,                  jmxManager.getKnownServers().size());
        assertEquals("test start, known stat beans",                            0,                  jmxManager.getStatsBeans().size());
        assertEquals("test start, known stat bean types",                       0,                  jmxManager.getStatsBeanTypes().size());

        server.registerMBean(markerBean, markerBeanName);

        assertEquals("after registering marker, known marker associations",     1,                  jmxManager.getKnownServers().size());
        assertSame("after registering marker, known marker associations",       server,             jmxManager.getKnownServers().get(markerBean).get(0));
        assertEquals("after registering marker, known stat beans",              0,                  jmxManager.getStatsBeans().size());
        assertEquals("after registering marker, known stat bean types",         0,                  jmxManager.getStatsBeanTypes().size());
        assertEquals("after adding marker, number of names known",              1,                  jmxManager.getRegistrationNames().size());
        assertEquals("after adding marker, marker name known",                  MARKER_BEAN_NAME,   jmxManager.getRegistrationNames().get(server));

        server.unregisterMBean(markerBeanName);

        assertEquals("after unregistering marker, known marker associations",   0,                  jmxManager.getKnownServers().size());
        assertEquals("after unregistering marker, known stat beans",            0,                  jmxManager.getStatsBeans().size());
        assertEquals("after unregistering marker, known stat bean types",       0,                  jmxManager.getStatsBeanTypes().size());
        assertEquals("after unregistering marker, number of names known",       0,                  jmxManager.getRegistrationNames().size());

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testStatsBeanAdditionAndRemoval() throws Exception
    {
        assertEquals("test start, known marker associations",                   0,                  jmxManager.getKnownServers().size());
        assertEquals("test start, known stat beans",                            0,                  jmxManager.getStatsBeans().size());
        assertEquals("test start, known stat bean types",                       0,                  jmxManager.getStatsBeanTypes().size());

        String appenderName = "example";
        AbstractWriterStatistics statsBean = new CloudWatchWriterStatistics();
        Class<?> statsBeanType = CloudWatchWriterStatisticsMXBean.class;

        jmxManager.addStatsBean(appenderName, statsBean, statsBeanType);

        assertEquals("after adding stats bean, known marker associations",      0,                  jmxManager.getKnownServers().size());
        assertEquals("after adding stats bean, known stat beans",               1,                  jmxManager.getStatsBeans().size());
        assertSame("after adding stats bean, known stat beans",                 statsBean,          jmxManager.getStatsBeans().get(appenderName));
        assertEquals("after adding stats bean, known stat bean types",          1,                  jmxManager.getStatsBeanTypes().size());
        assertEquals("after adding stats bean, known stat bean types",          statsBeanType,      jmxManager.getStatsBeanTypes().get(appenderName));

        jmxManager.removeStatsBean(appenderName);

        assertEquals("after removing stats bean, known marker associations",    0,                  jmxManager.getKnownServers().size());
        assertEquals("after removing stats bean, known stat beans",             0,                  jmxManager.getStatsBeans().size());
        assertEquals("after removing marker, known stat bean types",            0,                  jmxManager.getStatsBeanTypes().size());

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testMarkerBeanAddedBeforeStatsBean() throws Exception
    {
        assertEquals("test start, known marker associations",                   0,                  jmxManager.getKnownServers().size());
        assertEquals("test start, known stat beans",                            0,                  jmxManager.getStatsBeans().size());
        assertEquals("test start, known stat bean types",                       0,                  jmxManager.getStatsBeanTypes().size());

        server.registerMBean(markerBean, markerBeanName);

        assertEquals("after adding marker bean, registration count",            1,                  mock.registerMBeanInvocationCount);
        assertEquals("after adding marker bean, deregistration count",          0,                  mock.unregisterMBeanInvocationCount);
        assertEquals("after adding marker bean, number of registrations",       1,                  mock.registeredBeansByName.size());
        assertEquals("after adding marker bean, number of names known",         1,                  jmxManager.getRegistrationNames().size());
        assertEquals("after adding marker bean, marker name known",             MARKER_BEAN_NAME,   jmxManager.getRegistrationNames().get(server));

        String appenderName = "example";
        ObjectName expectedStatsBeanName = objectNameForAppender(markerBeanName, appenderName);
        AbstractWriterStatistics statsBean = new CloudWatchWriterStatistics();
        Class<?> statsBeanType = CloudWatchWriterStatisticsMXBean.class;

        jmxManager.addStatsBean(appenderName, statsBean, statsBeanType);

        assertEquals("after adding stats bean, registration count",             2,                  mock.registerMBeanInvocationCount);
        assertEquals("after adding stats bean, deregistration count",           0,                  mock.unregisterMBeanInvocationCount);
        assertNotNull("after adding stats bean, is registed under expected name",                   mock.registeredBeansByName.get(expectedStatsBeanName));

        jmxManager.removeStatsBean(appenderName);

        assertEquals("after removing stats bean, registration count",           2,                  mock.registerMBeanInvocationCount);
        assertEquals("after removing stats bean, deregistration count",         1,                  mock.unregisterMBeanInvocationCount);
        assertNull("after removing stats bean, not in active registrations",                        mock.registeredBeansByName.get(expectedStatsBeanName));

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testStatsBeanAddedBeforeMarkerBean() throws Exception
    {
        assertEquals("test start, known marker associations",                   0,                  jmxManager.getKnownServers().size());
        assertEquals("test start, known stat beans",                            0,                  jmxManager.getStatsBeans().size());
        assertEquals("test start, known stat bean types",                       0,                  jmxManager.getStatsBeanTypes().size());

        String appenderName = "example";
        ObjectName expectedStatsBeanName = objectNameForAppender(markerBeanName, appenderName);
        AbstractWriterStatistics statsBean = new CloudWatchWriterStatistics();
        Class<?> statsBeanType = CloudWatchWriterStatisticsMXBean.class;

        jmxManager.addStatsBean(appenderName, statsBean, statsBeanType);

        assertEquals("after adding stats bean, registration count",             0,                  mock.registerMBeanInvocationCount);
        assertEquals("after adding stats bean, deregistration count",           0,                  mock.unregisterMBeanInvocationCount);

        server.registerMBean(markerBean, markerBeanName);

        assertEquals("after registering marker, registration count",           2,                   mock.registerMBeanInvocationCount);
        assertEquals("after registering marker, deregistration count",         0,                   mock.unregisterMBeanInvocationCount);
        assertNotNull("after registering marker, stats bean registered",                            mock.registeredBeansByName.get(expectedStatsBeanName));

        server.unregisterMBean(markerBeanName);

        assertEquals("after unregistering marker, known marker associations",   0,                  jmxManager.getKnownServers().size());
        assertEquals("after unregistering marker, known stat beans",            1,                  jmxManager.getStatsBeans().size());
        assertEquals("after unregistering marker, known stat bean types",       1,                  jmxManager.getStatsBeanTypes().size());
        assertNull("after unregistering marker, stats bean not registered",                         mock.registeredBeansByName.get(expectedStatsBeanName));

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testMultipleServersSameMarkerName() throws Exception
    {
        assertEquals("test start, known marker associations",                   0,                  jmxManager.getKnownServers().size());
        assertEquals("test start, known stat beans",                            0,                  jmxManager.getStatsBeans().size());
        assertEquals("test start, known stat bean types",                       0,                  jmxManager.getStatsBeanTypes().size());

        MockMBeanServer mock2 = new MockMBeanServer();
        MBeanServer server2 = mock2.getInstance();

        server.registerMBean(markerBean, markerBeanName);
        server2.registerMBean(markerBean, markerBeanName);

        assertEquals("after adding marker bean, number of associations",        2,                  jmxManager.getKnownServers().get(markerBean).size());
        assertSame("after adding marker bean, server 0 association",            server,             jmxManager.getKnownServers().get(markerBean).get(0));
        assertSame("after adding marker bean, server 1 association",            server2,            jmxManager.getKnownServers().get(markerBean).get(1));
        assertEquals("after adding marker bean, server 0 registration name",    MARKER_BEAN_NAME,   jmxManager.getRegistrationNames().get(server));
        assertEquals("after adding marker bean, server 1 registration name",    MARKER_BEAN_NAME,   jmxManager.getRegistrationNames().get(server2));
        assertEquals("after adding marker bean, server 0 registration count",   1,                  mock.registerMBeanInvocationCount);
        assertEquals("after adding marker bean, server 1 registration count",   1,                  mock2.registerMBeanInvocationCount);

        String appenderName = "example";
        ObjectName expectedStatsBeanName = objectNameForAppender(markerBeanName, appenderName);
        AbstractWriterStatistics statsBean = new CloudWatchWriterStatistics();
        Class<?> statsBeanType = CloudWatchWriterStatisticsMXBean.class;

        jmxManager.addStatsBean(appenderName, statsBean, statsBeanType);

        assertEquals("after adding stats bean, server 0 registration count",    2,                  mock.registerMBeanInvocationCount);
        assertEquals("after adding stats bean, server 1 registration count",    2,                  mock2.registerMBeanInvocationCount);
        assertNotNull("after adding stats bean, it's registered with server 0",                     mock.registeredBeansByName.get(expectedStatsBeanName));
        assertNotNull("after adding stats bean, it's registered with server 1",                     mock2.registeredBeansByName.get(expectedStatsBeanName));

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testMultipleServersDifferentMarkerName() throws Exception
    {
        assertEquals("test start, known marker associations",                   0,                  jmxManager.getKnownServers().size());
        assertEquals("test start, known stat beans",                            0,                  jmxManager.getStatsBeans().size());
        assertEquals("test start, known stat bean types",                       0,                  jmxManager.getStatsBeanTypes().size());

        server.registerMBean(markerBean, markerBeanName);

        MockMBeanServer mock2 = new MockMBeanServer();
        MBeanServer server2 = mock2.getInstance();
        ObjectName markerBeanName2 = new ObjectName(MARKER_BEAN_NAME_2);
        server2.registerMBean(markerBean, markerBeanName2);

        assertEquals("after adding marker beans, number of associations",       2,                  jmxManager.getKnownServers().get(markerBean).size());
        assertSame("after adding marker beans, server 0 association",           server,             jmxManager.getKnownServers().get(markerBean).get(0));
        assertSame("after adding marker beans, server 1 association",           server2,            jmxManager.getKnownServers().get(markerBean).get(1));
        assertEquals("after adding marker beans, server 0 registration name",   MARKER_BEAN_NAME,   jmxManager.getRegistrationNames().get(server));
        assertEquals("after adding marker beans, server 1 registration name",   MARKER_BEAN_NAME_2, jmxManager.getRegistrationNames().get(server2));
        assertEquals("after adding marker beans, server 0 registration count",  1,                  mock.registerMBeanInvocationCount);
        assertEquals("after adding marker beans, server 1 registration count",  1,                  mock2.registerMBeanInvocationCount);

        String appenderName = "example";
        AbstractWriterStatistics statsBean = new CloudWatchWriterStatistics();
        Class<?> statsBeanType = CloudWatchWriterStatisticsMXBean.class;

        jmxManager.addStatsBean(appenderName, statsBean, statsBeanType);

        assertEquals("after adding stats bean, server 0 registration count",    2,              mock.registerMBeanInvocationCount);
        assertEquals("after adding stats bean, server 1 registration count",    2,              mock2.registerMBeanInvocationCount);
        assertNotNull("after adding stats bean, it's registered with server 0",                 mock.registeredBeansByName.get(objectNameForAppender(markerBeanName, appenderName)));
        assertNotNull("after adding stats bean, it's registered with server 1",                 mock2.registeredBeansByName.get(objectNameForAppender(markerBeanName2, appenderName)));

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testSingleServerSingleMarkerDifferentName() throws Exception
    {
        // this is an invalid configuration; the test verifies we can handle it

        assertEquals("test start, known marker associations",                   0,                  jmxManager.getKnownServers().size());
        assertEquals("test start, known stat beans",                            0,                  jmxManager.getStatsBeans().size());
        assertEquals("test start, known stat bean types",                       0,                  jmxManager.getStatsBeanTypes().size());

        server.registerMBean(markerBean, markerBeanName);

        ObjectName markerBeanName2 = new ObjectName(MARKER_BEAN_NAME_2);
        server.registerMBean(markerBean, markerBeanName2);

        // note: we can't prevent bean registration (although server might), but we won't maintain the association
        // therefore, the number of registrations and number of associations differ

        assertEquals("after adding marker beans, number of associations",       1,                  jmxManager.getKnownServers().get(markerBean).size());
        assertSame("after adding marker beans, server association",             server,             jmxManager.getKnownServers().get(markerBean).get(0));
        assertEquals("after adding marker beans, registration name",            MARKER_BEAN_NAME,   jmxManager.getRegistrationNames().get(server));
        assertEquals("after adding marker beans, registration count",           2,                  mock.registerMBeanInvocationCount);

        String appenderName = "example";
        AbstractWriterStatistics statsBean = new CloudWatchWriterStatistics();
        ObjectName expectedStatsBeanName = objectNameForAppender(markerBeanName, appenderName);
        Class<?> statsBeanType = CloudWatchWriterStatisticsMXBean.class;

        jmxManager.addStatsBean(appenderName, statsBean, statsBeanType);

        assertEquals("after adding stats bean, registration count",             3,                  mock.registerMBeanInvocationCount);
        assertEquals("after adding stats bean, deregistration count",           0,                  mock.unregisterMBeanInvocationCount);
        assertNotNull("after adding stats bean, is registed under expected name",                   mock.registeredBeansByName.get(expectedStatsBeanName));

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalWarningLog(".*server already registered.*" + MARKER_BEAN_NAME + ".*ignoring.*" + MARKER_BEAN_NAME_2);
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testSingleServerMultipleMarkerSameName() throws Exception
    {
        // this is an invalid configuration; the test verifies we can handle it

        assertEquals("test start, known marker associations",                   0,                  jmxManager.getKnownServers().size());
        assertEquals("test start, known stat beans",                            0,                  jmxManager.getStatsBeans().size());
        assertEquals("test start, known stat bean types",                       0,                  jmxManager.getStatsBeanTypes().size());

        server.registerMBean(markerBean, markerBeanName);

        Object markerBean2 = new ConcreteMarkerBean(jmxManager);
        ObjectName markerBeanName2 = new ObjectName(MARKER_BEAN_NAME);
        server.registerMBean(markerBean2, markerBeanName2);

        assertEquals("after adding marker beans, number of associations",       1,                  jmxManager.getKnownServers().get(markerBean).size());
        assertSame("after adding marker beans, server association",             server,             jmxManager.getKnownServers().get(markerBean).get(0));
        assertEquals("after adding marker beans, registration name",            MARKER_BEAN_NAME,   jmxManager.getRegistrationNames().get(server));
        assertEquals("after adding marker beans, registration count",           2,                  mock.registerMBeanInvocationCount);

        String appenderName = "example";
        AbstractWriterStatistics statsBean = new CloudWatchWriterStatistics();
        ObjectName expectedStatsBeanName = objectNameForAppender(markerBeanName, appenderName);
        Class<?> statsBeanType = CloudWatchWriterStatisticsMXBean.class;

        jmxManager.addStatsBean(appenderName, statsBean, statsBeanType);

        assertEquals("after adding stats bean, registration count",             3,                  mock.registerMBeanInvocationCount);
        assertEquals("after adding stats bean, deregistration count",           0,                  mock.unregisterMBeanInvocationCount);
        assertNotNull("after adding stats bean, is registed under expected name",                   mock.registeredBeansByName.get(expectedStatsBeanName));

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalWarningLog(".*server already registered.*" + MARKER_BEAN_NAME + ".*ignoring.*" + MARKER_BEAN_NAME);
        internalLogger.assertInternalErrorLog();
    }
}
