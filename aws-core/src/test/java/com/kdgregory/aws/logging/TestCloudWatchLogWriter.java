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

package com.kdgregory.aws.logging;


import java.lang.Thread.UncaughtExceptionHandler;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.lang.StringUtil;
import net.sf.kdgcommons.test.StringAsserts;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.DescribeLogGroupsRequest;
import com.amazonaws.services.logs.model.DescribeLogGroupsResult;
import com.amazonaws.services.logs.model.PutLogEventsRequest;
import com.amazonaws.services.logs.model.PutLogEventsResult;

import com.kdgregory.aws.logging.cloudwatch.CloudWatchAppenderStatistics;
import com.kdgregory.aws.logging.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.aws.logging.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.aws.logging.common.DefaultThreadFactory;
import com.kdgregory.aws.logging.common.DiscardAction;
import com.kdgregory.aws.logging.common.LogMessage;
import com.kdgregory.aws.logging.common.MessageQueue;
import com.kdgregory.aws.logging.testhelpers.TestingException;
import com.kdgregory.aws.logging.testhelpers.cloudwatch.MockCloudWatchClient;

/**
 *  Performs mock-client testing of the CloudWatch writer.
 *
 *  TODO: add tests for size-based batching and endpoint configuration.
 */
public class TestCloudWatchLogWriter
{
//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

    /**
     *  Default writer config, with group/stream that are considered "existing"
     *  by the mock client. This is created anew for each test, so may be
     *  modified as desired.
     */
    private CloudWatchWriterConfig config = new CloudWatchWriterConfig(
            "argle",                // log group name
            "bargle",               // log stream name
            100,                    // batch delay -- short enough to keep tests fast, long enough that we can write a lot of messages
            10000,                  // discard threshold
            DiscardAction.oldest,   // discard action
            null,                   // factory method
            null);                  // endpoint


    /**
     *  An appender statistics object, so that we don't have to create for
     *  each test.
     */
    private CloudWatchAppenderStatistics stats = new CloudWatchAppenderStatistics();


    /**
     *  The default mock object; can be overridden if necessary.
     */
    private MockCloudWatchClient mock = new MockCloudWatchClient();


    /**
     *  This will be assigned by createWriter();
     */
    private CloudWatchLogWriter writer;


    /**
     *  This will be set by the test thread's uncaught exception handler.
     *  TODO - add an @After check for any uncaught exceptions
     */
    @SuppressWarnings("unused")
    private Throwable uncaughtException;


    /**
     *  Whenever we need to spin up a logging thread, use this handler.
     */
    private UncaughtExceptionHandler defaultUncaughtExceptionHandler
        = new UncaughtExceptionHandler()
        {
            @Override
            public void uncaughtException(Thread t, Throwable e)
            {
                uncaughtException = e;
            }
        };


    /**
     *  Constructs and initializes a writer on a background thread, waiting for
     *  initialization to either complete or fail. Returns the writer.
     */
    private CloudWatchLogWriter createWriter()
    throws Exception
    {
        writer = (CloudWatchLogWriter)mock.newWriterFactory().newLogWriter(config, stats);
        new DefaultThreadFactory().startLoggingThread(writer, defaultUncaughtExceptionHandler);

        // we'll spin until either the writer is initialized, signals an error,
        // or a 5-second timeout expires
        for (int ii = 0 ; ii < 100 ; ii++)
        {
            if (writer.isInitializationComplete())
                return writer;
            if (! StringUtil.isEmpty(stats.getLastErrorMessage()))
                return writer;
            Thread.sleep(50);
        }

        fail("unable to initialize writer");

        // compiler doesn't know this will never happen
        return writer;
    }


    // the following variable and function are used by testStaticClientFactory

    private static MockCloudWatchClient staticFactoryMock = null;

    public static AWSLogs createMockClient()
    {
        staticFactoryMock = new MockCloudWatchClient();
        return staticFactoryMock.createClient();
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testConfiguration() throws Exception
    {
        config = new CloudWatchWriterConfig("foo", "bar", 123, 456, DiscardAction.newest, "com.example.factory.Method", "us-west-1");

        writer = new CloudWatchLogWriter(config, stats);
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        assertEquals("writer batch delay",              123L,                   writer.getBatchDelay());
        assertEquals("message queue discard policy",    DiscardAction.newest,   messageQueue.getDiscardAction());
        assertEquals("message queue discard threshold", 456,                    messageQueue.getDiscardThreshold());
        assertEquals("stats actual log group name",     "foo",                  stats.getActualLogGroupName());
        assertEquals("stats actual log stream name",    "bar",                  stats.getActualLogStreamName());
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

        assertEquals("messages sent, from statistics",          1,                  stats.getMessagesSent());
        assertEquals("actual log group name, from statistics",  "argle",            stats.getActualLogGroupName());
        assertEquals("actual log stream name, from statistics", "bargle",           stats.getActualLogStreamName());

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

        assertEquals("messages sent, from statistics",          3,                  stats.getMessagesSent());
    }


    @Test
    public void testOperationWithExistingGroupAndNewStream() throws Exception
    {
        config.logStream = "zippy";

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

        assertEquals("messages sent, from statistics",          1,                  stats.getMessagesSent());
        assertEquals("actual log group name, from statistics",  "argle",            stats.getActualLogGroupName());
        assertEquals("actual log stream name, from statistics", "zippy",            stats.getActualLogStreamName());

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

        assertEquals("messages sent, from statistics",          3,                  stats.getMessagesSent());
    }


    @Test
    public void testOperationWithNewGroupAndStream() throws Exception
    {
        config.logGroup = "griffy";
        config.logStream = "zippy";

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

        assertEquals("messages sent, from statistics",          1,                  stats.getMessagesSent());
        assertEquals("actual log group name, from statistics",  "griffy",            stats.getActualLogGroupName());
        assertEquals("actual log stream name, from statistics", "zippy",            stats.getActualLogStreamName());

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

        assertEquals("messages sent, from statistics",          3,                  stats.getMessagesSent());
    }


    @Test
    public void testPaginatedDescribes() throws Exception
    {
        // these two names are at the end of the default list
        config.logGroup = "argle";
        config.logStream = "fribble";

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

        assertEquals("messages sent, from statistics",          1,                  stats.getMessagesSent());
        assertEquals("actual log group name, from statistics",  "argle",            stats.getActualLogGroupName());
        assertEquals("actual log stream name, from statistics", "fribble",          stats.getActualLogStreamName());
    }


    @Test
    public void testInvalidGroupName() throws Exception
    {
        config.logGroup = "I'm No Good!";
        config.logStream = "Although, I Am";

        createWriter();

        // we don't need to write any messages; writer should fail to initialize

        String errorMessage = stats.getLastErrorMessage();

        StringAsserts.assertRegex("error message indicates failure, with group name (was: " + errorMessage + ")",
                                  ".*log group.*" + config.logGroup + "$",
                                  errorMessage);

        assertEquals("describeLogGroups: invocation count",    0,  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",   0,  mock.describeLogStreamsInvocationCount);
    }


    @Test
    public void testInvalidStreamName() throws Exception
    {
        config.logGroup = "IAmOK";
        config.logStream = "But: I'm Not";

        createWriter();

        // we don't need to write any messages; writer should fail to initialize

        String errorMessage = stats.getLastErrorMessage();

        StringAsserts.assertRegex("error message indicates failure, with stream name (was: " + errorMessage + ")",
                                  ".*log stream.*" + config.logStream + "$",
                                  errorMessage);

        assertEquals("describeLogGroups: invocation count",    0,  mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",   0,  mock.describeLogStreamsInvocationCount);
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
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        // we don't need to write any messages; writer should fail to initialize

        String errorMessage = stats.getLastErrorMessage();

        assertEquals("describeLogGroups: invocation count",             1,                          mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",            0,                          mock.describeLogStreamsInvocationCount);

        assertTrue("stats: error message (was: " + errorMessage + ")",                              stats.getLastErrorMessage().contains("unable to configure"));
        assertEquals("stats: exception class",                          TestingException.class,     stats.getLastError().getClass());
        assertEquals("stats: exception message",                        "not now, not ever",        stats.getLastError().getMessage());
        assertTrue("stats: exception trace",                                                        stats.getLastErrorStacktrace().size() > 0);
        assertNotNull("stats: exception timestamp",                                                 stats.getLastErrorTimestamp());

        assertEquals("message queue set to discard all",                0,                          messageQueue.getDiscardThreshold());
        assertEquals("message queue set to discard all",                DiscardAction.oldest,       messageQueue.getDiscardAction());
    }



    @Test
    public void testBatchExceptionHandling() throws Exception
    {
        mock = new MockCloudWatchClient()
        {
            @Override
            protected PutLogEventsResult putLogEvents(PutLogEventsRequest request)
            {
                throw new TestingException("can't send it");
            }
        };

        createWriter();
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        // we allow two trips to putLogEvents because (1) stats are updated before the main thread
        // restarts, and (2) we want to verify that the batch is re-processed

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();
        mock.allowWriterThread();

        assertEquals("putLogEvents: invocation count",                  2,                      mock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",              1,                      mock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",                      "message one",          mock.mostRecentEvents.get(0).getMessage());

        assertEquals("stats: messages sent",                            0,                      stats.getMessagesSent());
        assertFalse("stats: error message non-blank",                                           StringUtil.isBlank(stats.getLastErrorMessage()));
        assertEquals("stats: exception class",                          TestingException.class, stats.getLastError().getClass());
        assertEquals("stats: exception message",                        "can't send it",        stats.getLastError().getMessage());
        assertTrue("stats: exception trace",                                                    stats.getLastErrorStacktrace().size() > 0);
        assertNotNull("stats: exception timestamp",                                             stats.getLastErrorTimestamp());

        assertTrue("message queue still accepts messages",                                      messageQueue.getDiscardThreshold() > 0);
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

        assertEquals("stats: messages sent",                    2,                  stats.getMessagesSent());
        assertFalse("stats: error message non-blank",                               StringUtil.isBlank(stats.getLastErrorMessage()));
        assertNull("stats: no exception",                                           stats.getLastError());
        assertNull("stats: no exception trace",                                     stats.getLastErrorStacktrace());
        assertNotNull("stats: exception timestamp",                                 stats.getLastErrorTimestamp());

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
    public void testDiscardOldest() throws Exception
    {
        config.discardAction = DiscardAction.oldest;
        config.discardThreshold = 10;

        // this test doesn't need a background thread running

        writer = new CloudWatchLogWriter(config, stats);
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

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

        writer = new CloudWatchLogWriter(config, stats);
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

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

        writer = new CloudWatchLogWriter(config, stats);
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

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

        writer = new CloudWatchLogWriter(config, stats);
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        assertEquals("initial discard threshold",   123,                    messageQueue.getDiscardThreshold());
        assertEquals("initial discard action",      DiscardAction.none,     messageQueue.getDiscardAction());

        writer.setDiscardAction(DiscardAction.newest);
        writer.setDiscardThreshold(456);

        assertEquals("updated discard threshold",   456,                    messageQueue.getDiscardThreshold());
        assertEquals("updated discard action",      DiscardAction.newest,   messageQueue.getDiscardAction());
    }


    @Test
    public void testStaticClientFactory() throws Exception
    {
        config.clientFactoryMethod = this.getClass().getName() + ".createMockClient";
        config.logGroup = "argle";
        config.logStream = "bargle";

        // we have to manually initialize this writer so that it won't get a mock client

        writer = new CloudWatchLogWriter(config, stats);
        new DefaultThreadFactory().startLoggingThread(writer, defaultUncaughtExceptionHandler);

        for (int ii = 0 ; ii < 100 ; ii++)
        {
            if (writer.isInitializationComplete())
                break;
            Thread.sleep(50);
        }

        assertTrue("writer successfully initialized",                                       writer.isInitializationComplete());
        assertNotNull("factory called (local flag)",                                        staticFactoryMock);
        assertEquals("factory called (writer flag)",            config.clientFactoryMethod, writer.getClientFactoryUsed());
        assertNull("no initialization error",                                               stats.getLastError());
        assertNull("no initialization message",                                             stats.getLastErrorMessage());
        assertEquals("describeLogGroups: invocation count",     1,                          staticFactoryMock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    1,                          staticFactoryMock.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",        0,                          staticFactoryMock.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",       0,                          staticFactoryMock.createLogStreamInvocationCount);
        assertEquals("actual log group name, from statistics",  "argle",                    stats.getActualLogGroupName());
        assertEquals("actual log stream name, from statistics", "bargle",                   stats.getActualLogStreamName());
    }
}