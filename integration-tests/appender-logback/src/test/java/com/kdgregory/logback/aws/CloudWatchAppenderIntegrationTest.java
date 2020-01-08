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
import java.util.Arrays;

import org.junit.After;
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

import com.kdgregory.logback.aws.testhelpers.MessageWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.testhelpers.CloudWatchTestHelper;
import com.kdgregory.logging.testhelpers.CommonTestHelper;


public class CloudWatchAppenderIntegrationTest
{
    // CHANGE THIS IF YOU CHANGE THE CONFIG
    private final static String LOGSTREAM_BASE  = "AppenderTest";

    // this client is shared by all tests
    private static AWSLogs helperClient;

    // this one is used solely by the static factory test
    private static AWSLogs factoryClient;

    private CloudWatchTestHelper testHelper;

    // initialized here, and again by init() after the logging framework has been initialized
    private Logger localLogger = LoggerFactory.getLogger(getClass());

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Retrieves and holds a logger instance and related objects.
     */
    public static class LoggerInfo
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
    }


    /**
     *  This function is used by testFactoryMethod().
     */
    public static AWSLogs createClient()
    {
        factoryClient = AWSLogsClientBuilder.defaultClient();
        return factoryClient;
    }


    /**
     *  Loads the test-specific Logback configuration and resets the environment.
     */
    public void init(String testName) throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        testHelper = new CloudWatchTestHelper(helperClient, "AppenderIntegrationTest-" + testName);

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
        helperClient = AWSLogsClientBuilder.defaultClient();
    }


    @After
    public void tearDown()
    {
        if (factoryClient != null)
        {
            factoryClient.shutdown();
            factoryClient = null;
        }

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

        LoggerInfo loggerInfo = new LoggerInfo("TestLogger", "test");

        (new MessageWriter(loggerInfo.logger, numMessages)).run();

        localLogger.info("waiting for logger");
        CommonTestHelper.waitUntilMessagesSent(loggerInfo.stats, numMessages, 30000);

        testHelper.assertMessages(LOGSTREAM_BASE + "-1", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "-2", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "-3", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "-4", numMessages % rotationCount);

        assertNull("factory should not have been used to create client", factoryClient);

        assertEquals("stats: actual log group name",    "AppenderIntegrationTest-smoketest",    loggerInfo.stats.getActualLogGroupName());
        assertEquals("stats: actual log stream name",   LOGSTREAM_BASE + "-4",                  loggerInfo.stats.getActualLogStreamName());
        assertEquals("stats: messages written",         numMessages,                            loggerInfo.stats.getMessagesSent());

        // with four writers running concurrently, we can't say which wrote the last batch, so we'll test a range of values
        assertInRange("stats: messages in last batch",  1, rotationCount,                       loggerInfo.stats.getMessagesSentLastBatch());

        CloudWatchLogWriter lastWriter = ClassUtil.getFieldValue(loggerInfo.appender, "writer", CloudWatchLogWriter.class);
        assertEquals("number of batches for last writer", 1, lastWriter.getBatchCount());

        // while we're here, verify some more of the plumbing

        loggerInfo.appender.setBatchDelay(1234L);
        assertEquals("batch delay", 1234L, lastWriter.getBatchDelay());
    }


    @Test
    public void testMultipleThreadsSingleAppender() throws Exception
    {
        final int messagesPerThread = 200;
        final int rotationCount     = 300;

        init("testMultipleThreadsSingleAppender");

        LoggerInfo loggerInfo = new LoggerInfo("TestLogger", "test");

        MessageWriter[] writers = new MessageWriter[]
        {
            new MessageWriter(loggerInfo.logger, messagesPerThread),
            new MessageWriter(loggerInfo.logger, messagesPerThread),
            new MessageWriter(loggerInfo.logger, messagesPerThread),
            new MessageWriter(loggerInfo.logger, messagesPerThread),
            new MessageWriter(loggerInfo.logger, messagesPerThread)
        };
        MessageWriter.runOnThreads(writers);

        localLogger.info("waiting for logger");
        CommonTestHelper.waitUntilMessagesSent(loggerInfo.stats, messagesPerThread * 5, 30000);

        testHelper.assertMessages(LOGSTREAM_BASE + "-1", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "-2", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "-3", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "-4", (messagesPerThread * writers.length) % rotationCount);
    }


    @Test
    public void testMultipleThreadsMultipleAppendersDifferentDestinations() throws Exception
    {
        final int messagesPerThread = 1000;

        init("testMultipleThreadsMultipleAppendersDifferentDestinations");

        LoggerInfo loggerInfo1 = new LoggerInfo("TestLogger1", "test1");
        LoggerInfo loggerInfo2 = new LoggerInfo("TestLogger2", "test2");
        LoggerInfo loggerInfo3 = new LoggerInfo("TestLogger3", "test3");

        MessageWriter.runOnThreads(
            new MessageWriter(loggerInfo1.logger, messagesPerThread),
            new MessageWriter(loggerInfo2.logger, messagesPerThread),
            new MessageWriter(loggerInfo3.logger, messagesPerThread));

        localLogger.info("waiting for loggers");
        CommonTestHelper.waitUntilMessagesSent(loggerInfo1.stats, messagesPerThread, 30000);
        CommonTestHelper.waitUntilMessagesSent(loggerInfo2.stats, messagesPerThread, 30000);
        CommonTestHelper.waitUntilMessagesSent(loggerInfo3.stats, messagesPerThread, 30000);

        testHelper.assertMessages(LOGSTREAM_BASE + "-1", messagesPerThread);
        testHelper.assertMessages(LOGSTREAM_BASE + "-2", messagesPerThread);
        testHelper.assertMessages(LOGSTREAM_BASE + "-3", messagesPerThread);
    }


    @Test
    @SuppressWarnings("unused")
    public void testMultipleThreadsMultipleAppendersSameDestination() throws Exception
    {
        final int messagesPerThread = 1000;

        init("testMultipleThreadsMultipleAppendersSameDestination");

        LoggerInfo loggerInfo1 = new LoggerInfo("TestLogger1", "test1");
        LoggerInfo loggerInfo2 = new LoggerInfo("TestLogger2", "test2");
        LoggerInfo loggerInfo3 = new LoggerInfo("TestLogger3", "test3");
        LoggerInfo loggerInfo4 = new LoggerInfo("TestLogger4", "test4");
        LoggerInfo loggerInfo5 = new LoggerInfo("TestLogger5", "test5");

        MessageWriter.runOnThreads(
            new MessageWriter(loggerInfo1.logger, messagesPerThread),
            new MessageWriter(loggerInfo2.logger, messagesPerThread),
            new MessageWriter(loggerInfo3.logger, messagesPerThread),
            new MessageWriter(loggerInfo4.logger, messagesPerThread),
            new MessageWriter(loggerInfo5.logger, messagesPerThread),
            new MessageWriter(loggerInfo1.logger, messagesPerThread),
            new MessageWriter(loggerInfo2.logger, messagesPerThread),
            new MessageWriter(loggerInfo3.logger, messagesPerThread),
            new MessageWriter(loggerInfo4.logger, messagesPerThread),
            new MessageWriter(loggerInfo5.logger, messagesPerThread),
            new MessageWriter(loggerInfo1.logger, messagesPerThread),
            new MessageWriter(loggerInfo2.logger, messagesPerThread),
            new MessageWriter(loggerInfo3.logger, messagesPerThread),
            new MessageWriter(loggerInfo4.logger, messagesPerThread),
            new MessageWriter(loggerInfo5.logger, messagesPerThread),
            new MessageWriter(loggerInfo1.logger, messagesPerThread),
            new MessageWriter(loggerInfo2.logger, messagesPerThread),
            new MessageWriter(loggerInfo3.logger, messagesPerThread),
            new MessageWriter(loggerInfo4.logger, messagesPerThread),
            new MessageWriter(loggerInfo5.logger, messagesPerThread));

        localLogger.info("waiting for loggers");
        CommonTestHelper.waitUntilMessagesSent(loggerInfo1.stats, messagesPerThread * 4, 30000);
        CommonTestHelper.waitUntilMessagesSent(loggerInfo2.stats, messagesPerThread * 4, 30000);
        CommonTestHelper.waitUntilMessagesSent(loggerInfo3.stats, messagesPerThread * 4, 30000);
        CommonTestHelper.waitUntilMessagesSent(loggerInfo4.stats, messagesPerThread * 4, 30000);
        CommonTestHelper.waitUntilMessagesSent(loggerInfo5.stats, messagesPerThread * 4, 30000);

        // even after waiting until the stats say we've written everything, the read won't succeed
        // if we try it immediately ... so we sleep, while CloudWatch puts everything in its place
        Thread.sleep(10000);

        testHelper.assertMessages(LOGSTREAM_BASE, messagesPerThread * 20);

        int messageCountFromStats = 0;
        int messagesDiscardedFromStats = 0;
        int raceRetriesFromStats = 0;
        int unrecoveredRaceRetriesFromStats = 0;
        boolean raceReportedInStats = false;
        String lastNonRaceErrorFromStats = null;

        for (LoggerInfo info : Arrays.asList(loggerInfo1, loggerInfo2, loggerInfo3, loggerInfo4, loggerInfo5))
        {
            messageCountFromStats           += info.stats.getMessagesSent();
            messagesDiscardedFromStats      += info.stats.getMessagesDiscarded();
            raceRetriesFromStats            += info.stats.getWriterRaceRetries();
            unrecoveredRaceRetriesFromStats += info.stats.getUnrecoveredWriterRaceRetries();

            String lastErrorMessage = info.stats.getLastErrorMessage();
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
        // note: we configure the stream to use a sequence number, but it shouldn't change
        //       during this test: we re-create the stream, not the writer
        final String streamName  = LOGSTREAM_BASE + "-1";
        final int numMessages    = 100;

        init("testLogstreamDeletionAndRecreation");

        LoggerInfo loggerInfo = new LoggerInfo("TestLogger", "test");

        localLogger.info("writing first batch");
        (new MessageWriter(loggerInfo.logger, numMessages)).run();

        CommonTestHelper.waitUntilMessagesSent(loggerInfo.stats, numMessages, 30000);
        testHelper.assertMessages(streamName, numMessages);

        localLogger.info("deleting stream");
        testHelper.deleteLogStream(streamName);

        localLogger.info("writing second batch");
        (new MessageWriter(loggerInfo.logger, numMessages)).run();

        // the original batch of messages will be gone, so we can assert the new batch was written
        // however, the writer doesn't change so the stats will keep increasing

        CommonTestHelper.waitUntilMessagesSent(loggerInfo.stats, numMessages * 2, 30000);
        testHelper.assertMessages(streamName, numMessages);

        assertEquals("all messages reported in stats",  numMessages * 2, loggerInfo.stats.getMessagesSent());
        assertTrue("statistics has error message",      loggerInfo.stats.getLastErrorMessage().contains("log stream missing"));
    }


    @Test
    public void testFactoryMethod() throws Exception
    {
        final int numMessages     = 1001;

        init("testFactoryMethod");

        LoggerInfo loggerInfo = new LoggerInfo("TestLogger", "test");

        (new MessageWriter(loggerInfo.logger, numMessages)).run();

        localLogger.info("waiting for logger");
        CommonTestHelper.waitUntilMessagesSent(loggerInfo.stats, numMessages, 30000);

        testHelper.assertMessages(LOGSTREAM_BASE, numMessages);

        CloudWatchLogWriter writer = ClassUtil.getFieldValue(loggerInfo.appender, "writer", CloudWatchLogWriter.class);
        AWSLogs writerClient = ClassUtil.getFieldValue(writer, "client", AWSLogs.class);

        assertSame("factory should have been used to create client", factoryClient, writerClient);
    }


    @Test
    public void testAlternateRegion() throws Exception
    {
        final int numMessages = 1001;

        // BEWARE: my default region is us-east-1, so I use us-east-2 as the alternate
        //         if this is your default, then the test will fail
        AWSLogs altClient = AWSLogsClientBuilder.standard().withRegion("us-east-2").build();
        CloudWatchTestHelper altTestHelper = new CloudWatchTestHelper(altClient, "AppenderIntegrationTest-testAlternateRegion");

        // have to delete any eisting log group before initializing logger
        altTestHelper.deleteLogGroupIfExists();

        init("testAlternateRegion");

        LoggerInfo loggerInfo = new LoggerInfo("TestLogger", "test");

        (new MessageWriter(loggerInfo.logger, numMessages)).run();

        localLogger.info("waiting for logger");
        CommonTestHelper.waitUntilMessagesSent(loggerInfo.stats, numMessages, 30000);

        altTestHelper.assertMessages(LOGSTREAM_BASE, numMessages);
        assertFalse("logstream does not exist in default region", testHelper.isLogStreamAvailable(LOGSTREAM_BASE));
    }

//----------------------------------------------------------------------------
//  Tests for synchronous operation -- this is handled in AbstractAppender,
//  so only needs to be tested for one appender type
//----------------------------------------------------------------------------

    @Test
    public void testSynchronousModeSingleThread() throws Exception
    {
        init("testSynchronousModeSingleThread");

        LoggerInfo loggerInfo = new LoggerInfo("TestLogger", "test");

        localLogger.info("writing message");
        (new MessageWriter(loggerInfo.logger, 1)).run();

        assertEquals("number of messages recorded in stats", 1, loggerInfo.stats.getMessagesSent());

        // no need to wait, the message should be there as soon as we call
        testHelper.assertMessages(LOGSTREAM_BASE, 1);
    }


    @Test
    public void testSynchronousModeMultiThread() throws Exception
    {
        // we could do a lot of messages, but that will run very slowly
        final int messagesPerThread = 10;

        init("testSynchronousModeMultiThread");

        LoggerInfo loggerInfo = new LoggerInfo("TestLogger", "test");

        MessageWriter[] writers = new MessageWriter[]
        {
            new MessageWriter(loggerInfo.logger, messagesPerThread),
            new MessageWriter(loggerInfo.logger, messagesPerThread),
            new MessageWriter(loggerInfo.logger, messagesPerThread),
            new MessageWriter(loggerInfo.logger, messagesPerThread),
            new MessageWriter(loggerInfo.logger, messagesPerThread)
        };
        MessageWriter.runOnThreads(writers);

        // all messages should be written when the threads complete

        assertEquals("number of messages recorded in stats", messagesPerThread * 5, loggerInfo.stats.getMessagesSent());

        testHelper.assertMessages(LOGSTREAM_BASE, messagesPerThread * 5);
    }


    @Test
    public void testRetentionPeriod() throws Exception
    {
        init("testRetentionPeriod");

        LoggerInfo loggerInfo = new LoggerInfo("TestLogger", "test");

        // need to write a single message to ensure group/stream are created
        (new MessageWriter(loggerInfo.logger, 1)).run();

        localLogger.info("waiting for logger");
        CommonTestHelper.waitUntilMessagesSent(loggerInfo.stats, 1, 30000);

        assertEquals("retention period", 7, testHelper.describeLogGroup().getRetentionInDays().intValue());
    }
}
