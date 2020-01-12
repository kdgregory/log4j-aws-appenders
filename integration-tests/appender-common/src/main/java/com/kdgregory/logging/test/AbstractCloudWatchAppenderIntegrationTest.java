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

package com.kdgregory.logging.test;

import static net.sf.kdgcommons.test.NumericAsserts.*;

import static org.junit.Assert.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.kdgcommons.lang.ClassUtil;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.testhelpers.CloudWatchTestHelper;
import com.kdgregory.logging.testhelpers.CommonTestHelper;
import com.kdgregory.logging.testhelpers.MessageWriter;


public abstract class AbstractCloudWatchAppenderIntegrationTest
{
    // CHANGE THIS IF YOU CHANGE THE CONFIG
    protected final static String LOGSTREAM_BASE  = "AppenderTest";

    // initialized here, and again by init() after the logging framework has been initialized
    protected Logger localLogger = LoggerFactory.getLogger(getClass());

    // this client is shared by all tests
    protected static AWSLogs helperClient;

    // this one is used solely by the static factory test
    protected static AWSLogs factoryClient;

    protected CloudWatchTestHelper testHelper;

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Subclasses must implement this to give the common tests access to the
     *  logger components.
     */
    public interface LoggerAccessor
    {
        MessageWriter createMessageWriter(int numMessages);

        CloudWatchLogWriter getWriter() throws Exception;
        CloudWatchWriterStatistics getStats();

        void setBatchDelay(long value);
    }


    /**
     *  This function is used by testFactoryMethod().
     */
    public static AWSLogs createClient()
    {
        factoryClient = AWSLogsClientBuilder.defaultClient();
        return factoryClient;
    }

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    public static void beforeClass()
    {
        helperClient = AWSLogsClientBuilder.defaultClient();
    }


    public void tearDown()
    {
        if (factoryClient != null)
        {
            factoryClient.shutdown();
            factoryClient = null;
        }

        localLogger.info("finished");
    }


//----------------------------------------------------------------------------
//  Test Bodies
//----------------------------------------------------------------------------

    protected void smoketest(LoggerAccessor accessor)
    throws Exception
    {
        // configured values; should be the same for all frameworks
        final int numMessages     = 1001;
        final int rotationCount   = 333;

        MessageWriter messageWriter = accessor.createMessageWriter(numMessages);
        messageWriter.run();

        localLogger.info("waiting for logger");
        CommonTestHelper.waitUntilMessagesSent(accessor.getStats(), numMessages, 30000);

        testHelper.assertMessages(LOGSTREAM_BASE + "-1", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "-2", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "-3", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "-4", numMessages % rotationCount);

        assertNull("factory should not have been used to create client", factoryClient);

        assertEquals("stats: actual log group name",    "AppenderIntegrationTest-smoketest",    accessor.getStats().getActualLogGroupName());
        assertEquals("stats: actual log stream name",   LOGSTREAM_BASE + "-4",                  accessor.getStats().getActualLogStreamName());
        assertEquals("stats: messages written",         numMessages,                            accessor.getStats().getMessagesSent());

        // with four writers running concurrently, we can't say which wrote the last batch, so we'll test a range of values
        assertInRange("stats: messages in last batch",  1, rotationCount,                       accessor.getStats().getMessagesSentLastBatch());
        assertEquals("number of batches for last writer", 1, accessor.getWriter().getBatchCount());

        // while we're here, verify some more of the plumbing

        assertEquals("retention period", 7, testHelper.describeLogGroup().getRetentionInDays().intValue());

        accessor.setBatchDelay(1234L);
        assertEquals("batch delay", 1234L, accessor.getWriter().getBatchDelay());
    }


    protected void testMultipleThreadsSingleAppender(LoggerAccessor accessor)
    throws Exception
    {
        // configured values; should be the same for all frameworks
        final int messagesPerThread = 200;
        final int rotationCount     = 300;

        MessageWriter[] messageWriters = new MessageWriter[]
        {
            accessor.createMessageWriter(messagesPerThread),
            accessor.createMessageWriter(messagesPerThread),
            accessor.createMessageWriter(messagesPerThread),
            accessor.createMessageWriter(messagesPerThread),
            accessor.createMessageWriter(messagesPerThread)
        };
        MessageWriter.runOnThreads(messageWriters);

        localLogger.info("waiting for logger");
        CommonTestHelper.waitUntilMessagesSent(accessor.getStats(), messagesPerThread * 5, 30000);

        testHelper.assertMessages(LOGSTREAM_BASE + "-1", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "-2", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "-3", rotationCount);
        testHelper.assertMessages(LOGSTREAM_BASE + "-4", (messagesPerThread * messageWriters.length) % rotationCount);
    }


    protected void testMultipleThreadsMultipleAppendersDifferentDestinations(LoggerAccessor... accessors)
    throws Exception
    {
        // configured values; should be the same for all frameworks
        final int messagesPerThread = 1000;

        MessageWriter.runOnThreads(
            accessors[0].createMessageWriter(messagesPerThread),
            accessors[1].createMessageWriter(messagesPerThread),
            accessors[2].createMessageWriter(messagesPerThread));

        localLogger.info("waiting for loggers");
        CommonTestHelper.waitUntilMessagesSent(accessors[0].getStats(), messagesPerThread, 30000);
        CommonTestHelper.waitUntilMessagesSent(accessors[1].getStats(), messagesPerThread, 30000);
        CommonTestHelper.waitUntilMessagesSent(accessors[2].getStats(), messagesPerThread, 30000);

        testHelper.assertMessages(LOGSTREAM_BASE + "-1", messagesPerThread);
        testHelper.assertMessages(LOGSTREAM_BASE + "-2", messagesPerThread);
        testHelper.assertMessages(LOGSTREAM_BASE + "-3", messagesPerThread);
    }


    @SuppressWarnings("unused")
    protected void testMultipleThreadsMultipleAppendersSameDestination(LoggerAccessor... accessors)
    throws Exception
    {
        // configured values; should be the same for all frameworks
        final int messagesPerThread = 1000;

        MessageWriter.runOnThreads(
            accessors[0].createMessageWriter(messagesPerThread),
            accessors[1].createMessageWriter(messagesPerThread),
            accessors[2].createMessageWriter(messagesPerThread),
            accessors[3].createMessageWriter(messagesPerThread),
            accessors[4].createMessageWriter(messagesPerThread),
            accessors[0].createMessageWriter(messagesPerThread),
            accessors[1].createMessageWriter(messagesPerThread),
            accessors[2].createMessageWriter(messagesPerThread),
            accessors[3].createMessageWriter(messagesPerThread),
            accessors[4].createMessageWriter(messagesPerThread),
            accessors[0].createMessageWriter(messagesPerThread),
            accessors[1].createMessageWriter(messagesPerThread),
            accessors[2].createMessageWriter(messagesPerThread),
            accessors[3].createMessageWriter(messagesPerThread),
            accessors[4].createMessageWriter(messagesPerThread),
            accessors[0].createMessageWriter(messagesPerThread),
            accessors[1].createMessageWriter(messagesPerThread),
            accessors[2].createMessageWriter(messagesPerThread),
            accessors[3].createMessageWriter(messagesPerThread),
            accessors[4].createMessageWriter(messagesPerThread));

        localLogger.info("waiting for loggers");
        for (LoggerAccessor accessor : accessors)
        {
            CommonTestHelper.waitUntilMessagesSent(accessor.getStats(), messagesPerThread * 4, 30000);
        }

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

        for (LoggerAccessor accessor : accessors)
        {
            CloudWatchWriterStatistics stats = accessor.getStats();
            messageCountFromStats           += stats.getMessagesSent();
            messagesDiscardedFromStats      += stats.getMessagesDiscarded();
            raceRetriesFromStats            += stats.getWriterRaceRetries();
            unrecoveredRaceRetriesFromStats += stats.getUnrecoveredWriterRaceRetries();

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

        assertNull("stats: last error (was: " + lastNonRaceErrorFromStats + ")", lastNonRaceErrorFromStats);
    }


    protected void testLogstreamDeletionAndRecreation(LoggerAccessor accessor)
    throws Exception
    {
        // note: we configure the stream to use a sequence number, but it shouldn't change
        //       during this test: we re-create the stream, not the writer
        final String streamName  = LOGSTREAM_BASE + "-1";
        final int numMessages    = 100;

        localLogger.info("writing first batch");
        accessor.createMessageWriter(numMessages).run();

        CommonTestHelper.waitUntilMessagesSent(accessor.getStats(), numMessages, 30000);
        testHelper.assertMessages(streamName, numMessages);

        localLogger.info("deleting stream");
        testHelper.deleteLogStream(streamName);

        localLogger.info("writing second batch");
        accessor.createMessageWriter(numMessages).run();

        // the original batch of messages will be gone, so we can assert the new batch was written
        // however, the writer doesn't change so the stats will keep increasing

        CommonTestHelper.waitUntilMessagesSent(accessor.getStats(), numMessages * 2, 30000);
        testHelper.assertMessages(streamName, numMessages);

        assertEquals("all messages reported in stats",  numMessages * 2, accessor.getStats().getMessagesSent());
        assertTrue("statistics has error message",      accessor.getStats().getLastErrorMessage().contains("log stream missing"));
    }


    protected void testFactoryMethod(LoggerAccessor accessor)
    throws Exception
    {
        // again, defined in configuration
        final int numMessages     = 1001;

        accessor.createMessageWriter(numMessages).run();

        localLogger.info("waiting for logger");
        CommonTestHelper.waitUntilMessagesSent(accessor.getStats(), numMessages, 30000);

        testHelper.assertMessages(LOGSTREAM_BASE, numMessages);

        AWSLogs writerClient = ClassUtil.getFieldValue(accessor.getWriter(), "client", AWSLogs.class);
        assertSame("factory should have been used to create client", factoryClient, writerClient);
    }


    protected void testAlternateRegion(LoggerAccessor accessor)
    throws Exception
    {
        final int numMessages = 1001;

        // BEWARE: my default region is us-east-1, so I use us-east-2 as the alternate
        //         if that is your default, then the test will fail
        AWSLogs altClient = AWSLogsClientBuilder.standard().withRegion("us-east-2").build();
        CloudWatchTestHelper altTestHelper = new CloudWatchTestHelper(altClient, "AppenderIntegrationTest-testAlternateRegion");

        altTestHelper.deleteLogGroupIfExists();

        accessor.createMessageWriter(numMessages).run();

        localLogger.info("waiting for logger");
        CommonTestHelper.waitUntilMessagesSent(accessor.getStats(), numMessages, 30000);

        altTestHelper.assertMessages(LOGSTREAM_BASE, numMessages);
        assertFalse("logstream does not exist in default region", testHelper.isLogStreamAvailable(LOGSTREAM_BASE));
    }


    protected void testSynchronousModeSingleThread(LoggerAccessor accessor)
    throws Exception
    {
        localLogger.info("writing message");
        accessor.createMessageWriter(1).run();

        assertEquals("number of messages recorded in stats", 1, accessor.getStats().getMessagesSent());

        testHelper.assertMessages(LOGSTREAM_BASE, 1);
    }


    protected void testSynchronousModeMultiThread(LoggerAccessor accessor)
    throws Exception
    {
        // we could do a lot of messages, but that will run very slowly
        final int messagesPerThread = 10;

        MessageWriter[] writers = new MessageWriter[]
        {
            accessor.createMessageWriter(messagesPerThread),
            accessor.createMessageWriter(messagesPerThread),
            accessor.createMessageWriter(messagesPerThread),
            accessor.createMessageWriter(messagesPerThread),
            accessor.createMessageWriter(messagesPerThread)
        };
        MessageWriter.runOnThreads(writers);

        assertEquals("number of messages recorded in stats", messagesPerThread * 5, accessor.getStats().getMessagesSent());

        testHelper.assertMessages(LOGSTREAM_BASE, messagesPerThread * 5);
    }
}
