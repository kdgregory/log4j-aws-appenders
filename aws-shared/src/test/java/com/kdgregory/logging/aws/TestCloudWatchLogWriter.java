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
import static net.sf.kdgcommons.test.NumericAsserts.*;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterFactory;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.factories.DefaultThreadFactory;
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
            "argle",                // actualLogGroup
            "bargle",               // actualLogStream
            null,                   // retentionPeriod
            false,                  // dedicatedWriter
            100,                    // batchDelay -- short enough to keep tests fast, long enough that we can write a lot of messages
            10000,                  // discardThreshold
            DiscardAction.oldest,   // discardAction
            null,                   // clientFactoryMethod
            null,                   // assumedRole
            null,                   // clientRegion
            null);                  // clientEndpoint

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
        // note: the client endpoint configuration is ignored when creating a writer
        config = new CloudWatchWriterConfig(
                "foo",                              // actualLogGroup
                "bar",                              // actualLogStream
                1,                                  // retentionPeriod
                true,                               // dedicatedWriter
                123,                                // batchDelay
                456,                                // discardThreshold
                DiscardAction.newest,               // discardAction
                "com.example.factory.Method",       // clientFactoryMethod
                "SomeRole",                         // assumedRole
                "us-west-1",                        // clientRegion
                "logs.us-west-1.amazonaws.com");    // clientEndpoint

        assertEquals("log group name",                          "foo",                          config.logGroupName);
        assertEquals("log stream name",                         "bar",                          config.logStreamName);
        assertEquals("retention period",                        Integer.valueOf(1),             config.retentionPeriod);
        assertEquals("dedicated stream",                        true,                           config.dedicatedWriter);
        assertEquals("factory method",                          "com.example.factory.Method",   config.clientFactoryMethod);
        assertEquals("assumed role",                            "SomeRole",                     config.assumedRole);
        assertEquals("client region",                           "us-west-1",                    config.clientRegion);
        assertEquals("client endpoint",                         "logs.us-west-1.amazonaws.com", config.clientEndpoint);

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

        assertEquals("stats: actual log group name",            "argle",            stats.getActualLogGroupName());
        assertEquals("stats: actual log stream name",           "bargle",           stats.getActualLogStreamName());

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

        assertStatisticsTotalMessagesSent(1);
        assertEquals("statistics: last batch messages sent",    1,                  stats.getMessagesSentLastBatch());

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

        assertStatisticsTotalMessagesSent(3);
        assertEquals("statistics: last batch messages sent",    2,                  stats.getMessagesSentLastBatch());

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

        assertEquals("stats: actual log group name",            "argle",            stats.getActualLogGroupName());
        assertEquals("stats: actual log stream name",           "zippy",            stats.getActualLogStreamName());

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

        assertStatisticsTotalMessagesSent(1);
        assertEquals("statistics: last batch messages sent",    1,                  stats.getMessagesSentLastBatch());

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

        assertStatisticsTotalMessagesSent(3);
        assertEquals("statistics: last batch messages sent",    2,                  stats.getMessagesSentLastBatch());

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

        assertEquals("stats: actual log group name",            "griffy",           stats.getActualLogGroupName());
        assertEquals("stats: actual log stream name",           "zippy",            stats.getActualLogStreamName());

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

        assertStatisticsTotalMessagesSent(1);
        assertEquals("statistics: last batch messages sent",    1,                  stats.getMessagesSentLastBatch());

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

        assertStatisticsTotalMessagesSent(3);
        assertEquals("statistics: last batch messages sent",    2,                  stats.getMessagesSentLastBatch());

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "creating .* group: griffy",
                                              "creating .* stream: zippy",
                                              "log writer initialization complete.*");
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testRetentionPolicy() throws Exception
    {
        config.logGroupName = "griffy";
        config.logStreamName = "zippy";
        config.retentionPeriod = 3;

        createWriter();

        assertEquals("stats: actual log group name",            "griffy",           stats.getActualLogGroupName());
        assertEquals("stats: actual log stream name",           "zippy",            stats.getActualLogStreamName());

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        // I'm going to assume that the describes and creates work as expected

        assertEquals("putRetentionPolicy: invocation count",    1,                  mock.putRetentionPolicyInvocationCount);
        assertEquals("putRetentionPolicy: group name",          "griffy",           mock.putRetentionPolicyGroupName);
        assertEquals("putRetentionPolicy: value",               3,                  mock.putRetentionPolicyValue.intValue());

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "creating .* group: griffy",
                                              "setting retention policy on griffy to 3 days",
                                              "creating .* stream: zippy",
                                              "log writer initialization complete.*");
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testRetentionPolicyFailure() throws Exception
    {
        config.logGroupName = "griffy";
        config.logStreamName = "zippy";
        config.retentionPeriod = 3;

        mock = new MockCloudWatchClient()
        {
            @Override
            protected PutRetentionPolicyResult putRetentionPolicy(PutRetentionPolicyRequest request)
            {
                throw new RuntimeException("access denied");
            }
        };

        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        // we should fail to set the policy, but still create the stream and write the message

        assertEquals("createLogGroup: invocation count",        1,                  mock.createLogGroupInvocationCount);
        assertEquals("putRetentionPolicy: invocation count",    1,                  mock.putRetentionPolicyInvocationCount);
        assertEquals("createLogStream: invocation count",       1,                  mock.createLogStreamInvocationCount);
        assertEquals("putLogEvents: invocation count",          1,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      1,                  mock.mostRecentEvents.size());

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "creating .* group: griffy",
                                              "setting retention policy on griffy to 3 days",
                                              "creating .* stream: zippy",
                                              "log writer initialization complete.*");
        internalLogger.assertInternalErrorLog("failed to set retention policy.*griffy");
    }


    @Test
    public void testDedicatedWriter() throws Exception
    {
        config.dedicatedWriter = true;
        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        // will call describeLogGroups when checking group existence
        // will call describeLogStreams when checking stream existence, as well as for the first putLogEvents

        assertEquals("describeLogGroups: invocation count",     1,                  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    2,                  mock.describeLogStreamsInvocationCount);
        assertEquals("putLogEvents: invocation count",          1,                  mock.putLogEventsInvocationCount);

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message three"));
        mock.allowWriterThread();

        // these messages shouldn't require describe

        assertEquals("describeLogGroups: invocation count",     1,                  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    2,                  mock.describeLogStreamsInvocationCount);
        assertEquals("putLogEvents: invocation count",          2,                  mock.putLogEventsInvocationCount);

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "using existing .* group: argle",
                                              "using existing .* stream: bargle",
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

        assertEquals("stats: actual log group name",            "argle",            stats.getActualLogGroupName());
        assertEquals("stats: actual log stream name",           "fribble",          stats.getActualLogStreamName());

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

        assertStatisticsTotalMessagesSent(1);
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

        // these statistics may be from the current batch or previous batch, depending on thread race, but should be same regardless
        assertEquals("statistics: last batch messages sent",            0,                      stats.getMessagesSentLastBatch());
        assertEquals("statistics: last batch messages requeued",        1,                      stats.getMessagesRequeuedLastBatch());

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
        // and since the retry happens in the internal append, there won't be a requeue stat

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
    public void testInvalidSequenceTokenExceptionWithDedicatedWriter() throws Exception
    {
        config.dedicatedWriter = true;
        createWriter();

        // this call will set the sequence token in the writer
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        assertEquals("describeLogStreams: invocation count",            2,                      mock.describeLogStreamsInvocationCount);
        assertEquals("putLogEvents: invocation count",                  1,                      mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last message",                      "message one",          mock.mostRecentEvents.get(0).getMessage());

        // this call should use the cached sequence token
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));
        mock.allowWriterThread();

        assertEquals("describeLogStreams: invocation count",            2,                      mock.describeLogStreamsInvocationCount);
        assertEquals("putLogEvents: invocation count",                  2,                      mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last message",                      "message two",          mock.mostRecentEvents.get(0).getMessage());

        // this will make the cached sequence token invalid
        mock.putLogEventsSequenceToken += 7;

        // which will require two calls to PutLogEvents
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message three"));
        mock.allowWriterThread();
        mock.allowWriterThread();

        assertEquals("describeLogStreams: invocation count",            3,                      mock.describeLogStreamsInvocationCount);
        assertEquals("putLogEvents: invocation count",                  4,                      mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last message",                      "message three",        mock.mostRecentEvents.get(0).getMessage());

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "using existing .* group: argle",
                                              "using existing .* stream: bargle",
                                              "log writer initialization complete.*");
        internalLogger.assertInternalErrorLog();
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

        // based on the current timeout settings I know that there will be 5 retry attempts
        // ... although running on a slow machine there may be only 4, which can cause test
        //     to fail (not fixing)

        long start = System.currentTimeMillis();
        for (int ii = 0 ; ii < 6 ; ii++)
            mock.allowWriterThread();
        long finish = System.currentTimeMillis();

        assertTrue("waited for at least three seconds",                 (finish - start) >= 3000L);

        assertEquals("putLogEvents: invocation count",                  6,                      mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",              1,                      mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",                      "message one",          mock.mostRecentEvents.get(0).getMessage());

        assertRegex("statistics: error message",                        ".*repeated InvalidSequenceTokenExceptions.*",
                                                                        stats.getLastErrorMessage());

        // when running on a single-core CPU, this will occasionallly not report the most recent retry
        assertEquals("stats: writer race retries",                      6,                      stats.getWriterRaceRetries());
        assertEquals("stats: unrecovered writer race retries",          1,                      stats.getUnrecoveredWriterRaceRetries());

        assertEquals("statistics: last batch messages sent",            0,                      stats.getMessagesSentLastBatch());
        assertEquals("statistics: last batch messages requeued",        1,                      stats.getMessagesRequeuedLastBatch());

        internalLogger.assertInternalDebugLog(
            "log writer starting.*",
            "using existing.*log group.*",
            "using existing.*log stream.*",
            "log writer initialization complete.*");
        internalLogger.assertInternalErrorLog(".*InvalidSequenceTokenException.*");
        internalLogger.assertInternalErrorLogExceptionTypes(new Class<?>[] { null });   // we record the message, not the exception
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

        // this is a bug in counting, but one that I'm willing to overlook because this exception should be very rare
        // to fix it would require clearing the batch list in attemptToSend() or moving the messages-sent calculation
        // back into that method; either one seems uglier than an occasional miscount
        assertEquals("stats: incorrectly reports messages written",     1,                      stats.getMessagesSent());

        // note that we claim to have sent the message in the batch, even  though we didn't, because we didn't requeue
        assertEquals("statistics: last batch messages sent",            1,                      stats.getMessagesSentLastBatch());
        assertEquals("statistics: last batch messages requeued",        0,                      stats.getMessagesRequeuedLastBatch());

        assertTrue("message queue still accepts messages",                                      messageQueue.getDiscardThreshold() > 0);

        // this message should be accepted
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));
        mock.allowWriterThread();

        assertEquals("putLogEvents: invocation count",                  2,                      mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",              1,                      mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",                      "message two",          mock.mostRecentEvents.get(0).getMessage());

        // again, this is off by 1
        assertStatisticsTotalMessagesSent(2);

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

        assertStatisticsTotalMessagesSent(1);
        assertEquals("statistics: last batch messages sent",        1,              stats.getMessagesSentLastBatch());
        assertEquals("statistics: last batch messages requeued",    0,              stats.getMessagesRequeuedLastBatch());

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

        assertStatisticsTotalMessagesSent(2);
        assertEquals("statistics: last batch messages sent",        1,              stats.getMessagesSentLastBatch());
        assertEquals("statistics: last batch messages requeued",    0,              stats.getMessagesRequeuedLastBatch());

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
        final int cloudwatchMaximumEventSize    = 256 * 1024;   // copied from https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/cloudwatch_limits_cwl.html
        final int cloudwatchOverhead            = 26;           // copied from https://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_PutLogEvents.html
        final int cloudwatchMaximumMessageSize  = cloudwatchMaximumEventSize - cloudwatchOverhead;

        // note that we have to account for UTF-8 conversion
        final String bigMessage                 = StringUtil.repeat('\u00b7', cloudwatchMaximumMessageSize / 2);
        final String biggerMessage              = bigMessage + "X";

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

        assertStatisticsTotalMessagesSent(numMessages);

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

        assertStatisticsTotalMessagesSent(numMessages);

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
                                              "creating client via factory.*" + config.clientFactoryMethod,
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

        // it actually tests functionality in AbstractAppender, but I've replicated for all concrete
        // subclasses simply because it's a key piece of functionality

        createWriter();

        assertEquals("after creation, shutdown time should be infinite", Long.MAX_VALUE, getShutdownTime());

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));

        // the immediate stop should interrupt waitForMessage, but there's no guarantee
        writer.stop();

        long now = System.currentTimeMillis();
        long shutdownTime = getShutdownTime();
        assertInRange("after stop(), shutdown time should be based on batch delay", now, now + config.batchDelay + 100, shutdownTime);

        // the batch should still be processed
        mock.allowWriterThread();

        assertEquals("putLogEvents: invocation count",          1,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      1,                  mock.mostRecentEvents.size());

        // another call to stop should be ignored -- sleep to ensure times would be different
        Thread.sleep(100);
        writer.stop();
        assertEquals("second call to stop() should be no-op", shutdownTime, getShutdownTime());

        joinWriterThread();

        assertEquals("shutdown: invocation count",              1,                  mock.shutdownInvocationCount);

        internalLogger.assertInternalDebugLog(
            "log writer starting.*",
            "using existing.*log group.*",
            "using existing.*log stream.*",
            "log writer initialization complete.*",
            "log.writer shut down.*");
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testSynchronousOperation() throws Exception
    {
        // appender is expected to set batch delay in synchronous mode
        config.batchDelay = 1;

        // we just have one thread, so don't want any locks getting in the way
        mock.disableThreadSynchronization();

        // the createWriter() method spins up a background thread, which we don't want
        writer = (CloudWatchLogWriter)mock.newWriterFactory().newLogWriter(config, stats, internalLogger);
        messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        assertEquals("stats: actual log group name",            "argle",            stats.getActualLogGroupName());
        assertEquals("stats: actual log stream name",           "bargle",           stats.getActualLogStreamName());
        assertFalse("writer should not be initialized",         ClassUtil.getFieldValue(writer, "initializationComplete", Boolean.class).booleanValue());

        writer.initialize();

        assertTrue("writer has been initialized",               ClassUtil.getFieldValue(writer, "initializationComplete", Boolean.class).booleanValue());
        assertNull("no dispatch thread",                        ClassUtil.getFieldValue(writer, "dispatchThread", Thread.class));

        assertEquals("describeLogGroups: invocation count",     1,                  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    1,                  mock.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",        0,                  mock.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",       0,                  mock.createLogStreamInvocationCount);

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));

        assertEquals("message is waiting in queue",             1,                  messageQueue.queueSize());
        assertEquals("putLogEvents: invocation count",          0,                  mock.putLogEventsInvocationCount);

        writer.processBatch(System.currentTimeMillis());

        assertEquals("no longer in queue",                      0,                  messageQueue.queueSize());
        assertEquals("describeLogGroups: invocation count",     1,                  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    2,                  mock.describeLogStreamsInvocationCount);
        assertEquals("putLogEvents: invocation count",          1,                  mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",      1,                  mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",              "message one",      mock.mostRecentEvents.get(0).getMessage());

        assertStatisticsTotalMessagesSent(1);

        assertEquals("shutdown not called before cleanup",      0,                  mock.shutdownInvocationCount);
        writer.cleanup();
        assertEquals("shutdown called after cleanup",           1,                  mock.shutdownInvocationCount);

        // the "starting" and "initialization complete" messages are emitted in run(), so not present here
        internalLogger.assertInternalDebugLog("using existing .* group: argle",
                                              "using existing .* stream: bargle");
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testShutdownHook() throws Exception
    {
        // this is the only test for shutdown hooks: it's part of the abstract writer functionality
        // so doesn't need to be replicated (TODO - we need TestAbstractLogWriter)

        // we can't actually test a shutdown hook, so we have to assume that it's called as expected
        // nor can we test the behavior of removing a shutdown hook during shutdown

        // since createWriter() uses a thread factory that doesn't install a shutdown hook, we need
        // to explicitly create the writer

        writer = (CloudWatchLogWriter)mock.newWriterFactory().newLogWriter(config, stats, internalLogger);

        new DefaultThreadFactory("test").startLoggingThread(writer, true, defaultUncaughtExceptionHandler);
        assertTrue("writer running", writer.waitUntilInitialized(5000));

        Thread writerThread = ClassUtil.getFieldValue(writer, "dispatchThread", Thread.class);
        assertTrue("writer thread active", writerThread.isAlive());

        Thread shutdownHook = ClassUtil.getFieldValue(writer, "shutdownHook", Thread.class);
        assertNotNull("shutdown hook exists", shutdownHook);

        shutdownHook.start();
        shutdownHook.join();

        assertFalse("writer thread no longer active", writerThread.isAlive());
        assertNull("shutdown hook has been cleared on writer", ClassUtil.getFieldValue(writer, "shutdownHook", Thread.class));
        assertFalse("shutdown hook has been deregistered in JVM", Runtime.getRuntime().removeShutdownHook(shutdownHook));
    }
}
