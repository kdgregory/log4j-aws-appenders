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

import static net.sf.kdgcommons.test.StringAsserts.*;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.lang.StringUtil;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchAppenderStatistics;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterFactory;
import com.kdgregory.logging.aws.testhelpers.AbstractLogWriterTest;
import com.kdgregory.logging.aws.testhelpers.TestingException;
import com.kdgregory.logging.aws.testhelpers.cloudwatch.MockCloudWatchClient;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.common.util.MessageQueue;


/**
 *  Performs mock-client testing of the CloudWatch writer.
 */
public class TestCloudWatchLogWriter
extends AbstractLogWriterTest<CloudWatchLogWriter,CloudWatchWriterConfig,CloudWatchAppenderStatistics,AWSLogs>
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

        stats = new CloudWatchAppenderStatistics();

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

        assertEquals("debug message count",                     0,                  internalLogger.debugMessages.size());
        assertEquals("error message count",                     0,                  internalLogger.errorMessages.size());
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

        assertEquals("debug message count",                     1,                  internalLogger.debugMessages.size());
        assertEquals("error message count",                     0,                  internalLogger.errorMessages.size());
        assertRegex("debug messages indicates stream creation", "creat.*stream.*",  internalLogger.debugMessages.get(0));
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

        assertEquals("debug message count",                     2,                  internalLogger.debugMessages.size());
        assertEquals("error message count",                     0,                  internalLogger.errorMessages.size());
        assertRegex("debug messages indicates group creation",  "creat.*group.*",   internalLogger.debugMessages.get(0));
        assertRegex("debug messages indicates stream creation", "creat.*stream.*",  internalLogger.debugMessages.get(1));
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

        assertEquals("messages sent, from statistics",          1,                  stats.getMessagesSent());
        assertEquals("actual log group name, from statistics",  "argle",            stats.getActualLogGroupName());
        assertEquals("actual log stream name, from statistics", "fribble",          stats.getActualLogStreamName());
    }


    @Test
    public void testInvalidGroupName() throws Exception
    {
        config.logGroupName = "I'm No Good!";
        config.logStreamName = "Although, I Am";

        createWriter();

        // we don't need to write any messages; writer should fail to initialize

        String errorMessage = stats.getLastErrorMessage();
        assertRegex("error message indicates failure, with group name (was: " + errorMessage + ")",
                    ".*log group.*" + config.logGroupName + "$",
                    errorMessage);

        assertEquals("describeLogGroups: invocation count",     0,                      mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    0,                      mock.describeLogStreamsInvocationCount);

        assertEquals("debug message count",                     0,                      internalLogger.debugMessages.size());
        assertEquals("error message count",                     1,                      internalLogger.errorMessages.size());
        assertRegex("error message",                            ".*invalid.*group.*",   internalLogger.errorMessages.get(0));
    }


    @Test
    public void testInvalidStreamName() throws Exception
    {
        config.logGroupName = "IAmOK";
        config.logStreamName = "But: I'm Not";

        createWriter();

        // we don't need to write any messages; writer should fail to initialize

        String errorMessage = stats.getLastErrorMessage();
        assertRegex("error message indicates failure, with stream name (was: " + errorMessage + ")",
                    ".*log stream.*" + config.logStreamName + "$",
                    errorMessage);

        assertEquals("describeLogGroups: invocation count",     0,                      mock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    0,                      mock.describeLogStreamsInvocationCount);

        assertEquals("debug message count",                     0,                      internalLogger.debugMessages.size());
        assertEquals("error message count",                     1,                      internalLogger.errorMessages.size());
        assertRegex("error message",                            ".*invalid.*stream.*",  internalLogger.errorMessages.get(0));
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

        assertRegex("log: error message",                               "unable to configure.*",    stats.getLastErrorMessage());
        assertEquals("stats: exception class",                          TestingException.class,     stats.getLastError().getClass());
        assertEquals("stats: exception message",                        "not now, not ever",        stats.getLastError().getMessage());
        assertTrue("stats: exception trace",                                                        stats.getLastErrorStacktrace().size() > 0);
        assertNotNull("stats: exception timestamp",                                                 stats.getLastErrorTimestamp());

        assertEquals("log: debug message count",                        0,                          internalLogger.debugMessages.size());
        assertEquals("log: error message count",                        1,                          internalLogger.errorMessages.size());
        assertRegex("log: error message",                               "unable to configure.*",    internalLogger.errorMessages.get(0));
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

        assertEquals("log: debug message count",                        0,                      internalLogger.debugMessages.size());
        assertEquals("log: error message count",                        2,                      internalLogger.errorMessages.size());
        assertRegex("log: error message (0)",                           "failed to send.*",     internalLogger.errorMessages.get(0));
        assertEquals("log: exception (0)",                              TestingException.class, internalLogger.errorExceptions.get(0).getClass());
        assertRegex("log: error message (1)",                           "failed to send.*",     internalLogger.errorMessages.get(1));
        assertEquals("log: exception (1)",                              TestingException.class, internalLogger.errorExceptions.get(1).getClass());

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

        // will get an error message when stream goes missing, debug when it's recreated

        assertEquals("log: debug message count",                1,                      internalLogger.debugMessages.size());
        assertRegex("log: debug message content",               "creat.*stream.*",      internalLogger.debugMessages.get(0));
        assertEquals("log: error message count",                1,                      internalLogger.errorMessages.size());
        assertRegex("log: error message content",               ".*stream.*missing.*",  internalLogger.errorMessages.get(0));

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

        // this sleep is a hack to enable the writer thread to update stats
        Thread.sleep(50);

        assertEquals("total messages sent, from statistics",    numMessages,        stats.getMessagesSent());
        assertEquals("debug message count",                     0,                  internalLogger.debugMessages.size());
        assertEquals("error message count",                     0,                  internalLogger.errorMessages.size());
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

        assertEquals("total messages sent, from statistics",    numMessages,        stats.getMessagesSent());
        assertEquals("debug message count",                     0,                  internalLogger.debugMessages.size());
        assertEquals("error message count",                     0,                  internalLogger.errorMessages.size());
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

        assertRegex("log: debug message indicating factory",    ".*created client from factory.*" + getClass().getName() + ".*",
                                                                internalLogger.debugMessages.get(0));
    }
}
