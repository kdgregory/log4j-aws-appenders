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

package com.kdgregory.logback.aws;

import java.net.URL;

import junit.framework.AssertionFailedError;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;

import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import net.sf.kdgcommons.lang.ClassUtil;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.logs.AWSLogsClientBuilder;

import com.kdgregory.logback.aws.testhelpers.MessageWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.test.AbstractCloudWatchAppenderIntegrationTest;
import com.kdgregory.logging.testhelpers.CloudWatchTestHelper;
import com.kdgregory.logging.testhelpers.RoleTestHelper;


public class CloudWatchAppenderIntegrationTest
extends AbstractCloudWatchAppenderIntegrationTest
{

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Holds a logger instance and related objects, to support assertions.
     */
    public static class LoggerInfo
    implements LoggerAccessor
    {
        public ch.qos.logback.classic.Logger logger;
        public CloudWatchAppender<ILoggingEvent> appender;
        public CloudWatchWriterStatistics stats;

        public LoggerInfo(String loggerName, String appenderName)
        {
            logger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(loggerName);
            appender = (CloudWatchAppender<ILoggingEvent>)logger.getAppender(appenderName);
            stats = appender.getAppenderStatistics();
        }

        @Override
        public MessageWriter newMessageWriter(int numMessages)
        {
            return new MessageWriter(logger, numMessages);
        }

        @Override
        public CloudWatchLogWriter getWriter()
        throws Exception
        {
            return ClassUtil.getFieldValue(appender, "writer", CloudWatchLogWriter.class);
        }

        @Override
        public CloudWatchWriterStatistics getStats()
        {
            return stats;
        }

        @Override
        public boolean supportsConfigurationChanges()
        {
            return true;
        }

        @Override
        public void setBatchDelay(long value)
        {
            appender.setBatchDelay(value);
        }
    }


    /**
     *  Loads the test-specific logging configuration and resets the environment.
     */
    public void init(String testName, boolean preCreateLogGroup, String... preCreateLogStreams) throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        testHelper = new CloudWatchTestHelper(helperClient, BASE_LOGGROUP_NAME, testName);

        // clean up after any previous failure
        testHelper.deleteLogGroupIfExists();

        if (preCreateLogGroup)
        {
            // Insights won't read messages with timestamps before log group creation,
            // so we pre-create the group unless we want to explicitly test auto-create
            localLogger.debug("pre-creating log group");
            testHelper.createLogGroup();
        }
        
        if (preCreateLogStreams.length > 0)
        {
            // this is an attempt to avoid the CloudWatch Logs bug described in #184
            localLogger.debug("pre-creating log streams");
            for (String logStreamName : preCreateLogStreams)
            {
                testHelper.createLogStream(logStreamName);
            }
            Thread.sleep(100);
        }

        String propertiesName = "CloudWatchAppenderIntegrationTest/" + testName + ".xml";
        URL config = ClassLoader.getSystemResource(propertiesName);
        assertNotNull("missing configuration: " + propertiesName, config);

        LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
        context.reset();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(config);

        localLogger = LoggerFactory.getLogger(getClass());
    }

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    throws Exception
    {
        AbstractCloudWatchAppenderIntegrationTest.beforeClass();
    }


    @After
    @Override
    public void tearDown()
    throws Exception
    {
        super.tearDown();
        MDC.clear();
    }


    @AfterClass
    public static void afterClass()
    throws Exception
    {
        AbstractCloudWatchAppenderIntegrationTest.afterClass();
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void smoketest() throws Exception
    {
        init("smoketest", false);
        super.smoketest(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testMultipleThreadsSingleAppender() throws Exception
    {
        init("testMultipleThreadsSingleAppender", true);
        super.testMultipleThreadsSingleAppender(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testMultipleThreadsMultipleAppendersDifferentDestinations() throws Exception
    {
        init("testMultipleThreadsMultipleAppendersDifferentDestinations", true);
        super.testMultipleThreadsMultipleAppendersDifferentDestinations(
            new LoggerInfo("TestLogger1", "test1"),
            new LoggerInfo("TestLogger2", "test2"),
            new LoggerInfo("TestLogger3", "test3"));
    }


    @Test
    public void testMultipleThreadsMultipleAppendersSameDestination() throws Exception
    {
        init("testMultipleThreadsMultipleAppendersSameDestination", true, "AppenderTest");
        super.testMultipleThreadsMultipleAppendersSameDestination(
            new LoggerInfo("TestLogger1", "test1"),
            new LoggerInfo("TestLogger2", "test2"),
            new LoggerInfo("TestLogger3", "test3"),
            new LoggerInfo("TestLogger4", "test4"),
            new LoggerInfo("TestLogger5", "test5"));
    }


    @Test
    public void testLogstreamDeletionAndRecreation() throws Exception
    {
        init("testLogstreamDeletionAndRecreation", true);
        super.testLogstreamDeletionAndRecreation(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testFactoryMethod() throws Exception
    {
        init("testFactoryMethod", false);
        super.testFactoryMethod(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testAlternateRegion() throws Exception
    {
        // BEWARE: my default region is us-east-1, so I use us-east-2 as the alternate
        //         if that is your default, then the test will fail
        altClient = AWSLogsClientBuilder.standard().withRegion(Regions.US_EAST_2).build();
        CloudWatchTestHelper altTestHelper = new CloudWatchTestHelper(altClient, BASE_LOGGROUP_NAME, "testAlternateRegion");

        // must delete existing group before logger initialization to avoid race condition
        altTestHelper.deleteLogGroupIfExists();

        init("testAlternateRegion", false);
        super.testAlternateRegion(new LoggerInfo("TestLogger", "test"), altTestHelper);
    }


    @Test
    public void testAlternateEndpoint() throws Exception
    {
        // BEWARE: my default region is us-east-1, so I use us-east-2 as the alternate
        //         if that is your default, then the test will fail
        altClient = AWSLogsClientBuilder.standard().withRegion(Regions.US_EAST_2).build();
        CloudWatchTestHelper altTestHelper = new CloudWatchTestHelper(altClient, BASE_LOGGROUP_NAME, "testAlternateEndpoint");

        // must delete existing group before logger initialization to avoid race condition
        altTestHelper.deleteLogGroupIfExists();

        init("testAlternateEndpoint", false);
        super.testAlternateRegion(new LoggerInfo("TestLogger", "test"), altTestHelper);
    }


    @Test
    public void testAssumedRole() throws Exception
    {
        // we can't change the config, so will have to pass a role name that's unlikely
        // to be used by the user; also have to create it before loading config
        final String roleName = "CloudWatchAppenderIntegrationTest-Logback";

        RoleTestHelper roleHelper = new RoleTestHelper();
        try
        {
            Role role = roleHelper.createRole(roleName, "arn:aws:iam::aws:policy/CloudWatchLogsFullAccess");
            roleHelper.waitUntilRoleAssumable(role.getArn(), 60);
            init("testAssumedRole", false);
            super.testAssumedRole(new LoggerInfo("TestLogger", "test"));
        }
        catch (AssertionFailedError ex)
        {
            // the next catch clause will swallow the assertion if we didn't do this
            throw ex;
        }
        catch (Exception ex)
        {
            localLogger.warn("unexpected exception", ex);
            fail("unexpected exception: " + ex.getMessage());
        }
        finally
        {
            roleHelper.deleteRole(roleName);
            roleHelper.shutdown();
        }
    }

//----------------------------------------------------------------------------
//  Tests for synchronous operation -- this is handled in AbstractAppender,
//  so only needs to be tested for one appender type
//----------------------------------------------------------------------------

    @Test
    public void testSynchronousModeSingleThread() throws Exception
    {
        init("testSynchronousModeSingleThread", false);
        super.testSynchronousModeSingleThread(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testSynchronousModeMultiThread() throws Exception
    {
        init("testSynchronousModeMultiThread", false);
        super.testSynchronousModeMultiThread(new LoggerInfo("TestLogger", "test"));
    }
}
