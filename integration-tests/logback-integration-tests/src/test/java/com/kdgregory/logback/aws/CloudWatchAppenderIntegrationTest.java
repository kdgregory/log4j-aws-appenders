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

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import net.sf.kdgcommons.lang.ClassUtil;
import static net.sf.kdgcommons.test.NumericAsserts.*;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.testhelpers.CloudWatchTestHelper;


public class CloudWatchAppenderIntegrationTest
{
    // single client is shared by all tests
    private static AWSLogs cloudwatchClient;

    // CHANGE THESE IF YOU CHANGE THE CONFIG
    private final static String LOGSTREAM_BASE  = "AppenderTest";

    private CloudWatchTestHelper testHelper;

    // initialized here, and again by init() after the logging framework has been initialized
    private Logger localLogger = LoggerFactory.getLogger(getClass());

    // this is only set by smoketest
    private static boolean localFactoryUsed;

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
     *  Loads the test-specific Logback configuration and resets the environment.
     */
    public void init(String testName) throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        testHelper = new CloudWatchTestHelper(cloudwatchClient, "AppenderIntegrationTest-" + testName);

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

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    {
        cloudwatchClient = AWSLogsClientBuilder.defaultClient();
    }


    @Before
    public void setUp()
    {
        // this won't be updated by most tests
        localFactoryUsed = false;
    }


    @After
    public void tearDown()
    {
        localLogger.info("finished");
        MDC.clear();
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

        ch.qos.logback.classic.Logger testLogger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger("TestLogger");
        CloudWatchAppender<ILoggingEvent> appender = (CloudWatchAppender<ILoggingEvent>)testLogger.getAppender("test");

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("all messages written");

        testHelper.assertMessages(LOGSTREAM_BASE + "-1", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "-2", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "-3", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "-4", numMessages % rotationCount);

        assertTrue("client factory should have been invoked", localFactoryUsed);

        CloudWatchWriterStatistics appenderStats = appender.getAppenderStatistics();
        assertEquals("stats: actual log group name",    "AppenderIntegrationTest-smoketest",    appenderStats.getActualLogGroupName());
        assertEquals("stats: actual log stream name",   LOGSTREAM_BASE + "-4",                  appenderStats.getActualLogStreamName());
        assertEquals("stats: messages written",         numMessages,                            appenderStats.getMessagesSent());

        // with four writers running concurrently, we can't say which wrote the last batch
        // so will just verify that the stats are updated
        assertInRange("stats: messages in last batch",  1, rotationCount,                       appenderStats.getMessagesSentLastBatch());

        CloudWatchLogWriter lastWriter = ClassUtil.getFieldValue(appender, "writer", CloudWatchLogWriter.class);
        assertEquals("number of batches for last writer", 1, lastWriter.getBatchCount());

        // while we're here, verify some more of the plumbing

        appender.setBatchDelay(1234L);
        assertEquals("batch delay", 1234L, lastWriter.getBatchDelay());
    }


    @Test
    public void testMultipleThreadsSingleAppender() throws Exception
    {
        final int messagesPerThread = 200;
        final int rotationCount     = 300;

        init("testMultipleThreadsSingleAppender");

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

        localLogger.info("all threads started");

        testHelper.assertMessages(LOGSTREAM_BASE + "-1", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "-2", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "-3", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "-4", (messagesPerThread * writers.length) % rotationCount);

        assertFalse("client factory should not have been invoked", localFactoryUsed);
    }


    @Test
    public void testMultipleThreadsMultipleAppendersDifferentDestinations() throws Exception
    {
        final int messagesPerThread = 300;

        init("testMultipleThreadsMultipleAppendersDifferentDestinations");

        MessageWriter.runOnThreads(
            new MessageWriter(LoggerFactory.getLogger("TestLogger1"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger2"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger3"), messagesPerThread));

        localLogger.info("all threads started");

        testHelper.assertMessages(LOGSTREAM_BASE + "-1", messagesPerThread);
        testHelper.assertMessages(LOGSTREAM_BASE + "-2", messagesPerThread);
        testHelper.assertMessages(LOGSTREAM_BASE + "-3", messagesPerThread);

        assertFalse("client factory should not have been invoked", localFactoryUsed);
    }


    @Test
    @SuppressWarnings("unused")
    public void testMultipleThreadsMultipleAppendersSameDestination() throws Exception
    {
        final int messagesPerThread = 1000;

        init("testMultipleThreadsMultipleAppendersSameDestination");

        MessageWriter.runOnThreads(
            new MessageWriter(LoggerFactory.getLogger("TestLogger1"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger2"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger3"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger4"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger5"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger1"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger2"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger3"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger4"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger5"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger1"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger2"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger3"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger4"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger5"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger1"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger2"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger3"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger4"), messagesPerThread),
            new MessageWriter(LoggerFactory.getLogger("TestLogger5"), messagesPerThread));

        localLogger.info("all threads started");

        testHelper.assertMessages(LOGSTREAM_BASE, messagesPerThread * 20);

        int messageCountFromStats = 0;
        int messagesDiscardedFromStats = 0;
        int raceRetriesFromStats = 0;
        boolean raceReportedInStats = false;
        String lastNonRaceErrorFromStats = null;
        for (int appenderNumber = 1 ; appenderNumber <= 5 ; appenderNumber++)
        {
            ch.qos.logback.classic.Logger testLogger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger("TestLogger" + appenderNumber);
            CloudWatchAppender<?> appender = (CloudWatchAppender<?>)testLogger.getAppender("test" + appenderNumber);
            CloudWatchWriterStatistics stats = appender.getAppenderStatistics();
            messageCountFromStats += stats.getMessagesSent();
            messagesDiscardedFromStats += stats.getMessagesDiscarded();
            raceRetriesFromStats += stats.getWriterRaceRetries();

            String lastErrorMessage = stats.getLastErrorMessage();
            if (lastErrorMessage != null)
            {
                if (lastErrorMessage.contains("InvalidSequenceTokenException"))
                    raceReportedInStats = true;
                else
                    lastNonRaceErrorFromStats = lastErrorMessage;
            }
        }

        assertEquals("stats: message count",        messagesPerThread * 20, messageCountFromStats);
        assertEquals("stats: messages discarded",   0,                      messagesDiscardedFromStats);

        // manually enable these two assertions -- this test does not reliably create a race retry since 2.0.2

//        assertTrue("stats: race retries",                       raceRetriesFromStats > 0);
//        assertEquals("stats: all race retries recovered",   0,  unrecoveredRaceRetriesFromStats);

        // perhaps we shouldn't fail the test if we received a different error (because it was retried),
        // but we shouldn't be getting any
        assertNull("stats: last error (was: " + lastNonRaceErrorFromStats + ")", lastNonRaceErrorFromStats);
    }


    @Test
    public void testLogstreamDeletionAndRecreation() throws Exception
    {
        final String logStreamName = LOGSTREAM_BASE + "-1";
        final int numMessages      = 100;

        init("testLogstreamDeletionAndRecreation");

        ch.qos.logback.classic.Logger testLogger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger("TestLogger");
        CloudWatchAppender<ILoggingEvent> appender = (CloudWatchAppender<ILoggingEvent>)testLogger.getAppender("test");

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("first batch of messages written");

        testHelper.assertMessages(logStreamName, numMessages);

        localLogger.info("deleting stream");
        testHelper.deleteLogStream(logStreamName);

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("second batch of messages written");

        // the original batch of messages will be gone, so we can assert the new batch was written
        testHelper.assertMessages(logStreamName, numMessages);

        assertTrue("statistics has error message", appender.getAppenderStatistics().getLastErrorMessage().contains("log stream missing"));

        localLogger.info("finished");
    }


    @Test
    public void testAlternateRegion() throws Exception
    {
        final int numMessages = 1001;

        init("testAlternateRegion");

        // BEWARE: my default region is us-east-1, so I use us-east-2 as the alternate
        //         if this is your default, then the test will fail
        AWSLogs altClient = AWSLogsClientBuilder.standard().withRegion("us-east-2").build();
        CloudWatchTestHelper altTestHelper = new CloudWatchTestHelper(altClient, "AppenderIntegrationTest-testAlternateRegion");

        altTestHelper.deleteLogGroupIfExists();

        ch.qos.logback.classic.Logger testLogger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger("TestLogger");
        CloudWatchAppender<ILoggingEvent> appender = (CloudWatchAppender<ILoggingEvent>)testLogger.getAppender("test");

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("all messages written");

        altTestHelper.assertMessages(LOGSTREAM_BASE, numMessages);
        assertFalse("logstream does not exist in default region", testHelper.isLogStreamAvailable(LOGSTREAM_BASE));

        assertEquals("stats: messages written",  numMessages,  appender.getAppenderStatistics().getMessagesSent());
    }

//----------------------------------------------------------------------------
//  Tests for synchronous operation -- this is handled in AbstractAppender,
//  so only needs to be tested for one appender type
//----------------------------------------------------------------------------

    @Test
    public void testSynchronousModeSingleThread() throws Exception
    {
        init("testSynchronousModeSingleThread");

        ch.qos.logback.classic.Logger testLogger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger("TestLogger");
        CloudWatchAppender<ILoggingEvent> appender = (CloudWatchAppender<ILoggingEvent>)testLogger.getAppender("test");

        localLogger.info("writing first message");

        (new MessageWriter(testLogger, 1)).run();

        assertEquals("number of messages recorded in stats", 1, appender.getAppenderStatistics().getMessagesSent());

        testHelper.assertMessages(LOGSTREAM_BASE, 1);
    }


    @Test
    public void testSynchronousModeMultiThread() throws Exception
    {
        // we could do a lot of messages, but that will run very slowly
        final int messagesPerThread = 10;

        init("testSynchronousModeMultiThread");

        ch.qos.logback.classic.Logger testLogger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger("TestLogger");
        CloudWatchAppender<ILoggingEvent> appender = (CloudWatchAppender<ILoggingEvent>)testLogger.getAppender("test");

        localLogger.info("writing messages");

        MessageWriter[] writers = new MessageWriter[]
        {
            new MessageWriter(testLogger, messagesPerThread),
            new MessageWriter(testLogger, messagesPerThread),
            new MessageWriter(testLogger, messagesPerThread),
            new MessageWriter(testLogger, messagesPerThread),
            new MessageWriter(testLogger, messagesPerThread)
        };
        MessageWriter.runOnThreads(writers);

        assertEquals("number of messages recorded in stats", messagesPerThread * 5, appender.getAppenderStatistics().getMessagesSent());

        testHelper.assertMessages(LOGSTREAM_BASE, messagesPerThread * 5);
    }
}
