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

import net.sf.kdgcommons.lang.ClassUtil;

import static net.sf.kdgcommons.test.StringAsserts.*;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.aws.facade.CloudWatchFacade;
import com.kdgregory.logging.testhelpers.CloudWatchTestHelper;
import com.kdgregory.logging.testhelpers.CommonTestHelper;
import com.kdgregory.logging.testhelpers.MessageWriter;


/**
 *  This class contains all of the actual test code for the CloudWatch
 *  integration tests. Subclass tests initialize the logging framework
 *  and then call the like-named method here.
 */
public abstract class AbstractCloudWatchAppenderIntegrationTest
{
    // change these if you change the config
    protected final static String BASE_LOGGROUP_NAME = "AppenderIntegrationTest";
    protected final static String LOGSTREAM_BASE  = "AppenderTest";

    // initialized here, and again by init() after the logging framework has been initialized
    protected Logger localLogger = LoggerFactory.getLogger(getClass());

    // this client is shared by all tests
    protected static AWSLogs helperClient;

    // this one is used solely by the static factory test
    protected static AWSLogs factoryClient;

    // this one is used by the alternate region test
    protected AWSLogs altClient;

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
        /**
         *  Creates a new Messagewriter that will log to the tested appender.
         */
        MessageWriter newMessageWriter(int numMessages);

        /**
         *  Retrieves the current writer from the tested appender.
         */
        CloudWatchLogWriter getWriter() throws Exception;

        /**
         *  Returns the statistics object associated with the tested appender.
         */
        CloudWatchWriterStatistics getStats();

        /**
         *  Identifies whether the appender supports post-creation config changes
         *  (used in the smoketest).
         */
        boolean supportsConfigurationChanges();

        /**
         *  Changes the appender's batch delay, iff if supports such changes.
         */
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
//  JUnit Scaffolding -- must be overridden by subclasses (I'm assuming that
//  JUnit doesn't go out of its way to find annotations on superclasses)
//----------------------------------------------------------------------------

    public static void beforeClass()
    throws Exception
    {
        helperClient = AWSLogsClientBuilder.defaultClient();
    }


    public void tearDown()
    throws Exception
    {
        // set by single test, but easier to reset always (if needed)
        if (factoryClient != null)
        {
            factoryClient.shutdown();
            factoryClient = null;
        }

        // set by single test, but easier to reset always (if needed)
        if (altClient != null)
        {
            altClient.shutdown();
            altClient = null;
        }

        localLogger.info("finished");
    }


    public static void afterClass()
    throws Exception
    {
        if (helperClient != null)
        {
            helperClient.shutdown();
        }
    }

//----------------------------------------------------------------------------
//  Test Bodies
//----------------------------------------------------------------------------

    protected void smoketest(LoggerAccessor accessor)
    throws Exception
    {
        // configured values; should be the same for all frameworks
        final int numMessages     = 1001;

        MessageWriter messageWriter = accessor.newMessageWriter(numMessages);
        messageWriter.run();

        localLogger.info("waiting for logger");
        CommonTestHelper.waitUntilMessagesSent(accessor.getStats(), numMessages, 30000);

        assertEquals("stats: actual log group name",    "AppenderIntegrationTest-smoketest",    accessor.getStats().getActualLogGroupName());
        assertRegex("stats: actual log stream name",    LOGSTREAM_BASE + "-\\d{12}",            accessor.getStats().getActualLogStreamName());
        assertEquals("stats: messages written",         numMessages,                            accessor.getStats().getMessagesSent());

        // while we're here, verify some more of the plumbing

        assertNull("factory should not have been used to create client", factoryClient);
        assertEquals("retention period", 7, testHelper.describeLogGroup().getRetentionInDays().intValue());

        if (accessor.supportsConfigurationChanges())
        {
            accessor.setBatchDelay(1234L);
            assertEquals("batch delay", 1234L, accessor.getWriter().getBatchDelay());
        }

        testHelper.deleteLogGroupIfExists();
    }


    protected void testMultipleThreadsSingleAppender(LoggerAccessor accessor)
    throws Exception
    {
        // configured values; should be the same for all frameworks
        final int messagesPerThread = 200;
        final int numMessages = messagesPerThread * 5;

        MessageWriter[] messageWriters = new MessageWriter[]
        {
            accessor.newMessageWriter(messagesPerThread),
            accessor.newMessageWriter(messagesPerThread),
            accessor.newMessageWriter(messagesPerThread),
            accessor.newMessageWriter(messagesPerThread),
            accessor.newMessageWriter(messagesPerThread)
        };
        MessageWriter.runOnThreads(messageWriters);

        localLogger.info("waiting for logger");
        CommonTestHelper.waitUntilMessagesSent(accessor.getStats(), numMessages, 30000);

        testHelper.assertMessages(LOGSTREAM_BASE, numMessages);

        testHelper.deleteLogGroupIfExists();
    }


    protected void testMultipleThreadsMultipleAppendersDifferentDestinations(LoggerAccessor... accessors)
    throws Exception
    {
        final int messagesPerThread = 1000;     // configured value; must be the same for all frameworks

        MessageWriter.runOnThreads(
            accessors[0].newMessageWriter(messagesPerThread),
            accessors[1].newMessageWriter(messagesPerThread),
            accessors[2].newMessageWriter(messagesPerThread));

        localLogger.info("waiting for loggers");
        CommonTestHelper.waitUntilMessagesSent(accessors[0].getStats(), messagesPerThread, 30000);
        CommonTestHelper.waitUntilMessagesSent(accessors[1].getStats(), messagesPerThread, 30000);
        CommonTestHelper.waitUntilMessagesSent(accessors[2].getStats(), messagesPerThread, 30000);

        testHelper.assertMessages(LOGSTREAM_BASE + "-1", messagesPerThread);
        testHelper.assertMessages(LOGSTREAM_BASE + "-2", messagesPerThread);
        testHelper.assertMessages(LOGSTREAM_BASE + "-3", messagesPerThread);

        testHelper.deleteLogGroupIfExists();
    }


    @SuppressWarnings("unused")
    protected void testMultipleThreadsMultipleAppendersSameDestination(LoggerAccessor... accessors)
    throws Exception
    {
        final int messagesPerThread = 1000;     // configured value; must be the same for all frameworks
        final int threadsPerAccessor = 4;
        
        List<MessageWriter> messageWriters = new ArrayList<>();
        for (int ii = 0 ; ii < threadsPerAccessor ; ii++)
        {
            for (int jj = 0 ; jj < accessors.length ; jj++)
            {
                messageWriters.add(accessors[jj].newMessageWriter(messagesPerThread));
            }
        }
        MessageWriter.runOnThreads(messageWriters);

        localLogger.info("waiting for loggers");
        for (LoggerAccessor accessor : accessors)
        {
            CommonTestHelper.waitUntilMessagesSent(accessor.getStats(), messagesPerThread * threadsPerAccessor, 30000);
        }

        // even after waiting until the stats say we've written everything, the read won't succeed
        // if we try it immediately ... so we sleep, while CloudWatch puts everything in its place
        Thread.sleep(15000);

        testHelper.assertMessages(LOGSTREAM_BASE, messagesPerThread * accessors.length * threadsPerAccessor);

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

        testHelper.deleteLogGroupIfExists();
    }


    protected void testLogstreamDeletionAndRecreation(LoggerAccessor accessor)
    throws Exception
    {
        final String streamName  = LOGSTREAM_BASE;
        final int numMessages    = 100;

        localLogger.info("writing first batch");
        accessor.newMessageWriter(numMessages).run();

        CommonTestHelper.waitUntilMessagesSent(accessor.getStats(), numMessages, 30000);
        testHelper.assertMessages(streamName, numMessages);

        localLogger.info("deleting stream");
        testHelper.deleteLogStream(streamName);

        localLogger.info("writing second batch (framework may report error)");
        accessor.newMessageWriter(numMessages).run();

        // the original batch of messages will be gone, so we can assert the new batch was written
        // however, the writer doesn't change so the stats will keep increasing

        CommonTestHelper.waitUntilMessagesSent(accessor.getStats(), numMessages * 2, 30000);
        testHelper.assertMessages(streamName, numMessages);

        assertEquals("all messages reported in stats",  numMessages * 2, accessor.getStats().getMessagesSent());

        testHelper.deleteLogGroupIfExists();
    }


    protected void testFactoryMethod(LoggerAccessor accessor)
    throws Exception
    {
        final int numMessages     = 1001;

        localLogger.info("writing messages");
        accessor.newMessageWriter(numMessages).run();

        localLogger.info("waiting for logger");
        CommonTestHelper.waitUntilMessagesSent(accessor.getStats(), numMessages, 30000);

        testHelper.assertMessages(LOGSTREAM_BASE, numMessages);

        CloudWatchFacade facade = ClassUtil.getFieldValue(accessor.getWriter(), "facade", CloudWatchFacade.class);
        AWSLogs client = ClassUtil.getFieldValue(facade, "client", AWSLogs.class);
        assertSame("factory should have been used to create client", factoryClient, client);

        testHelper.deleteLogGroupIfExists();
    }


    protected void testAlternateRegion(LoggerAccessor accessor, CloudWatchTestHelper altTestHelper)
    throws Exception
    {
        final int numMessages = 1001;

        localLogger.info("writing messages");
        accessor.newMessageWriter(numMessages).run();

        localLogger.info("waiting for logger");
        CommonTestHelper.waitUntilMessagesSent(accessor.getStats(), numMessages, 30000);

        altTestHelper.assertMessages(LOGSTREAM_BASE, numMessages);
        assertFalse("logstream does not exist in default region", testHelper.isLogStreamAvailable(LOGSTREAM_BASE));

        altTestHelper.deleteLogGroupIfExists();
    }


    protected void testAlternateEndpoint(LoggerAccessor accessor, CloudWatchTestHelper altTestHelper)
    throws Exception
    {
        final int numMessages = 1001;

        localLogger.info("writing messages");
        accessor.newMessageWriter(numMessages).run();

        localLogger.info("waiting for logger");
        CommonTestHelper.waitUntilMessagesSent(accessor.getStats(), numMessages, 30000);

        altTestHelper.assertMessages(LOGSTREAM_BASE, numMessages);
        assertFalse("logstream does not exist in default region", testHelper.isLogStreamAvailable(LOGSTREAM_BASE));

        altTestHelper.deleteLogGroupIfExists();
    }


    protected void testAssumedRole(LoggerAccessor accessor)
    throws Exception
    {
        final int numMessages = 1001;

        localLogger.info("writing messages");
        accessor.newMessageWriter(numMessages).run();

        localLogger.info("waiting for logger");
        CommonTestHelper.waitUntilMessagesSent(accessor.getStats(), numMessages, 30000);

        testHelper.assertMessages(LOGSTREAM_BASE, numMessages);
        assertEquals("credentials provider",
                     STSAssumeRoleSessionCredentialsProvider.class,
                     CommonTestHelper.getCredentialsProviderClass(accessor.getWriter())
                     );

        testHelper.deleteLogGroupIfExists();
    }


    protected void testSynchronousModeSingleThread(LoggerAccessor accessor)
    throws Exception
    {
        localLogger.info("writing message");
        accessor.newMessageWriter(1).run();

        assertEquals("number of messages recorded in stats", 1, accessor.getStats().getMessagesSent());

        testHelper.assertMessages(LOGSTREAM_BASE, 1);

        testHelper.deleteLogGroupIfExists();
    }


    protected void testSynchronousModeMultiThread(LoggerAccessor accessor)
    throws Exception
    {
        // if we do too many messages we get throttled ... this will be a problem for real-world use
        final int messagesPerThread = 5;

        MessageWriter[] writers = new MessageWriter[]
        {
            accessor.newMessageWriter(messagesPerThread),
            accessor.newMessageWriter(messagesPerThread),
            accessor.newMessageWriter(messagesPerThread),
            accessor.newMessageWriter(messagesPerThread),
            accessor.newMessageWriter(messagesPerThread)
        };
        MessageWriter.runOnThreads(writers);

        assertEquals("number of messages recorded in stats", messagesPerThread * 5, accessor.getStats().getMessagesSent());

        testHelper.assertMessages(LOGSTREAM_BASE, messagesPerThread * 5);

        testHelper.deleteLogGroupIfExists();
    }
}
