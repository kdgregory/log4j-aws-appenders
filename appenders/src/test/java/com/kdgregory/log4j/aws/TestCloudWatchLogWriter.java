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

package com.kdgregory.log4j.aws;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.LogLog;

import net.sf.kdgcommons.lang.ClassUtil;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.DescribeLogGroupsRequest;
import com.amazonaws.services.logs.model.DescribeLogGroupsResult;
import com.amazonaws.services.logs.model.InputLogEvent;
import com.amazonaws.services.logs.model.PutLogEventsRequest;
import com.amazonaws.services.logs.model.PutLogEventsResult;

import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchAppenderStatistics;
import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchWriterFactory;
import com.kdgregory.log4j.aws.internal.shared.AbstractLogWriter;
import com.kdgregory.log4j.aws.internal.shared.DefaultThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.MessageQueue;
import com.kdgregory.log4j.aws.internal.shared.MessageQueue.DiscardAction;
import com.kdgregory.log4j.testhelpers.*;
import com.kdgregory.log4j.testhelpers.aws.cloudwatch.*;


/**
 *  These tests exercise the interaction between the appender and CloudWatch, via
 *  an actual writer. To do that, they mock out the CloudWatch client. Most of
 *  these tests spin up an actual writer thread, so must coordinate interaction
 *  between that thread and the test (main) thread.
 */
public class TestCloudWatchLogWriter
{
    private Logger logger;
    private TestableCloudWatchAppender appender;


    private void initialize(String propsName)
    throws Exception
    {
        URL config = ClassLoader.getSystemResource(propsName);
        assertNotNull("was able to retrieve config", config);
        PropertyConfigurator.configure(config);

        logger = Logger.getLogger(getClass());

        Logger rootLogger = Logger.getRootLogger();
        appender = (TestableCloudWatchAppender)rootLogger.getAppender("default");

        appender.setThreadFactory(new InlineThreadFactory());
        appender.setWriterFactory(new MockCloudWatchWriterFactory(appender));
    }


    /**
     *  A spin loop that waits for an writer running in another thread to
     *  finish initialization, either successfully or with error.
     */
    private void waitForInitialization() throws Exception
    {
        for (int ii = 0 ; ii < 50 ; ii++)
        {
            AbstractLogWriter writer = (AbstractLogWriter)appender.getWriter();
            if ((writer != null) && writer.isInitializationComplete())
                return;
            else if (appender.getAppenderStatistics().getLastErrorMessage() != null)
                return;
            else
                Thread.sleep(100);
        }
        fail("timed out waiting for initialization");
    }


    // the following variable and function are used by testStaticClientFactory

    private static MockCloudWatchClient staticFactoryMock = null;

    public static AWSLogs createMockClient()
    {
        staticFactoryMock = new MockCloudWatchClient();
        return staticFactoryMock.createClient();
    }

//----------------------------------------------------------------------------
//  JUnit stuff
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        LogManager.resetConfiguration();
        LogLog.setQuietMode(true);
    }


    @After
    public void tearDown()
    {
        LogLog.setQuietMode(false);
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testWriterWithExistingGroupAndStream() throws Exception
    {
        initialize("TestCloudWatchAppender/testWriterWithExistingGroupAndStream.properties");

        MockCloudWatchClient mockClient = new MockCloudWatchClient();

        // TODO: need a new thread factory that creates non-daemon threads
        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        logger.debug("message one");
        mockClient.allowWriterThread();

        // will call describeLogGroups when checking group existence
        // will call describeLogStreams when checking stream existence, as well as for each putLogEvents

        assertEquals("describeLogGroups: invocation count",   2,                mockClient.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",  4,                mockClient.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",      0,                mockClient.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",     0,                mockClient.createLogStreamInvocationCount);
        assertEquals("putLogEvents: invocation count",        1,                mockClient.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",    1,                mockClient.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",            "message one\n",  mockClient.mostRecentEvents.get(0).getMessage());

        logger.debug("message two");
        mockClient.allowWriterThread();

        assertEquals("describeLogGroups: invocation count",   2,                mockClient.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",  6,                mockClient.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",      0,                mockClient.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",     0,                mockClient.createLogStreamInvocationCount);
        assertEquals("putLogEvents: invocation count",        2,                mockClient.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",    1,                mockClient.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",            "message two\n",  mockClient.mostRecentEvents.get(0).getMessage());

        assertEquals("messages sent, from statistics",          2,              appender.getAppenderStatistics().getMessagesSent());
        assertEquals("actual log group name, from statistics",  "argle",        appender.getAppenderStatistics().getActualLogGroupName());
        assertEquals("actual log stream name, from statistics", "bargle",       appender.getAppenderStatistics().getActualLogStreamName());
    }


    @Test
    public void testWriterWithExistingGroupNewStream() throws Exception
    {
        initialize("TestCloudWatchAppender/testWriterWithExistingGroupNewStream.properties");

        MockCloudWatchClient mockClient = new MockCloudWatchClient();

        // TODO: need a new thread factory that creates non-daemon threads
        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        logger.debug("message one");
        mockClient.allowWriterThread();

        // will call describeLogGroups when checking group existence
        // will call describeLogStreams before and after creating stream, as well as for each putLogEvents

        assertEquals("describeLogGroups: invocation count",   2,                mockClient.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",  6,                mockClient.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",      0,                mockClient.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",     1,                mockClient.createLogStreamInvocationCount);
        assertEquals("createLogStream: group name",           "argle",          mockClient.createLogStreamGroupName);
        assertEquals("createLogStream: stream name",          "zippy-0",        mockClient.createLogStreamStreamName);
        assertEquals("putLogEvents: invocation count",        1,                mockClient.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",    1,                mockClient.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",            "message one\n",  mockClient.mostRecentEvents.get(0).getMessage());

        logger.debug("message two");
        mockClient.allowWriterThread();

        assertEquals("describeLogGroups: invocation count",   2,                mockClient.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",  8,                mockClient.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",      0,                mockClient.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",     1,                mockClient.createLogStreamInvocationCount);
        assertEquals("createLogStream: group name",           "argle",          mockClient.createLogStreamGroupName);
        assertEquals("createLogStream: stream name",          "zippy-0",        mockClient.createLogStreamStreamName);
        assertEquals("putLogEvents: invocation count",        2,                mockClient.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",    1,                mockClient.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",            "message two\n",  mockClient.mostRecentEvents.get(0).getMessage());

        assertEquals("messages sent, from statistics",          2,              appender.getAppenderStatistics().getMessagesSent());
        assertEquals("actual log group name, from statistics",  "argle",        appender.getAppenderStatistics().getActualLogGroupName());
        assertEquals("actual log stream name, from statistics", "zippy-0",      appender.getAppenderStatistics().getActualLogStreamName());
    }


    @Test
    public void testWriterWithNewGroupAndStream() throws Exception
    {
        initialize("TestCloudWatchAppender/testWriterWithNewGroupAndStream.properties");

        MockCloudWatchClient mockClient = new MockCloudWatchClient();

        // TODO: need a new thread factory that creates non-daemon threads
        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        logger.debug("message one");
        mockClient.allowWriterThread();

        // will call describeLogGroups both before and after creating group
        // will call describeLogStreams before and after creating stream, as well as for each putLogEvents

        assertEquals("describeLogGroups: invocation count",   4,                mockClient.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",  6,                mockClient.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",      1,                mockClient.createLogGroupInvocationCount);
        assertEquals("createLogGroup: group name",            "griffy",         mockClient.createLogGroupGroupName);
        assertEquals("createLogStream: invocation count",     1,                mockClient.createLogStreamInvocationCount);
        assertEquals("createLogStream: group name",           "griffy",         mockClient.createLogStreamGroupName);
        assertEquals("createLogStream: stream name",          "zippy",          mockClient.createLogStreamStreamName);
        assertEquals("putLogEvents: invocation count",        1,                mockClient.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",    1,                mockClient.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",            "message one\n",  mockClient.mostRecentEvents.get(0).getMessage());

        logger.debug("message two");
        mockClient.allowWriterThread();

        assertEquals("describeLogGroups: invocation count",   4,                mockClient.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",  8,                mockClient.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",      1,                mockClient.createLogGroupInvocationCount);
        assertEquals("createLogGroup: group name",            "griffy",         mockClient.createLogGroupGroupName);
        assertEquals("createLogStream: invocation count",     1,                mockClient.createLogStreamInvocationCount);
        assertEquals("createLogStream: group name",           "griffy",         mockClient.createLogStreamGroupName);
        assertEquals("createLogStream: stream name",          "zippy",          mockClient.createLogStreamStreamName);
        assertEquals("putLogEvents: invocation count",        2,                mockClient.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",    1,                mockClient.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",            "message two\n",  mockClient.mostRecentEvents.get(0).getMessage());

        assertEquals("messages sent, from statistics",          2,              appender.getAppenderStatistics().getMessagesSent());
        assertEquals("actual log group name, from statistics",  "griffy",       appender.getAppenderStatistics().getActualLogGroupName());
        assertEquals("actual log stream name, from statistics", "zippy",        appender.getAppenderStatistics().getActualLogStreamName());
    }


    @Test
    public void testInvalidGroupName() throws Exception
    {
        initialize("TestCloudWatchAppender/testInvalidGroupName.properties");

        MockCloudWatchClient mockClient = new MockCloudWatchClient();

        // TODO: need a new thread factory that creates non-daemon threads
        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        logger.debug("message one");

        waitForInitialization();
        String initializationMessage = appender.getAppenderStatistics().getLastErrorMessage();

        assertTrue("initialization message indicates invalid group name (was: " + initializationMessage + ")",
                   initializationMessage.contains("invalid log group name"));
        assertTrue("initialization message contains invalid name (was: " + initializationMessage + ")",
                   initializationMessage.contains("helpme!"));

        assertEquals("describeLogGroups: should not be invoked",    0,  mockClient.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: should not be invoked",   0,  mockClient.describeLogStreamsInvocationCount);
    }


    @Test
    public void testInvalidStreamName() throws Exception
    {
        initialize("TestCloudWatchAppender/testInvalidStreamName.properties");

        MockCloudWatchClient mockClient = new MockCloudWatchClient();

        // TODO: need a new thread factory that creates non-daemon threads
        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        logger.debug("message one");

        waitForInitialization();
        String initializationMessage = appender.getAppenderStatistics().getLastErrorMessage();

        assertTrue("initialization message indicates invalid stream name (was: " + initializationMessage + ")",
                   initializationMessage.contains("invalid log stream name"));
        assertTrue("initialization message contains invalid name (was: " + initializationMessage + ")",
                   initializationMessage.contains("I:Am:Not"));

        assertEquals("describeLogGroups: should not be invoked",    0,  mockClient.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: should not be invoked",   0,  mockClient.describeLogStreamsInvocationCount);
    }


    @Test
    public void testInitializationErrorHandling() throws Exception
    {
        initialize("TestCloudWatchAppender/testInitializationErrorHandling.properties");

        MockCloudWatchClient mockClient = new MockCloudWatchClient()
        {
            @Override
            protected DescribeLogGroupsResult describeLogGroups(DescribeLogGroupsRequest request)
            {
                throw new TestingException("not now, not ever");
            }
        };

        // TODO: need a new thread factory that creates non-daemon threads
        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        // first message triggers writer creation

        logger.debug("message one");

        waitForInitialization();
        String initializationMessage = appender.getAppenderStatistics().getLastErrorMessage();
        Throwable initializationError = appender.getAppenderStatistics().getLastError();

        AbstractLogWriter writer = (AbstractLogWriter)appender.getWriter();
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        assertEquals("describeLogGroups: invocation count",     1,                          mockClient.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    0,                          mockClient.describeLogStreamsInvocationCount);

        assertNotNull("writer still exists",                                                writer);
        assertTrue("initialization message was non-blank",                                  ! initializationMessage.equals(""));
        assertEquals("initialization exception retained",       TestingException.class,     initializationError.getClass());
        assertEquals("initialization error message",            "not now, not ever",        initializationError.getMessage());


        assertEquals("message queue set to discard all",        0,                          messageQueue.getDiscardThreshold());
        assertEquals("message queue set to discard all",        DiscardAction.oldest,       messageQueue.getDiscardAction());
        assertEquals("messages in queue (initial)",             1,                          messageQueue.toList().size());

        // trying to log another message should clear the queue

        logger.info("message two");
        assertEquals("messages in queue (second try)",          0,                          messageQueue.toList().size());
    }


    @Test
    public void testBatchExceptionHandling() throws Exception
    {
        initialize("TestCloudWatchAppender/testBatchExceptionHandling.properties");

        MockCloudWatchClient mockClient = new MockCloudWatchClient()
        {
            @Override
            protected PutLogEventsResult putLogEvents(PutLogEventsRequest request)
            {
                throw new TestingException("can't send it");
            }
        };

        // TODO: need a new thread factory that creates non-daemon threads
        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        CloudWatchAppenderStatistics appenderStats = appender.getAppenderStatistics();

        // first message triggers writer creation

        logger.debug("message one");

        mockClient.allowWriterThread();

        AbstractLogWriter writer = (AbstractLogWriter)appender.getWriter();
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);
        Throwable lastError = appenderStats.getLastError();

        assertEquals("putLogEvents called",                     1,                          mockClient.putLogEventsInvocationCount);
        assertNotNull("writer still exists",                                                writer);
        assertEquals("stats reports no messages sent",          0,                          appenderStats.getMessagesSent());
        assertTrue("error message was non-blank",                                           ! appenderStats.getLastErrorMessage().equals(""));
        assertEquals("exception retained",                      TestingException.class,     lastError.getClass());
        assertEquals("exception message",                       "can't send it",            lastError.getMessage());
        assertTrue("message queue still accepts messages",                                  messageQueue.getDiscardThreshold() > 0);

        // the background thread will try to assemble another batch right away, so we can't examine
        // the message queue; instead we'll wait for the writer to call PutLogEvents again

        mockClient.allowWriterThread();

        assertEquals("putLogEvents called again",               2,                          mockClient.putLogEventsInvocationCount);
    }


    @Test
    public void testMessageErrorHandling() throws Exception
    {
        // WARNING: this test may break if the internal implementation changes

        initialize("TestCloudWatchAppender/testMessageErrorHandling.properties");

        // the mock client -- will throw on odd invocations
        MockCloudWatchClient mockClient = new MockCloudWatchClient()
        {
            @Override
            protected PutLogEventsResult putLogEvents(PutLogEventsRequest request)
            {
                if (putLogEventsInvocationCount % 2 == 1)
                {
                    throw new TestingException("anything");
                }
                else
                {
                    return super.putLogEvents(request);
                }
            }
        };

        // TODO: need a new thread factory that creates non-daemon threads
        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        for (int ii = 0 ; ii < 10 ; ii++)
        {
            logger.debug("message " + ii);
        }

        mockClient.allowWriterThread();

        assertEquals("first batch, number of events in request", 10, mockClient.mostRecentEvents.size());

        List<InputLogEvent> preservedEvents = new ArrayList<InputLogEvent>(mockClient.mostRecentEvents);

        // the first batch should have been returned to the message queue, in order

        mockClient.allowWriterThread();

        assertEquals("second batch, number of events in request", 10, mockClient.mostRecentEvents.size());

        for (int ii = 0 ; ii < mockClient.mostRecentEvents.size() ; ii++)
        {
            assertEquals("event #" + ii, preservedEvents.get(ii), mockClient.mostRecentEvents.get(ii));
        }

        // now assert that those messages will not be resent

        for (int ii = 100 ; ii < 102 ; ii++)
        {
            logger.debug("message " + ii);
        }

        mockClient.allowWriterThread();

        assertEquals("third batch, number of events in request", 2, mockClient.mostRecentEvents.size());
    }


    @Test
    public void testDiscardOldest() throws Exception
    {
        initialize("TestCloudWatchAppender/testDiscardOldest.properties");

        appender.setThreadFactory(new NullThreadFactory());
        appender.setWriterFactory(new MockCloudWatchClient().newWriterFactory());

        for (int ii = 0 ; ii < 20 ; ii++)
        {
            logger.debug("message " + ii);
        }

        MessageQueue messageQueue = ClassUtil.getFieldValue(appender.getWriter(), "messageQueue", MessageQueue.class);
        List<LogMessage> messages = messageQueue.toList();

        assertEquals("number of messages in queue", 10, messages.size());
        assertEquals("oldest message", "message 10\n", messages.get(0).getMessage());
        assertEquals("newest message", "message 19\n", messages.get(9).getMessage());
    }


    @Test
    public void testDiscardNewest() throws Exception
    {
        initialize("TestCloudWatchAppender/testDiscardNewest.properties");

        appender.setThreadFactory(new NullThreadFactory());
        appender.setWriterFactory(new MockCloudWatchClient().newWriterFactory());

        for (int ii = 0 ; ii < 20 ; ii++)
        {
            logger.debug("message " + ii);
        }

        MessageQueue messageQueue = ClassUtil.getFieldValue(appender.getWriter(), "messageQueue", MessageQueue.class);
        List<LogMessage> messages = messageQueue.toList();

        assertEquals("number of messages in queue", 10, messages.size());
        assertEquals("oldest message", "message 0\n", messages.get(0).getMessage());
        assertEquals("newest message", "message 9\n", messages.get(9).getMessage());
    }


    @Test
    public void testDiscardNone() throws Exception
    {
        initialize("TestCloudWatchAppender/testDiscardNone.properties");

        appender.setThreadFactory(new NullThreadFactory());
        appender.setWriterFactory(new MockCloudWatchClient().newWriterFactory());

        for (int ii = 0 ; ii < 20 ; ii++)
        {
            logger.debug("message " + ii);
        }

        MessageQueue messageQueue = ClassUtil.getFieldValue(appender.getWriter(), "messageQueue", MessageQueue.class);
        List<LogMessage> messages = messageQueue.toList();

        assertEquals("number of messages in queue", 20, messages.size());
        assertEquals("oldest message", "message 0\n", messages.get(0).getMessage());
        assertEquals("newest message", "message 19\n", messages.get(19).getMessage());
    }


    @Test
    public void testReconfigureDiscardProperties() throws Exception
    {
        initialize("TestCloudWatchAppender/testReconfigureDiscardProperties.properties");

        appender.setThreadFactory(new NullThreadFactory());
        appender.setWriterFactory(new MockCloudWatchClient().newWriterFactory());

        logger.debug("trigger writer creation");

        MessageQueue messageQueue = ClassUtil.getFieldValue(appender.getWriter(), "messageQueue", MessageQueue.class);

        assertEquals("initial discard threshold, from appender",    12345,                              appender.getDiscardThreshold());
        assertEquals("initial discard action, from appender",       DiscardAction.newest.toString(),    appender.getDiscardAction());

        assertEquals("initial discard threshold, from queue",       12345,                              messageQueue.getDiscardThreshold());
        assertEquals("initial discard action, from queue",          DiscardAction.newest.toString(),    messageQueue.getDiscardAction().toString());

        appender.setDiscardThreshold(54321);
        appender.setDiscardAction(DiscardAction.oldest.toString());

        assertEquals("updated discard threshold, from appender",    54321,                              appender.getDiscardThreshold());
        assertEquals("updated discard action, from appender",       DiscardAction.oldest.toString(),    appender.getDiscardAction());

        assertEquals("updated discard threshold, from queue",       54321,                              messageQueue.getDiscardThreshold());
        assertEquals("updated discard action, from queue",          DiscardAction.oldest.toString(),    messageQueue.getDiscardAction().toString());
    }


    @Test
    public void testStaticClientFactory() throws Exception
    {
        initialize("TestCloudWatchAppender/testStaticClientFactory.properties");

        // TODO: need a new thread factory that creates non-daemon threads
        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(new CloudWatchWriterFactory());

        // first message triggers writer creation

        logger.debug("message one");

        waitForInitialization();

        AbstractLogWriter writer = (AbstractLogWriter)appender.getWriter();

        assertNotNull("factory was called to create client",    staticFactoryMock);
        assertNull("no initialization message",                 appender.getAppenderStatistics().getLastErrorMessage());
        assertNull("no initialization error",                   appender.getAppenderStatistics().getLastError());
        assertEquals("writer called factory method",            "com.kdgregory.log4j.aws.TestCloudWatchLogWriter.createMockClient",
                                                                writer.getClientFactoryUsed());

        // at this point we know that the factory was called, but we'll let the client write
        // the message and use the same asserts as in testWriterWithExistingGroupAndStream()

        staticFactoryMock.allowWriterThread();

        assertEquals("describeLogGroups: invocation count",   2,                staticFactoryMock.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",  4,                staticFactoryMock.describeLogStreamsInvocationCount);
        assertEquals("createLogGroup: invocation count",      0,                staticFactoryMock.createLogGroupInvocationCount);
        assertEquals("createLogStream: invocation count",     0,                staticFactoryMock.createLogStreamInvocationCount);
        assertEquals("putLogEvents: invocation count",        1,                staticFactoryMock.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",    1,                staticFactoryMock.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",            "message one\n",  staticFactoryMock.mostRecentEvents.get(0).getMessage());
    }
}
