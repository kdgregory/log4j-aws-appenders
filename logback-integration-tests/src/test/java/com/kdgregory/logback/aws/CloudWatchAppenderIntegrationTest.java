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

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.kdgcommons.lang.ClassUtil;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.testhelpers.cloudwatch.CloudWatchTestHelper;


public class CloudWatchAppenderIntegrationTest
{
    // CHANGE THESE IF YOU CHANGE THE CONFIG
    private final static String LOGSTREAM_BASE  = "AppenderTest-";

    private CloudWatchTestHelper testHelper;

    // this gets set by init() after the logging framework has been initialized
    private Logger localLogger;

    // this is only set by smoketest
    private static boolean localFactoryUsed;

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        localFactoryUsed = false;
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void smoketest() throws Exception
    {
        final int numMessages     = 1001;
        final int rotationCount   = 333;

        init("smoketest");
        localLogger.info("smoketest: starting");

        ch.qos.logback.classic.Logger testLogger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger("TestLogger");
        CloudWatchAppender<ILoggingEvent> appender = (CloudWatchAppender<ILoggingEvent>)testLogger.getAppender("test");

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("smoketest: all messages written; sleeping to give writers chance to run");
        Thread.sleep(5000);

        testHelper.assertMessages(LOGSTREAM_BASE + "1", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "2", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "3", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "4", numMessages % rotationCount);

        CloudWatchWriterStatistics appenderStats = appender.getAppenderStatistics();
        assertEquals("actual log group name, from statistics",  "AppenderIntegrationTest-smoketest",    appenderStats.getActualLogGroupName());
        assertEquals("actual log stream name, from statistics", LOGSTREAM_BASE + "4",                   appenderStats.getActualLogStreamName());
        assertEquals("messages written, from statistics",       numMessages,                            appenderStats.getMessagesSent());

        CloudWatchLogWriter lastWriter = ClassUtil.getFieldValue(appender, "writer", CloudWatchLogWriter.class);
        assertEquals("number of batches for last writer", 1, lastWriter.getBatchCount());

        assertTrue("client factory used", localFactoryUsed);

        // while we're here, verify some more of the plumbing

        appender.setBatchDelay(1234L);
        assertEquals("batch delay", 1234L, lastWriter.getBatchDelay());

        localLogger.info("smoketest: finished");
    }


    @Test
    public void testMultipleThreadsSingleAppender() throws Exception
    {
        final int messagesPerThread = 200;
        final int rotationCount     = 333;

        init("testMultipleThreadsSingleAppender");
        localLogger.info("multi-thread/single-appender: starting");

        ch.qos.logback.classic.Logger testLogger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger("TestLogger");

        MessageWriter[] writers = new MessageWriter[]
        {
            new MessageWriter(testLogger, messagesPerThread),
            new MessageWriter(testLogger, messagesPerThread),
            new MessageWriter(testLogger, messagesPerThread),
            new MessageWriter(testLogger, messagesPerThread),
            new MessageWriter(testLogger, messagesPerThread)
        };
        MessageWriter.runOnThreads(writers);

        localLogger.info("multi-thread/single-appender: all threads started; sleeping to give writer chance to run");
        Thread.sleep(3000);

        testHelper.assertMessages(LOGSTREAM_BASE + "1", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "2", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "3", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "4", (messagesPerThread * writers.length) % rotationCount);

        assertFalse("client factory used", localFactoryUsed);

        localLogger.info("multi-thread/single-appender: finished");
    }


    @Test
    public void testMultipleThreadsMultipleAppenders() throws Exception
    {
        final int messagesPerThread = 300;

        init("testMultipleThreadsMultipleAppenders");
        localLogger.info("multi-thread/multi-appender: starting");

        MessageWriter.runOnThreads(
            new MessageWriter(LoggerFactory.getLogger("TestLogger1"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger2"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger3"), messagesPerThread));

        localLogger.info("multi-thread/multi-appender: all threads started; sleeping to give writer chance to run");
        Thread.sleep(3000);

        testHelper.assertMessages(LOGSTREAM_BASE + "1", messagesPerThread);
        testHelper.assertMessages(LOGSTREAM_BASE + "2", messagesPerThread);
        testHelper.assertMessages(LOGSTREAM_BASE + "3", messagesPerThread);

        assertFalse("client factory used", localFactoryUsed);

        localLogger.info("multi-thread/multi-appender: finished");
    }


    @Test
    public void testLogstreamDeletionAndRecreation() throws Exception
    {
        final String logStreamName = LOGSTREAM_BASE + "1";
        final int numMessages      = 100;

        init("testLogstreamDeletionAndRecreation");
        localLogger.info("testLogstreamDeletionAndRecreation: starting");

        ch.qos.logback.classic.Logger testLogger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger("TestLogger");
        CloudWatchAppender<ILoggingEvent> appender = (CloudWatchAppender<ILoggingEvent>)testLogger.getAppender("test");

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("testLogstreamDeletionAndRecreation: first batch of messages written; sleeping to give writer chance to run");
        Thread.sleep(1000);

        testHelper.assertMessages(logStreamName, numMessages);

        localLogger.info("testLogstreamDeletionAndRecreation: deleting stream");
        testHelper.deleteLogStream(logStreamName);

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("testLogstreamDeletionAndRecreation: second batch of messages written; sleeping to give writer chance to run");
        Thread.sleep(2000);

        // the original batch of messages will be gone, so we can assert the new batch was written
        testHelper.assertMessages(logStreamName, numMessages);

        assertTrue("statistics has error message", appender.getAppenderStatistics().getLastErrorMessage().contains("log stream missing"));

        localLogger.info("testLogstreamDeletionAndRecreation: finished");
    }

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Logger-specific implementation of utility class.
     */
    private static class MessageWriter
    extends com.kdgregory.logging.testhelpers.MessageWriter
    {
        private Logger logger;

        public MessageWriter(Logger logger, int numMessages)
        {
            super(numMessages);
            this.logger = logger;
        }

        @Override
        protected void writeLogMessage(String message)
        {
            logger.debug(message);
        }
    }


    /**
     *  This function is used as a client factory by the smoketest.
     */
    public static AWSLogs createClient()
    {
        localFactoryUsed = true;
        return AWSLogsClientBuilder.defaultClient();
    }


    /**
     *  Loads the test-specific Log4J configuration and resets the environment.
     */
    public void init(String testName) throws Exception
    {
        testHelper = new CloudWatchTestHelper(AWSLogsClientBuilder.defaultClient(), "AppenderIntegrationTest-" + testName);

        // this has to happen before the logger is initialized or we have a race condition
        testHelper.deleteLogGroupIfExists();

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
}
