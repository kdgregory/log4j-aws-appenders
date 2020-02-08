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

package com.kdgregory.log4j2.aws;

import java.net.URI;

import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import net.sf.kdgcommons.lang.ClassUtil;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.test.AbstractCloudWatchAppenderIntegrationTest;
import com.kdgregory.logging.testhelpers.CloudWatchTestHelper;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;

import com.kdgregory.log4j2.aws.testhelpers.MessageWriter;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;


public class CloudWatchAppenderIntegrationTest
extends AbstractCloudWatchAppenderIntegrationTest
{
    private final static String BASE_LOGGROUP_NAME = "AppenderIntegrationTest";

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Holds a logger instance and related objects, to support assertions.
     */
    public static class LoggerInfo
    implements LoggerAccessor
    {
        public Logger logger;
        public CloudWatchAppender appender;
        public CloudWatchWriterStatistics stats;

        public LoggerInfo(String loggerName, String appenderName)
        {
            LoggerContext context = LoggerContext.getContext();
            logger = context.getLogger(loggerName);
            appender = (CloudWatchAppender)logger.getAppenders().get(appenderName);
            stats = appender.getAppenderStatistics();
        }

        @Override
        public MessageWriter createMessageWriter(int numMessages)
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
            return false;
        }

        @Override
        public void setBatchDelay(long value)
        {
            throw new IllegalStateException("can't reconfigure logger after initialization");
        }
    }

    /**
     *  Loads the test-specific Logback configuration and resets the environment.
     */
    public void init(String testName) throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        testHelper = new CloudWatchTestHelper(helperClient, BASE_LOGGROUP_NAME, testName);

        // this has to happen before the logger is initialized or we have a race condition
        testHelper.deleteLogGroupIfExists();

        String propsName = "CloudWatchAppenderIntegrationTest/" + testName + ".xml";
        URI config = ClassLoader.getSystemResource(propsName).toURI();
        assertNotNull("was able to retrieve config", config);

        LoggerContext context = LoggerContext.getContext();
        context.setConfigLocation(config);

        // must reload after configuration
        localLogger = LoggerFactory.getLogger(getClass());
    }

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    {
        AbstractCloudWatchAppenderIntegrationTest.beforeClass();
    }


    @After
    @Override
    public void tearDown()
    {
        super.tearDown();
        MDC.clear();
    }

//----------------------------------------------------------------------------
//  Test Cases
//----------------------------------------------------------------------------

    @Test
    public void smoketest() throws Exception
    {
        init("smoketest");
        super.smoketest(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testMultipleThreadsSingleAppender() throws Exception
    {
        init("testMultipleThreadsSingleAppender");
        super.testMultipleThreadsSingleAppender(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testMultipleThreadsMultipleAppendersDifferentDestinations() throws Exception
    {
        init("testMultipleThreadsMultipleAppendersDifferentDestinations");
        super.testMultipleThreadsMultipleAppendersDifferentDestinations(
            new LoggerInfo("TestLogger1", "test1"),
            new LoggerInfo("TestLogger2", "test2"),
            new LoggerInfo("TestLogger3", "test3"));
    }


    @Test
    public void testMultipleThreadsMultipleAppendersSameDestination() throws Exception
    {
        init("testMultipleThreadsMultipleAppendersSameDestination");
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
        init("testLogstreamDeletionAndRecreation");
        super.testLogstreamDeletionAndRecreation(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testFactoryMethod() throws Exception
    {
        init("testFactoryMethod");
        super.testFactoryMethod(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testAlternateRegion() throws Exception
    {
        // BEWARE: my default region is us-east-1, so I use us-east-2 as the alternate
        //         if that is your default, then the test will fail
        AWSLogs altClient = AWSLogsClientBuilder.standard().withRegion("us-east-2").build();
        CloudWatchTestHelper altTestHelper = new CloudWatchTestHelper(altClient, BASE_LOGGROUP_NAME, "testAlternateRegion");

        // must delete existing group before logger initialization to avoid race condition
        altTestHelper.deleteLogGroupIfExists();

        init("testAlternateRegion");
        super.testAlternateRegion(new LoggerInfo("TestLogger", "test"), altTestHelper);
    }

//----------------------------------------------------------------------------
//  Tests for synchronous operation -- this is handled in AbstractAppender,
//  so only needs to be tested for one appender type
//----------------------------------------------------------------------------

    @Test
    public void testSynchronousModeSingleThread() throws Exception
    {
        init("testSynchronousModeSingleThread");
        super.testSynchronousModeSingleThread(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testSynchronousModeMultiThread() throws Exception
    {
        init("testSynchronousModeMultiThread");
        super.testSynchronousModeMultiThread(new LoggerInfo("TestLogger", "test"));
    }
}
