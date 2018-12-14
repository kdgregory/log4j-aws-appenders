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

package com.kdgregory.logging.aws;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.lang.StringUtil;

import static net.sf.kdgcommons.test.StringAsserts.*;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterFactory;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.common.util.MessageQueue;
import com.kdgregory.logging.testhelpers.TestingException;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchClient;


/**
 *  Performs mock-client testing of the CloudWatch writer.
 */
public class TestCloudWatchLogWriter
extends AbstractLogWriterTest<CloudWatchLogWriter,CloudWatchWriterConfig,CloudWatchWriterStatistics,AWSLogs>
{
//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

    /**
     *  Rather than re-create each time, we initialize in setUp(), replace in
     *  tests that need to do so.
     */
    private MockCloudWatchClient mock = new MockCloudWatchClient();


    /**
     *  Creates a writer using the current mock client, waiting for it to be initialized.
     */
    private void createWriter()
    throws Exception
    {
        createWriter(mock.newWriterFactory());
    }

    // the following variable and function are used by testStaticClientFactory

    private static MockCloudWatchClient staticFactoryMock;

    public static AWSLogs createMockClient()
    {
        staticFactoryMock = new MockCloudWatchClient();
        return staticFactoryMock.createClient();
    }

//----------------------------------------------------------------------------
//  JUnit scaffolding
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        // this is the default configuration; may be updated or replaced by test
        config = new CloudWatchWriterConfig(
            "argle",                // log group name
            "bargle",               // log stream name
            100,                    // batch delay -- short enough to keep tests fast, long enough that we can write a lot of messages
            10000,                  // discard threshold
            DiscardAction.oldest,   // discard action
            null,                   // factory method
            null);                  // endpoint

        stats = new CloudWatchWriterStatistics();

        staticFactoryMock = null;
    }


    @After
    public void checkUncaughtExceptions()
    throws Throwable
    {
        if (uncaughtException != null)
            throw uncaughtException;
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testConfiguration() throws Exception
    {
        config = new CloudWatchWriterConfig("foo", "bar", 123, 456, DiscardAction.newest, "com.example.factory.Method", "us-west-1");

        writer = new CloudWatchLogWriter(config, stats, internalLogger, dummyClientFactory);
        messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        assertEquals("writer batch delay",                      123L,                   writer.getBatchDelay());
        assertEquals("message queue discard policy",            DiscardAction.newest,   messageQueue.getDiscardAction());
        assertEquals("message queue discard threshold",         456,                    messageQueue.getDiscardThreshold());
        assertEquals("stats: actual log group name",            "foo",                  stats.getActualLogGroupName());
        assertEquals("stats: actual log stream name",           "bar",                  stats.getActualLogStreamName());
    }


    @Test
    public void testOperationWithExistingGroupAndStream() throws Exception
    {
        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        // will call describeLogGroups when checking group existence
        // will call describeLogStreams when checking stream existence, as well as for each putLogEvents

        assertEquals("describeLogGroups: invocation count",     1,                  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    2,                  mock.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",        0,                  mock.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",       0,                  mock.createLogStreamInvocationCount);
        assertEquals("putLogEvents: invocation count",          1,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      1,                  mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",              "message one",      mock.mostRecentEvents.get(0).getMessage());

        assertEquals("stats: actual log group name",            "argle",            stats.getActualLogGroupName());
        assertEquals("stats: actual log stream name",           "bargle",           stats.getActualLogStreamName());

        assertStatisticsMessagesSent(1);

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message three"));
        mock.allowWriterThread();

        // will call describeLogStreams for each putLogEvents

        assertEquals("describeLogGroups: invocation count",     1,                  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    3,                  mock.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",        0,                  mock.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",       0,                  mock.createLogStreamInvocationCount);
        assertEquals("putLogEvents: invocation count",          2,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      2,                  mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message (0)",          "message two",      mock.mostRecentEvents.get(0).getMessage());
        assertEquals("putLogEvents: last message (1)",          "message three",    mock.mostRecentEvents.get(1).getMessage());

        assertStatisticsMessagesSent(3);

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "using existing .* group: argle",
                                              "using existing .* stream: bargle",
                                              "log writer initialization complete.*");
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testOperationWithExistingGroupAndNewStream() throws Exception
    {
        config.logStreamName = "zippy";

        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        // will call describeLogGroups when checking group existence
        // will call describeLogStreams when checking stream existence, after creating stream, and for each putLogEvents

        assertEquals("describeLogGroups: invocation count",     1,                  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    3,                  mock.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",        0,                  mock.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",       1,                  mock.createLogStreamInvocationCount);
        assertEquals("createLogStream: group name",             "argle",            mock.createLogStreamGroupName);
        assertEquals("createLogStream: stream name",            "zippy",            mock.createLogStreamStreamName);
        assertEquals("putLogEvents: invocation count",          1,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      1,                  mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",              "message one",      mock.mostRecentEvents.get(0).getMessage());

        assertEquals("stats: actual log group name",            "argle",            stats.getActualLogGroupName());
        assertEquals("stats: actual log stream name",           "zippy",            stats.getActualLogStreamName());

        assertStatisticsMessagesSent(1);

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message three"));
        mock.allowWriterThread();

        // will call describeLogStreams for each putLogEvents

        assertEquals("describeLogGroups: invocation count",     1,                  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    4,                  mock.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",        0,                  mock.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",       1,                  mock.createLogStreamInvocationCount);
        assertEquals("putLogEvents: invocation count",          2,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      2,                  mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message (0)",          "message two",      mock.mostRecentEvents.get(0).getMessage());
        assertEquals("putLogEvents: last message (1)",          "message three",    mock.mostRecentEvents.get(1).getMessage());

        assertStatisticsMessagesSent(3);

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "using existing .* group: argle",
                                              "creating .* stream: zippy",
                                              "log writer initialization complete.*");
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testOperationWithNewGroupAndStream() throws Exception
    {
        config.logGroupName = "griffy";
        config.logStreamName = "zippy";

        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        // will call describeLogGroups when checking group existence, as well as after creating group
        // will call describeLogStreams when checking stream existence, after creating stream, and for each putLogEvents

        assertEquals("describeLogGroups: invocation count",     2,                  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    3,                  mock.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",        1,                  mock.createLogGroupInvocationCount);
        assertEquals("createLogGroup: group name",              "griffy",           mock.createLogGroupGroupName);
        assertEquals("createLogStream: invocation count",       1,                  mock.createLogStreamInvocationCount);
        assertEquals("createLogStream: group name",             "griffy",           mock.createLogStreamGroupName);
        assertEquals("createLogStream: stream name",            "zippy",            mock.createLogStreamStreamName);
        assertEquals("putLogEvents: invocation count",          1,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      1,                  mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",              "message one",      mock.mostRecentEvents.get(0).getMessage());

        assertEquals("stats: actual log group name",            "griffy",           stats.getActualLogGroupName());
        assertEquals("stats: actual log stream name",           "zippy",            stats.getActualLogStreamName());

        assertStatisticsMessagesSent(1);

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message three"));
        mock.allowWriterThread();

        // will call describeLogStreams for each putLogEvents

        assertEquals("describeLogGroups: invocation count",     2,                  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    4,                  mock.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",        1,                  mock.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",       1,                  mock.createLogStreamInvocationCount);
        assertEquals("putLogEvents: invocation count",          2,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      2,                  mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message (0)",          "message two",      mock.mostRecentEvents.get(0).getMessage());
        assertEquals("putLogEvents: last message (1)",          "message three",    mock.mostRecentEvents.get(1).getMessage());

        assertStatisticsMessagesSent(3);

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "creating .* group: griffy",
                                              "creating .* stream: zippy",
                                              "log writer initialization complete.*");
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testPaginatedDescribes() throws Exception
    {
        // these two names are at the end of the default list
        config.logGroupName = "argle";
        config.logStreamName = "fribble";

        mock = new MockCloudWatchClient(MockCloudWatchClient.NAMES, 5, MockCloudWatchClient.NAMES, 5);
        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        // this is an "existing group and stream" test, but with twice the describe calls

        assertEquals("describeLogGroups: invocation count",     2,                  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    4,                  mock.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",        0,                  mock.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",       0,                  mock.createLogStreamInvocationCount);
        assertEquals("putLogEvents: invocation count",          1,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      1,                  mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",              "message one",      mock.mostRecentEvents.get(0).getMessage());

        assertEquals("stats: actual log group name",            "argle",            stats.getActualLogGroupName());
        assertEquals("stats: actual log stream name",           "fribble",          stats.getActualLogStreamName());

        assertStatisticsMessagesSent(1);
    }


    @Test
    public void testInvalidGroupName() throws Exception
    {
        config.logGroupName = "I'm No Good!";
        config.logStreamName = "Although, I Am";

        createWriter();

        // we don't need to write any messages; writer should fail to initialize

        assertStatisticsErrorMessage("invalid log group name: " + config.logGroupName + "$");

        assertEquals("describeLogGroups: invocation count",     0,                      mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    0,                      mock.describeLogStreamsInvocationCount);

        internalLogger.assertInternalDebugLog("log writer starting.*");
        internalLogger.assertInternalErrorLog(".*invalid.*group.*");
    }


    @Test
    public void testInvalidStreamName() throws Exception
    {
        config.logGroupName = "IAmOK";
        config.logStreamName = "But: I'm Not";

        createWriter();

        // we don't need to write any messages; writer should fail to initialize

        assertStatisticsErrorMessage("invalid log stream name: " + config.logStreamName + "$");

        assertEquals("describeLogGroups: invocation count",     0,                      mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    0,                      mock.describeLogStreamsInvocationCount);

        internalLogger.assertInternalDebugLog("log writer starting.*");
        internalLogger.assertInternalErrorLog(".*invalid.*stream.*");
    }


    @Test
    public void testInitializationErrorHandling() throws Exception
    {
        mock = new MockCloudWatchClient()
        {
            @Override
            protected DescribeLogGroupsResult describeLogGroups(DescribeLogGroupsRequest request)
            {
                throw new TestingException("not now, not ever");
            }
        };

        createWriter();

        // we don't need to write any messages; writer should fail to initialize

        assertEquals("describeLogGroups: invocation count",             1,                          mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",            0,                          mock.describeLogStreamsInvocationCount);

        assertEquals("message queue set to discard all",                0,                          messageQueue.getDiscardThreshold());
        assertEquals("message queue set to discard all",                DiscardAction.oldest,       messageQueue.getDiscardAction());

        assertStatisticsErrorMessage("unable to configure.*");
        assertStatisticsException(TestingException.class, "not now, not ever");

        internalLogger.assertInternalDebugLog("log writer starting.*");
        internalLogger.assertInternalErrorLog("unable to configure.*");
        internalLogger.assertInternalErrorLogExceptionTypes(TestingException.class);
    }


    @Test
    public void testBatchExceptionHandling() throws Exception
    {
        mock = new MockCloudWatchClient()
        {
            @Override
            protected PutLogEventsResult putLogEvents(PutLogEventsRequest request)
            {
                throw new TestingException("I don't wanna do the work");
            }
        };

        createWriter();

        // we allow two trips to putLogEvents because (1) stats are updated before the main thread
        // restarts, and (2) we want to verify that the batch is re-processed

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();
        mock.allowWriterThread();

        assertEquals("putLogEvents: invocation count",                  2,                      mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",              1,                      mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",                      "message one",          mock.mostRecentEvents.get(0).getMessage());

        assertStatisticsErrorMessage("failed to send batch");
        assertStatisticsException(TestingException.class, "I don't wanna do the work");

        assertTrue("message queue still accepts messages",                                      messageQueue.getDiscardThreshold() > 0);

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "using existing .* group: argle",
                                              "using existing .* stream: bargle",
                                              "log writer initialization complete.*");
        internalLogger.assertInternalErrorLog("failed to send.*", "failed to send.*");
        internalLogger.assertInternalErrorLogExceptionTypes(TestingException.class, TestingException.class);
    }


    @Test
    public void testInvalidSequenceTokenException() throws Exception
    {
        mock = new MockCloudWatchClient()
        {
            @Override
            protected PutLogEventsResult putLogEvents(PutLogEventsRequest request)
            {
                if (putLogEventsInvocationCount < 3)
                    throw new InvalidSequenceTokenException("race condition!");
                else
                    return super.putLogEvents(request);
            }
        };

        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));

        // we need three trips to putLogEvents because the first two will have exceptions
        mock.allowWriterThread();
        mock.allowWriterThread();
        mock.allowWriterThread();

        assertEquals("putLogEvents: invocation count",                  3,                      mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",              1,                      mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",                      "message one",          mock.mostRecentEvents.get(0).getMessage());

        // no message should be recorded in stats or reported to logs

        assertNull("statistics error message not set", stats.getLastErrorMessage());

        assertEquals("stats: writer race retries",                      2,                      stats.getWriterRaceRetries());
        assertEquals("stats: unrecovered writer race retries",          0,                      stats.getUnrecoveredWriterRaceRetries());

        internalLogger.assertInternalDebugLog(
            "log writer starting.*",
            "using existing.*log group.*",
            "using existing.*log stream.*",
            "log writer initialization complete.*");
        internalLogger.assertInternalErrorLog();
        internalLogger.assertInternalErrorLogExceptionTypes();
    }


    @Test
    public void testUnrecoveredInvalidSequenceTokenException() throws Exception
    {
        mock = new MockCloudWatchClient()
        {
            @Override
            protected PutLogEventsResult putLogEvents(PutLogEventsRequest request)
            {
                throw new InvalidSequenceTokenException("I'll never complete!");
            }
        };

        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));

        // I know that there will be 5 retry attempts before giving up, so will wait +1 times
        for (int ii = 0 ; ii < 6 ; ii++)
            mock.allowWriterThread();

        assertEquals("putLogEvents: invocation count",                  6,                      mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",              1,                      mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",                      "message one",          mock.mostRecentEvents.get(0).getMessage());

        assertRegex("statistics: error message",                        ".*repeated InvalidSequenceTokenException.*",
                                                                        stats.getLastErrorMessage());

        assertEquals("stats: writer race retries",                      6,                      stats.getWriterRaceRetries());
        assertEquals("stats: unrecovered writer race retries",          1,                      stats.getUnrecoveredWriterRaceRetries());

        internalLogger.assertInternalDebugLog(
            "log writer starting.*",
            "using existing.*log group.*",
            "using existing.*log stream.*",
            "log writer initialization complete.*");
        internalLogger.assertInternalErrorLog(".*InvalidSequenceTokenException.*");
        internalLogger.assertInternalErrorLogExceptionTypes();
    }


    @Test
    public void testDataAlreadyAcceptedException() throws Exception
    {
        // to ensure that the batch is discarded without looking too deeply into
        // implementation details, we'll send two batches: only the second should
        // succeed

        mock = new MockCloudWatchClient()
        {
            @Override
            protected PutLogEventsResult putLogEvents(PutLogEventsRequest request)
            {
                if (putLogEventsInvocationCount == 1)
                    throw new DataAlreadyAcceptedException("blah blah blah");
                else
                    return super.putLogEvents(request);
            }
        };

        createWriter();

        // this first message should be rejected
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        assertEquals("putLogEvents: invocation count",                  1,                      mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",              1,                      mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",                      "message one",          mock.mostRecentEvents.get(0).getMessage());

        assertStatisticsErrorMessage("received DataAlreadyAcceptedException.*");
        assertStatisticsException(DataAlreadyAcceptedException.class,   "blah blah blah.*");

        assertEquals("stats: no messages written",                      0,                      stats.getMessagesSent());
        assertTrue("message queue still accepts messages",                                      messageQueue.getDiscardThreshold() > 0);

        // this message should be accepted
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));
        mock.allowWriterThread();

        assertEquals("putLogEvents: invocation count",                  2,                      mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",              1,                      mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",                      "message two",          mock.mostRecentEvents.get(0).getMessage());

        assertStatisticsMessagesSent(1);

        internalLogger.assertInternalDebugLog(
            "log writer starting.*",
            "using existing.*log group.*",
            "using existing.*log stream.*",
            "log writer initialization complete.*");
        internalLogger.assertInternalErrorLog("received DataAlreadyAcceptedException.*");
        internalLogger.assertInternalErrorLogExceptionTypes(DataAlreadyAcceptedException.class);
    }


    @Test
    public void testRecoveryFromLogStreamDeletion() throws Exception
    {
        // this starts off with the default (existing) group and stream

        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        // will call describeLogGroups when checking group existence
        // will call describeLogStreams when checking stream existence, as well as for each putLogEvents
        // stream exists, so no creation methods called

        assertEquals("describeLogGroups: invocation count",     1,                  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    2,                  mock.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",        0,                  mock.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",       0,                  mock.createLogStreamInvocationCount);
        assertEquals("putLogEvents: invocation count",          1,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      1,                  mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",              "message one",      mock.mostRecentEvents.get(0).getMessage());

        mock.logStreamNames.clear();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));
        mock.allowWriterThread();

        // we'll do another invoke of describeLogStream to discover that stream no longer exists,
        // followed by an invoke of both describes and then a create, then describeStream to verify creation,
        // followed by another stream describe before the putLogEvents
        // ... but we'll never see the actual failure state

        assertEquals("describeLogGroups: invocation count",     2,                  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    6,                  mock.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",        0,                  mock.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",       1,                  mock.createLogStreamInvocationCount);
        assertEquals("putLogEvents: invocation count",          2,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      1,                  mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",              "message two",      mock.mostRecentEvents.get(0).getMessage());

        assertStatisticsMessagesSent(2);
        assertStatisticsErrorMessage("log stream missing: " + config.logStreamName);

        // will get an error message when stream goes missing, debug when it's recreated

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "using existing .* group: argle",
                                              "using existing .* stream: bargle",
                                              "log writer initialization complete.*",
                                              "using existing .* group: argle",
                                              "creating .* stream: bargle*");
        internalLogger.assertInternalErrorLog(".*stream.*missing.*");

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message three"));
        mock.allowWriterThread();

        // this should be fine: another describeStream, followed by a putLogEvents

        assertEquals("describeLogGroups: invocation count",     2,                  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    7,                  mock.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",        0,                  mock.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",       1,                  mock.createLogStreamInvocationCount);
        assertEquals("putLogEvents: invocation count",          3,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      1,                  mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",              "message three",    mock.mostRecentEvents.get(0).getMessage());
    }


    @Test
    public void testMaximumMessageSize() throws Exception
    {
        final int cloudwatchMaximumBatchSize    = 1048576;  // copied from http://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_PutLogEvents.html
        final int cloudwatchOverhead            = 26;       // ditto

        final int maxMessageSize                = cloudwatchMaximumBatchSize - cloudwatchOverhead;
        final String bigMessage                 = StringUtil.repeat('A', maxMessageSize);
        final String biggerMessage              = bigMessage + "1";

        createWriter();

        try
        {
            writer.addMessage(new LogMessage(System.currentTimeMillis(), biggerMessage));
            fail("writer allowed too-large message");
        }
        catch (IllegalArgumentException ex)
        {
            assertEquals("exception message", "attempted to enqueue a too-large message", ex.getMessage());
        }
        catch (Exception ex)
        {
            fail("writer threw " + ex.getClass().getName() + ", not IllegalArgumentException");
        }

        // we'll send an OK message through to verify that nothing bad happened
        writer.addMessage(new LogMessage(System.currentTimeMillis(), bigMessage));

        mock.allowWriterThread();

        assertEquals("putLogEvents: invocation count",          1,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      1,                  mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",              bigMessage,         mock.mostRecentEvents.get(0).getMessage());
    }


    @Test
    public void testDiscardOldest() throws Exception
    {
        config.discardAction = DiscardAction.oldest;
        config.discardThreshold = 10;

        // this test doesn't need a background thread running

        writer = new CloudWatchLogWriter(config, stats, internalLogger, dummyClientFactory);
        messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        for (int ii = 0 ; ii < 20 ; ii++)
        {
            writer.addMessage(new LogMessage(System.currentTimeMillis(), "message " + ii));
        }

        List<LogMessage> messages = messageQueue.toList();

        assertEquals("number of messages in queue",     10,             messages.size());
        assertEquals("oldest message in queue",         "message 10",   messages.get(0).getMessage());
        assertEquals("newest message in queue",         "message 19",   messages.get(9).getMessage());
    }


    @Test
    public void testDiscardNewest() throws Exception
    {
        config.discardAction = DiscardAction.newest;
        config.discardThreshold = 10;

        // this test doesn't need a background thread running

        writer = new CloudWatchLogWriter(config, stats, internalLogger, dummyClientFactory);
        messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        for (int ii = 0 ; ii < 20 ; ii++)
        {
            writer.addMessage(new LogMessage(System.currentTimeMillis(), "message " + ii));
        }

        List<LogMessage> messages = messageQueue.toList();

        assertEquals("number of messages in queue",     10,             messages.size());
        assertEquals("oldest message in queue",         "message 0",    messages.get(0).getMessage());
        assertEquals("newest message in queue",         "message 9",    messages.get(9).getMessage());
    }


    @Test
    public void testDiscardNone() throws Exception
    {
        config.discardAction = DiscardAction.none;
        config.discardThreshold = 10;

        // this test doesn't need a background thread running

        writer = new CloudWatchLogWriter(config, stats, internalLogger, dummyClientFactory);
        messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        for (int ii = 0 ; ii < 20 ; ii++)
        {
            writer.addMessage(new LogMessage(System.currentTimeMillis(), "message " + ii));
        }

        List<LogMessage> messages = messageQueue.toList();

        assertEquals("number of messages in queue",     20,             messages.size());
        assertEquals("oldest message in queue",         "message 0",    messages.get(0).getMessage());
        assertEquals("newest message in queue",         "message 19",   messages.get(19).getMessage());
    }


    @Test
    public void testReconfigureDiscardProperties() throws Exception
    {
        config.discardAction = DiscardAction.none;
        config.discardThreshold = 123;

        // this test doesn't need a background thread running

        writer = new CloudWatchLogWriter(config, stats, internalLogger, dummyClientFactory);
        messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        assertEquals("initial discard threshold",   123,                    messageQueue.getDiscardThreshold());
        assertEquals("initial discard action",      DiscardAction.none,     messageQueue.getDiscardAction());

        writer.setDiscardAction(DiscardAction.newest);
        writer.setDiscardThreshold(456);

        assertEquals("updated discard threshold",   456,                    messageQueue.getDiscardThreshold());
        assertEquals("updated discard action",      DiscardAction.newest,   messageQueue.getDiscardAction());
    }


    @Test
    public void testCountBasedBatching() throws Exception
    {
        // don't let discard threshold get in the way of the test
        config.discardThreshold = Integer.MAX_VALUE;
        config.discardAction = DiscardAction.none;

        // increasing delay because it will take time to create the messages
        config.batchDelay = 300;

        final String testMessage = "test";    // this won't trigger batching based on size
        final int numMessages = 15000;

        createWriter();
        for (int ii = 0 ; ii < numMessages ; ii++)
        {
            writer.addMessage(new LogMessage(System.currentTimeMillis(), testMessage));
        }

        // first batch should stop at 10,000

        mock.allowWriterThread();

        // will call describeLogGroups when checking group existence
        // will call describeLogStreams when checking stream existence, as well as for each putLogEvents

        assertEquals("describeLogGroups: invocation count",     1,                  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    2,                  mock.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",        0,                  mock.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",       0,                  mock.createLogStreamInvocationCount);
        assertEquals("putLogEvents: invocation count",          1,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      10000,              mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",              testMessage,        mock.mostRecentEvents.get(9999).getMessage());

        // second batch should get remaining 5,000

        mock.allowWriterThread();

        // one more call to describeLogStreams, to get sequence token

        assertEquals("describeLogGroups: invocation count",     1,                  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    3,                  mock.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",        0,                  mock.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",       0,                  mock.createLogStreamInvocationCount);
        assertEquals("putLogEvents: invocation count",          2,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      5000,               mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",              testMessage,        mock.mostRecentEvents.get(4999).getMessage());

        assertStatisticsMessagesSent(numMessages);

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "using existing .* group: argle",
                                              "using existing .* stream: bargle",
                                              "log writer initialization complete.*");
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testSizeBasedBatching() throws Exception
    {
        // don't let discard threshold get in the way of the test
        config.discardThreshold = Integer.MAX_VALUE;
        config.discardAction = DiscardAction.none;

        // increasing delay because it will take time to create the messages
        config.batchDelay = 300;

        final String testMessage = StringUtil.randomAlphaString(1024, 1024);
        final int numMessages = 1500;

        createWriter();
        for (int ii = 0 ; ii < numMessages ; ii++)
        {
            writer.addMessage(new LogMessage(System.currentTimeMillis(), testMessage));
        }

        // first batch should stop just under 1 megabyte -- including record overhead

        mock.allowWriterThread();

        // will call describeLogGroups when checking group existence
        // will call describeLogStreams when checking stream existence, as well as for each putLogEvents

        assertEquals("describeLogGroups: invocation count",     1,                  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    2,                  mock.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",        0,                  mock.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",       0,                  mock.createLogStreamInvocationCount);
        assertEquals("putLogEvents: invocation count",          1,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      998,               mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",              testMessage,        mock.mostRecentEvents.get(0).getMessage());

        // second batch should get remaining records

        mock.allowWriterThread();

        // one more call to describeLogStreams, to get sequence token

        assertEquals("describeLogGroups: invocation count",     1,                  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    3,                  mock.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",        0,                  mock.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",       0,                  mock.createLogStreamInvocationCount);
        assertEquals("putLogEvents: invocation count",          2,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      502,              mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",              testMessage,        mock.mostRecentEvents.get(0).getMessage());

        assertStatisticsMessagesSent(numMessages);

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "using existing .* group: argle",
                                              "using existing .* stream: bargle",
                                              "log writer initialization complete.*");
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testStaticClientFactory() throws Exception
    {
        config.clientFactoryMethod = this.getClass().getName() + ".createMockClient";
        config.logGroupName = "argle";
        config.logStreamName = "bargle";

        createWriter(new CloudWatchWriterFactory());

        assertTrue("writer successfully initialized",                                           writer.isInitializationComplete());
        assertNotNull("factory called (local flag)",                                            staticFactoryMock);

        assertEquals("describeLogGroups: invocation count",     1,                              staticFactoryMock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    1,                              staticFactoryMock.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",        0,                              staticFactoryMock.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",       0,                              staticFactoryMock.createLogStreamInvocationCount);

        assertNull("stats: no initialization error",                                            stats.getLastError());
        assertNull("stats: no initialization message",                                          stats.getLastErrorMessage());
        assertEquals("stats: actual log group name",            "argle",                        stats.getActualLogGroupName());
        assertEquals("stats: actual log stream name",           "bargle",                       stats.getActualLogStreamName());

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              ".*created client from factory.*" + getClass().getName() + ".*",
                                              "using existing .* group: argle",
                                              "using existing .* stream: bargle",
                                              "log writer initialization complete.*");
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testShutdown() throws Exception
    {
        // this test is the only place that we expicitly test shutdown logic, to avoid cluttering
        // the "operation" tests; it's otherwise identical to the "existing group/stream" test

        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));

        // the immediate stop should interrupt waitForMessage, but there's no guarantee
        writer.stop();

        // the batch should still be processed
        mock.allowWriterThread();

        assertEquals("putLogEvents: invocation count",          1,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      1,                  mock.mostRecentEvents.size());

        joinWriterThread();

        assertEquals("shutdown: invocation count",              1,                  mock.shutdownInvocationCount);

        internalLogger.assertInternalDebugLog(
            "log writer starting.*",
            "using existing.*log group.*",
            "using existing.*log stream.*",
            "log writer initialization complete.*",
            "stopping log.writer.*");
        internalLogger.assertInternalErrorLog();
    }
}
