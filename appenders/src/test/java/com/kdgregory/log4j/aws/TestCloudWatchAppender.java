// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static net.sf.kdgcommons.test.StringAsserts.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.LogLog;

import net.sf.kdgcommons.lang.StringUtil;

import com.amazonaws.services.logs.model.DescribeLogGroupsRequest;
import com.amazonaws.services.logs.model.DescribeLogGroupsResult;
import com.amazonaws.services.logs.model.InputLogEvent;
import com.amazonaws.services.logs.model.PutLogEventsRequest;
import com.amazonaws.services.logs.model.PutLogEventsResult;

import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.log4j.aws.internal.shared.AbstractLogWriter;
import com.kdgregory.log4j.aws.internal.shared.DefaultThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.MessageQueue;
import com.kdgregory.log4j.aws.internal.shared.MessageQueue.DiscardAction;
import com.kdgregory.log4j.testhelpers.*;
import com.kdgregory.log4j.testhelpers.aws.cloudwatch.*;


public class TestCloudWatchAppender
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
     *  finish initialization. Times out after 5 seconds, otherwise returns
     *  the initialization message.
     */
    private String waitForInitialization() throws Exception
    {
        for (int ii = 0 ; ii < 50 ; ii++)
        {
            AbstractLogWriter writer = (AbstractLogWriter)appender.getWriter();
            if ((writer != null) && (writer.getInitializationMessage() != null))
                return writer.getInitializationMessage();
            else
                Thread.sleep(100);
        }
        fail("timed out waiting for initialization");
        return null; // never reached
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
    public void testConfiguration() throws Exception
    {
        initialize("TestCloudWatchAppender/testConfiguration.properties");

        assertEquals("log group name",      "argle",              appender.getLogGroup());
        assertEquals("log stream name",     "bargle",             appender.getLogStream());
        assertEquals("max delay",           1234L,                appender.getBatchDelay());
        assertEquals("sequence",            2,                    appender.getSequence());
        assertEquals("rotation mode",       "interval",           appender.getRotationMode());
        assertEquals("rotation interval",   86400000L,            appender.getRotationInterval());
        assertEquals("discard threshold",   12345,                appender.getDiscardThreshold());
        assertEquals("discard action",      "newest",             appender.getDiscardAction());
    }


    @Test
    public void testDefaultConfiguration() throws Exception
    {
        initialize("TestCloudWatchAppender/testDefaultConfiguration.properties");

        // note: this is allowed at time of configuration, would disable logger if we try to append
        assertNull("log group name",    appender.getLogGroup());

        assertEquals("log stream name",     "{startupTimestamp}", appender.getLogStream());
        assertEquals("max delay",           2000L,                appender.getBatchDelay());
        assertEquals("sequence",            0,                    appender.getSequence());
        assertEquals("rotation mode",       "none",               appender.getRotationMode());
        assertEquals("rotation interval",   -1,                   appender.getRotationInterval());
        assertEquals("discard threshold",   10000,                appender.getDiscardThreshold());
        assertEquals("discard action",      "oldest",               appender.getDiscardAction());
    }


    @Test
    public void testAppend() throws Exception
    {
        initialize("TestCloudWatchAppender/testAppend.properties");
        MockCloudWatchWriterFactory writerFactory = appender.getWriterFactory();

        long initialTimestamp = System.currentTimeMillis();

        assertNull("before messages, writer is null",                   appender.getWriter());

        logger.debug("first message");

        MockCloudWatchWriter writer = (MockCloudWatchWriter)appender.getWriter();

        assertNotNull("after message 1, writer is initialized",         writer);
        assertEquals("after message 1, calls to writer factory",        1,              writerFactory.invocationCount);
        assertEquals("actual log-group name",                           "argle",        writer.logGroup);
        assertRegex("actual log-stream name",                           "20\\d{12}",    writer.logStream);


        assertEquals("after message 1, number of messages in writer",   1,          writer.messages.size());

        // throw in a sleep so that we can discern timestamps
        Thread.sleep(100);

        logger.error("test with exception", new Exception("this is a test"));

        assertEquals("after message 2, calls to writer factory",        1,          writerFactory.invocationCount);
        assertEquals("after message 2, number of messages in writer",   2,          writer.messages.size());

        long finalTimestamp = System.currentTimeMillis();

        LogMessage message1 = writer.messages.get(0);
        assertTrue("message 1 timestamp >= initial timestamp", message1.getTimestamp() >= initialTimestamp);
        assertTrue("message 1 timestamp <= batch timestamp",   message1.getTimestamp() <= finalTimestamp);
        assertRegex(
                "message 1 generally follows layout: " + message1.getMessage(),
                "20[12][0-9]-.* DEBUG .*TestCloudWatchAppender .*first message.*",
                message1.getMessage().trim());

        LogMessage message2 = writer.messages.get(1);
        assertTrue("message 2 includes exception",
                   message2.getMessage().indexOf("java.lang.Exception") > 0);
        assertTrue("message 2 includes exception",
                   message2.getMessage().indexOf("this is a test") > 0);

        // since we have the writer, we can verify that setting the batch delay gets propagated

        appender.setBatchDelay(1234567);
        assertEquals("writer batch delay propagated", 1234567, writer.batchDelay);
    }


    @Test(expected=IllegalStateException.class)
    public void testThrowsIfAppenderClosed() throws Exception
    {
        initialize("TestCloudWatchAppender/testAppend.properties");

        // write the first message to initialize the appender
        logger.debug("should not throw");

        appender.close();

        // second message should throw
        logger.error("blah blah blah");
    }


    @Test
    public void testWriteHeaderAndFooter() throws Exception
    {
        initialize("TestCloudWatchAppender/testWriteHeaderAndFooter.properties");

        logger.debug("blah blah blah");

        // must retrieve writer before we shut down
        MockCloudWatchWriter mockWriter = appender.getMockWriter();
        LogManager.shutdown();

        assertEquals("number of messages written to log", 3, mockWriter.messages.size());
        assertEquals("header is first", HeaderFooterLayout.HEADER, mockWriter.getMessage(0));
        assertEquals("footer is last",  HeaderFooterLayout.FOOTER, mockWriter.getMessage(2));
    }


    @Test
    public void testSubstitution() throws Exception
    {
        // note that the property value includes invalid characters
        System.setProperty("TestCloudWatchAppender.testSubstitution", "foo/bar");

        initialize("TestCloudWatchAppender/testSubstitution.properties");

        assertEquals("appender's log group name",   "MyLog-{sysprop:TestCloudWatchAppender.testSubstitution}", appender.getLogGroup());
        assertEquals("appender's log stream name",  "MyStream-{timestamp}-{bogus}",                            appender.getLogStream());

        logger.debug("this triggers writer creation");

        MockCloudWatchWriter writer = appender.getMockWriter();

        assertEquals("writers log group name",      "MyLog-foo/bar",                writer.logGroup);
        assertRegex("writers log stream name",      "MyStream-20\\d{12}-\\{bogus}", writer.logStream);
    }


    @Test
    public void testExplicitRotation() throws Exception
    {
        initialize("TestCloudWatchAppender/testExplicitRotation.properties");
        MockCloudWatchWriterFactory writerFactory = appender.getWriterFactory();

        logger.debug("first message");

        MockCloudWatchWriter writer0 = appender.getMockWriter();

        assertEquals("pre-rotate, writer factory calls",            1,          writerFactory.invocationCount);
        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.logStream);

        appender.rotate();

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertEquals("post-rotate, writer factory calls",           2,          writerFactory.invocationCount);
        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.logStream);
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  0,          writer1.messages.size());
    }


    @Test
    public void testCountedRotation() throws Exception
    {
        initialize("TestCloudWatchAppender/testCountedRotation.properties");

        logger.debug("message 1");

        // writer gets created on first append; we want to hold onto it
        MockCloudWatchWriter writer0 = appender.getMockWriter();

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.logStream);

        // these messages should trigger rotation
        logger.debug("message 2");
        logger.debug("message 3");
        logger.debug("message 4");

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.logStream);
        assertEquals("post-rotate, messages passed to old writer",  3,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  1,          writer1.messages.size());
    }


    @Test
    public void testTimedRotation() throws Exception
    {
        initialize("TestCloudWatchAppender/testTimedRotation.properties");

        logger.debug("first message");

        MockCloudWatchWriter writer0 = appender.getMockWriter();

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.logStream);

        appender.updateLastRotationTimestamp(-20000);

        logger.debug("second message");

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.logStream);
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  1,          writer1.messages.size());
    }


    @Test
    public void testInvalidTimedRotationConfiguration() throws Exception
    {
        // note: this will generate a log message that we can't validate and don't want to see
        initialize("TestCloudWatchAppender/testInvalidTimedRotation.properties");
        assertEquals("rotation mode", "none", appender.getRotationMode());
    }


    @Test
    public void testHourlyRotation() throws Exception
    {
        initialize("TestCloudWatchAppender/testHourlyRotation.properties");

        logger.debug("first message");

        MockCloudWatchWriter writer0 = appender.getMockWriter();

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.logStream);

        appender.updateLastRotationTimestamp(-3600000);

        logger.debug("second message");

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.logStream);
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  1,          writer1.messages.size());
    }


    @Test
    public void testDailyRotation() throws Exception
    {
        initialize("TestCloudWatchAppender/testDailyRotation.properties");

        logger.debug("first message");

        MockCloudWatchWriter writer0 = appender.getMockWriter();

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.logStream);

        appender.updateLastRotationTimestamp(-86400000);

        logger.debug("second message");

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.logStream);
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  1,          writer1.messages.size());
    }


    @Test
    public void testMaximumMessageSize() throws Exception
    {
        final int cloudwatchMaximumBatchSize    = 1048576;  // copied from http://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_PutLogEvents.html
        final int cloudwatchOverhead            = 26;       // ditto
        final int layoutOverhead                = 1;        // newline after message

        final int maxMessageSize                =  cloudwatchMaximumBatchSize - (layoutOverhead + cloudwatchOverhead);
        final String bigMessage                 =  StringUtil.repeat('A', maxMessageSize);

        initialize("TestCloudWatchAppender/testMaximumMessageSize.properties");
        // no need to create a writer; cloudwatch messages not dependent on any names

        assertFalse("max message size",             appender.isMessageTooLarge(new LogMessage(System.currentTimeMillis(), bigMessage)));
        assertTrue("bigger than max message size",  appender.isMessageTooLarge(new LogMessage(System.currentTimeMillis(), bigMessage + "1")));
    }


    @Test
    public void testWriterWithExistingGroupAndStream() throws Exception
    {
        initialize("TestCloudWatchAppender/testWriterWithExistingGroupAndStream.properties");

        MockCloudWatchClient mockClient = new MockCloudWatchClient();

        // note that we will be running the writer on a separate thread
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
    }


    @Test
    public void testWriterWithExistingGroupNewStream() throws Exception
    {
        initialize("TestCloudWatchAppender/testWriterWithExistingGroupNewStream.properties");

        MockCloudWatchClient mockClient = new MockCloudWatchClient();

        // note that we will be running the writer on a separate thread
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
        assertEquals("createLogStream: stream name",          "zippy",          mockClient.createLogStreamStreamName);
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
        assertEquals("createLogStream: stream name",          "zippy",          mockClient.createLogStreamStreamName);
        assertEquals("putLogEvents: invocation count",        2,                mockClient.putLogEventsInvocationCount);
        assertEquals("putLogEvents: last call #/messages",    1,                mockClient.mostRecentEvents.size());
        assertEquals("putLogEvents: last message",            "message two\n",  mockClient.mostRecentEvents.get(0).getMessage());
    }


    @Test
    public void testWriterWithNewGroupAndStream() throws Exception
    {
        initialize("TestCloudWatchAppender/testWriterWithNewGroupAndStream.properties");

        MockCloudWatchClient mockClient = new MockCloudWatchClient();

        // note that we will be running the writer on a separate thread
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
    }


    @Test
    public void testInvalidGroupName() throws Exception
    {
        initialize("TestCloudWatchAppender/testInvalidGroupName.properties");

        MockCloudWatchClient mockClient = new MockCloudWatchClient();

        // note that we will be running the writer on a separate thread
        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        logger.debug("message one");

        String initializationMessage = waitForInitialization();

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

        // note that we will be running the writer on a separate thread
        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        logger.debug("message one");

        String initializationMessage = waitForInitialization();

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
            protected DescribeLogGroupsResult describeLogGroups(
                DescribeLogGroupsRequest request)
            {
                throw new TestingException("not now, not ever");
            }
        };

        // note that we will be running the writer on a separate thread
        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        // first message triggers writer creation

        logger.debug("message one");
        waitForInitialization();

        AbstractLogWriter writer = (AbstractLogWriter)appender.getWriter();
        MessageQueue messageQueue = appender.getMessageQueue();

        assertEquals("describeLogGroups: invocation count",     1,                mockClient.describeLogGroupsInvocationCount);
        assertEquals("describeLogStreams: invocation count",    0,                mockClient.describeLogStreamsInvocationCount);

        assertTrue("initialization message was non-blank",      ! writer.getInitializationMessage().equals(""));
        assertEquals("initialization exception retained",       TestingException.class,     writer.getInitializationException().getClass());
        assertEquals("initialization error message",            "not now, not ever",        writer.getInitializationException().getMessage());


        assertEquals("message queue set to discard all",        0,                          messageQueue.getDiscardThreshold());
        assertEquals("message queue set to discard all",        DiscardAction.oldest,       messageQueue.getDiscardAction());
        assertEquals("messages in queue (initial)",             1,                          messageQueue.toList().size());

        // trying to log another message should clear the queue

        logger.info("message two");
        assertEquals("messages in queue (second try)",          0,                          messageQueue.toList().size());
    }


    @Test
    public void testUncaughtExceptionHandling() throws Exception
    {
        initialize("TestCloudWatchAppender/testUncaughtExceptionHandling.properties");

        // note that we will be running the writer on a separate thread

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(new ThrowingWriterFactory<CloudWatchWriterConfig>());

        logger.debug("this should trigger writer creation");

        assertNull("writer has not yet thrown",         appender.getLastWriterException());

        logger.debug("this should trigger writer throwage");

        // without getting really clever, the best way to wait for the throw to be reported is to sit and spin
        for (int ii = 0 ; (ii < 10) && (appender.getLastWriterException() == null) ; ii++)
        {
            Thread.sleep(10);
        }

        assertNull("writer has been reset",         appender.getWriter());
        assertEquals("last writer exception class", TestingException.class, appender.getLastWriterException().getClass());
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

        // this is a dummy client: never actually run the writer thread, but
        // need to test the real writer
        MockCloudWatchClient mockClient = new MockCloudWatchClient()
        {
            @Override
            protected PutLogEventsResult putLogEvents(PutLogEventsRequest request)
            {
                throw new IllegalStateException("should never be called");
            }
        };

        appender.setThreadFactory(new NullThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        for (int ii = 0 ; ii < 20 ; ii++)
        {
            logger.debug("message " + ii);
        }

        List<LogMessage> messages = appender.getMessageQueue().toList();

        assertEquals("number of messages in queue", 10, messages.size());
        assertEquals("oldest message", "message 10\n", messages.get(0).getMessage());
        assertEquals("newest message", "message 19\n", messages.get(9).getMessage());
    }


    @Test
    public void testDiscardNewest() throws Exception
    {
        initialize("TestCloudWatchAppender/testDiscardNewest.properties");

        // this is a dummy client: never actually run the writer thread, but
        // need to test the real writer
        MockCloudWatchClient mockClient = new MockCloudWatchClient()
        {
            @Override
            protected PutLogEventsResult putLogEvents(PutLogEventsRequest request)
            {
                throw new IllegalStateException("should never be called");
            }
        };

        appender.setThreadFactory(new NullThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        for (int ii = 0 ; ii < 20 ; ii++)
        {
            logger.debug("message " + ii);
        }

        List<LogMessage> messages = appender.getMessageQueue().toList();

        assertEquals("number of messages in queue", 10, messages.size());
        assertEquals("oldest message", "message 0\n", messages.get(0).getMessage());
        assertEquals("newest message", "message 9\n", messages.get(9).getMessage());
    }


    @Test
    public void testDiscardNone() throws Exception
    {
        initialize("TestCloudWatchAppender/testDiscardNone.properties");

        // this is a dummy client: we never actually run the writer thread, but
        // need to test the real writer
        MockCloudWatchClient mockClient = new MockCloudWatchClient()
        {
            @Override
            protected PutLogEventsResult putLogEvents(PutLogEventsRequest request)
            {
                throw new IllegalStateException("should never be called");
            }
        };

        appender.setThreadFactory(new NullThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        for (int ii = 0 ; ii < 20 ; ii++)
        {
            logger.debug("message " + ii);
        }

        List<LogMessage> messages = appender.getMessageQueue().toList();

        assertEquals("number of messages in queue", 20, messages.size());
        assertEquals("oldest message", "message 0\n", messages.get(0).getMessage());
        assertEquals("newest message", "message 19\n", messages.get(19).getMessage());
    }


    @Test
    public void testReconfigureDiscardProperties() throws Exception
    {
        initialize("TestCloudWatchAppender/testReconfigureDiscardProperties.properties");

        // another test where we don't actually do anything but need to verify actual writer

        appender.setThreadFactory(new NullThreadFactory());
        appender.setWriterFactory(new MockCloudWatchClient().newWriterFactory());

        logger.debug("trigger writer creation");

        MessageQueue messageQueue = appender.getMessageQueue();

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
}
