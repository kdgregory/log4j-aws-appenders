// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import static net.sf.kdgcommons.test.StringAsserts.*;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.LogLog;

import net.sf.kdgcommons.lang.StringUtil;

import com.amazonaws.services.kinesis.model.*;
import com.amazonaws.util.BinaryUtils;

import com.kdgregory.log4j.aws.internal.kinesis.KinesisWriterConfig;
import com.kdgregory.log4j.aws.internal.shared.AbstractLogWriter;
import com.kdgregory.log4j.aws.internal.shared.DefaultThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.MessageQueue;
import com.kdgregory.log4j.aws.internal.shared.MessageQueue.DiscardAction;
import com.kdgregory.log4j.testhelpers.HeaderFooterLayout;
import com.kdgregory.log4j.testhelpers.InlineThreadFactory;
import com.kdgregory.log4j.testhelpers.NullThreadFactory;
import com.kdgregory.log4j.testhelpers.TestingException;
import com.kdgregory.log4j.testhelpers.ThrowingWriterFactory;
import com.kdgregory.log4j.testhelpers.aws.kinesis.MockKinesisClient;
import com.kdgregory.log4j.testhelpers.aws.kinesis.MockKinesisWriter;
import com.kdgregory.log4j.testhelpers.aws.kinesis.MockKinesisWriterFactory;
import com.kdgregory.log4j.testhelpers.aws.kinesis.TestableKinesisAppender;


public class TestKinesisAppender
{
    private Logger logger;
    private TestableKinesisAppender appender;


    private void initialize(String propsName)
    throws Exception
    {
        URL config = ClassLoader.getSystemResource(propsName);
        PropertyConfigurator.configure(config);

        logger = Logger.getLogger(getClass());

        Logger rootLogger = Logger.getRootLogger();
        appender = (TestableKinesisAppender)rootLogger.getAppender("default");

        appender.setThreadFactory(new InlineThreadFactory());
        appender.setWriterFactory(new MockKinesisWriterFactory(appender));
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
        return null; // never reached, but the compiler doesn't know that
    }

//----------------------------------------------------------------------------
//  JUnit stuff
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
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
        initialize("TestKinesisAppender/testConfiguration.properties");

        assertEquals("stream name",         "argle-{bargle}",       appender.getStreamName());
        assertEquals("partition key",       "foo-{date}",           appender.getPartitionKey());
        assertEquals("shard count",         7,                      appender.getShardCount());
        assertEquals("retention period",    48,                     appender.getRetentionPeriod());
        assertEquals("max delay",           1234L,                  appender.getBatchDelay());
        assertEquals("discard threshold",   54321,                  appender.getDiscardThreshold());
        assertEquals("discard action",      "newest",               appender.getDiscardAction());
    }


    @Test
    public void testDefaultConfiguration() throws Exception
    {
        initialize("TestKinesisAppender/testDefaultConfiguration.properties");

        // don't test stream name because there's no default
        assertEquals("partition key",       "{startupTimestamp}",   appender.getPartitionKey());
        assertEquals("shard count",         1,                      appender.getShardCount());
        assertEquals("retention period",    24,                     appender.getRetentionPeriod());
        assertEquals("max delay",           2000L,                  appender.getBatchDelay());
        assertEquals("discard threshold",   10000,                  appender.getDiscardThreshold());
        assertEquals("discard action",      "oldest",               appender.getDiscardAction());
    }


    @Test
    public void testAppend() throws Exception
    {
        initialize("TestKinesisAppender/testAppend.properties");
        MockKinesisWriterFactory writerFactory = appender.getWriterFactory();

        long initialTimestamp = System.currentTimeMillis();

        // this sleep is to make timestamps discernable
        Thread.sleep(100);

        assertNull("before messages, writer is null",                           appender.getMockWriter());

        logger.debug("first message");

        MockKinesisWriter writer = appender.getMockWriter();

        assertNotNull("after message 1, writer is initialized",                 writer);
        assertEquals("after message 1, calls to writer factory",                1,              writerFactory.invocationCount);

        assertRegex("stream name, with substitutions",                          "argle-\\d+",   writer.streamName);
        assertRegex("default partition key, after substitutions",               "20\\d{12}",    writer.partitionKey);
        assertEquals("after message 1, number of messages in writer",           1,              writer.messages.size());

        logger.error("test with exception", new Exception("this is a test"));

        assertEquals("after message 2, calls to writer factory",                1,          writerFactory.invocationCount);
        assertEquals("after message 2, number of messages in writer",           2,          writer.messages.size());

        long finalTimestamp = System.currentTimeMillis();

        LogMessage message1 = writer.messages.get(0);
        assertTrue("message 1 timestamp >= initial timestamp", message1.getTimestamp() >= initialTimestamp);
        assertTrue("message 1 timestamp <= batch timestamp",   message1.getTimestamp() <= finalTimestamp);
        assertRegex(
                "message 1 generally follows layout: " + message1.getMessage(),
                "20[12][0-9]-.* DEBUG .*TestKinesisAppender .*first message.*",
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
        initialize("TestKinesisAppender/testAppend.properties");

        // write the first message to initialize the appender
        logger.debug("should not throw");

        appender.close();

        // second message should throw
        logger.error("blah blah blah");
    }


    @Test
    public void testWriteHeaderAndFooter() throws Exception
    {
        initialize("TestKinesisAppender/testWriteHeaderAndFooter.properties");

        logger.debug("blah blah blah");

        // must retrieve writer before we shut down
        MockKinesisWriter writer = appender.getMockWriter();
        LogManager.shutdown();

        assertEquals("number of messages written to log", 3, writer.messages.size());
        assertEquals("header is first", HeaderFooterLayout.HEADER, writer.getMessage(0));
        assertEquals("footer is last",  HeaderFooterLayout.FOOTER, writer.getMessage(2));
    }


    @Test
    public void testMaximumMessageSize() throws Exception
    {
        final int kinesisMaximumMessageSize = 1024 * 1024;      // 1 MB
        final int layoutOverhead            = 1;                // newline after message
        final int partitionKeySize          = 4;                // "test"

        final int maxMessageSize            =  kinesisMaximumMessageSize - (layoutOverhead + partitionKeySize);
        final String bigMessage             =  StringUtil.repeat('A', maxMessageSize);

        initialize("TestKinesisAppender/testMaximumMessageSize.properties");
        logger.debug("this message triggers writer configuration");

        assertFalse("max message size",             appender.isMessageTooLarge(new LogMessage(System.currentTimeMillis(), bigMessage)));
        assertTrue("bigger than max message size",  appender.isMessageTooLarge(new LogMessage(System.currentTimeMillis(), bigMessage + "1")));
    }


    @Test
    public void testWriterWithExistingStream() throws Exception
    {
        initialize("TestKinesisAppender/testWriterWithExistingStream.properties");

        MockKinesisClient mockClient = new MockKinesisClient();

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        logger.debug("example message");
        mockClient.allowWriterThread();

        assertEquals("describeStream: invocation count",        1,          mockClient.describeStreamInvocationCount);
        assertEquals("describeStream: stream name",             "argle",    mockClient.describeStreamStreamName);
        assertEquals("createStream: invocation count",          0,          mockClient.createStreamInvocationCount);
        assertEquals("putRecords: invocation count",            1,          mockClient.putRecordsInvocationCount);
        assertEquals("putRecords: source record count",         1,          mockClient.putRecordsSourceRecords.size());
        assertEquals("putRecords: source record partition key", "bargle",   mockClient.putRecordsSourceRecords.get(0).getPartitionKey());
        assertEquals("putRecords: source record content",       "example message\n",
                                                                new String(
                                                                    BinaryUtils.copyAllBytesFrom(mockClient.putRecordsSourceRecords.get(0).getData()),
                                                                    "UTF-8"));
    }


    @Test
    public void testWriterWithNewStream() throws Exception
    {
        initialize("TestKinesisAppender/testWriterWithNewStream.properties");

        MockKinesisClient mockClient = new MockKinesisClient();

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        logger.debug("example message");
        mockClient.allowWriterThread();

        // writer calls describeStream once to see if stream exists, a second time
        // to verify that it's active -- could perhaps combine those calls?

        assertEquals("describeStream: invocation count",        3,          mockClient.describeStreamInvocationCount);
        assertEquals("describeStream: stream name",             "foo",      mockClient.describeStreamStreamName);
        assertEquals("createStream: invocation count",          1,          mockClient.createStreamInvocationCount);
        assertEquals("createStream: stream name",               "foo",      mockClient.createStreamStreamName);
        assertEquals("putRecords: invocation count",            1,          mockClient.putRecordsInvocationCount);
        assertEquals("putRecords: source record count",         1,          mockClient.putRecordsSourceRecords.size());
        assertEquals("putRecords: source record partition key", "bar",      mockClient.putRecordsSourceRecords.get(0).getPartitionKey());
        assertEquals("putRecords: source record content",       "example message\n",
                                                                new String(
                                                                    BinaryUtils.copyAllBytesFrom(mockClient.putRecordsSourceRecords.get(0).getData()),
                                                                    "UTF-8"));
    }


    @Test
    public void testInvalidStreamName() throws Exception
    {
        initialize("TestKinesisAppender/testInvalidStreamName.properties");

        MockKinesisClient mockClient = new MockKinesisClient();

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        logger.debug("this triggers writer creation");

        String initializationMessage = waitForInitialization();

        assertTrue("initialization message indicates invalid stream name (was: " + initializationMessage + ")",
                   initializationMessage.contains("invalid stream name"));
        assertTrue("initialization message contains invalid name (was: " + initializationMessage + ")",
                   initializationMessage.contains("helpme!"));

        assertEquals("describeStream: should not be invoked", 0, mockClient.describeStreamInvocationCount);
    }


    @Test
    public void testInvalidPartitionKey() throws Exception
    {
        initialize("TestKinesisAppender/testInvalidPartitionKey.properties");

        MockKinesisClient mockClient = new MockKinesisClient();

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        logger.debug("this triggers writer creation");

        String initializationMessage = waitForInitialization();

        assertTrue("initialization message indicates invalid partition key (was: " + initializationMessage + ")",
                   initializationMessage.contains("invalid partition key"));

        assertEquals("describeStream: should not be invoked", 0, mockClient.describeStreamInvocationCount);
    }


    @Test
    public void testRateLimitedDescribe() throws Exception
    {
        initialize("TestKinesisAppender/testRateLimitedDescribe.properties");

        MockKinesisClient mockClient = new MockKinesisClient()
        {
            @Override
            protected DescribeStreamResult describeStream(DescribeStreamRequest request)
            {
                if (describeStreamInvocationCount < 3)
                    throw new LimitExceededException("uh oh!");
                else
                    return super.describeStream(request);
            }
        };

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        logger.debug("example message");
        mockClient.allowWriterThread();

        // even with exceptions we should be able to write our message

        assertEquals("describeStream: invocation count",        3,          mockClient.describeStreamInvocationCount);
        assertEquals("describeStream: stream name",             "argle",    mockClient.describeStreamStreamName);
        assertEquals("createStream: invocation count",          0,          mockClient.createStreamInvocationCount);
        assertEquals("putRecords: invocation count",            1,          mockClient.putRecordsInvocationCount);
        assertEquals("putRecords: source record count",         1,          mockClient.putRecordsSourceRecords.size());
    }


    @Test
    public void testInitializationErrorHandling() throws Exception
    {
        initialize("TestKinesisAppender/testInitializationErrorHandling.properties");

        // the mock client will report an error on every third record
        MockKinesisClient mockClient = new MockKinesisClient()
        {
            @Override
            protected CreateStreamResult createStream(CreateStreamRequest request)
            {
                throw new TestingException("not now, not ever");
            }
        };

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        // first message triggers writer creation

        logger.debug("example message");
        waitForInitialization();

        AbstractLogWriter writer = (AbstractLogWriter)appender.getWriter();
        MessageQueue messageQueue = appender.getMessageQueue();

        assertEquals("describeStream: invocation count",    1,          mockClient.describeStreamInvocationCount);
        assertEquals("describeStream: stream name",         "foo",      mockClient.describeStreamStreamName);
        assertEquals("createStream: invocation count",      1,          mockClient.createStreamInvocationCount);
        assertEquals("createStream: stream name",           "foo",      mockClient.createStreamStreamName);

        assertTrue("initialization message non-blank",      ! writer.getInitializationMessage().equals(""));
        assertEquals("initialization error class",          TestingException.class,     writer.getInitializationException().getClass());
        assertEquals("initialization error message",        "not now, not ever",        writer.getInitializationException().getMessage());

        assertEquals("message queue set to discard all",    0,                          messageQueue.getDiscardThreshold());
        assertEquals("message queue set to discard all",    DiscardAction.oldest,       messageQueue.getDiscardAction());
        assertEquals("messages in queue (initial)",         1,                          messageQueue.toList().size());

        // trying to log another message should clear the queue

        logger.info("message two");
        assertEquals("messages in queue (second try)",      0,                          messageQueue.toList().size());
    }


    @Test
    public void testMessageErrorHandling() throws Exception
    {
        initialize("TestKinesisAppender/testMessageErrorHandling.properties");

        // the mock client will report an error on every third record
        MockKinesisClient mockClient = new MockKinesisClient()
        {
            @Override
            public PutRecordsResult putRecords(PutRecordsRequest request)
            {
                int failedRecordCount = 0;
                List<PutRecordsResultEntry> resultRecords = new ArrayList<PutRecordsResultEntry>();
                for (int ii = 0 ; ii < request.getRecords().size() ; ii++)
                {
                    PutRecordsResultEntry resultRecord = new PutRecordsResultEntry();
                    resultRecords.add(resultRecord);
                    if ((ii % 3) == 1)
                    {
                        failedRecordCount++;
                        resultRecord.setErrorCode("anything, really");
                    }
                }
                return new PutRecordsResult()
                       .withFailedRecordCount(Integer.valueOf(failedRecordCount))
                       .withRecords(resultRecords);
            }
        };

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        for (int ii = 0 ; ii < 10 ; ii++)
        {
            logger.debug("message " + ii);
        }

        mockClient.allowWriterThread();

        assertEquals("first batch, number of successful messages", 7, mockClient.putRecordsSuccesses.size());
        assertEquals("first batch, number of failed messages",     3, mockClient.putRecordsFailures.size());

        PutRecordsRequestEntry savedFailure1 = mockClient.putRecordsFailures.get(0);
        PutRecordsRequestEntry savedFailure2 = mockClient.putRecordsFailures.get(1);
        PutRecordsRequestEntry savedFailure3 = mockClient.putRecordsFailures.get(2);

        mockClient.allowWriterThread();

        assertEquals("second batch, number of successful messages", 2, mockClient.putRecordsSuccesses.size());
        assertEquals("second batch, number of failed messages",     1, mockClient.putRecordsFailures.size());

        assertTrue("first failure is now first success",
                   Arrays.equals(
                       BinaryUtils.copyAllBytesFrom(savedFailure1.getData()),
                       BinaryUtils.copyAllBytesFrom(mockClient.putRecordsSuccesses.get(0).getData())));
        assertTrue("third failure is now second success (second failure failed again)",
                   Arrays.equals(
                       BinaryUtils.copyAllBytesFrom(savedFailure3.getData()),
                       BinaryUtils.copyAllBytesFrom(mockClient.putRecordsSuccesses.get(1).getData())));


        mockClient.allowWriterThread();

        assertEquals("third batch, number of successful messages", 1, mockClient.putRecordsSuccesses.size());
        assertEquals("third batch, number of failed messages",     0, mockClient.putRecordsFailures.size());

        assertTrue("second original failure is now a success",
                   Arrays.equals(
                       BinaryUtils.copyAllBytesFrom(savedFailure2.getData()),
                       BinaryUtils.copyAllBytesFrom(mockClient.putRecordsSuccesses.get(0).getData())));
    }


    @Test
    public void testUncaughtExceptionHandling() throws Exception
    {
        initialize("TestKinesisAppender/testUncaughtExceptionHandling.properties");

        // note that we will be running the writer on a separate thread

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(new ThrowingWriterFactory<KinesisWriterConfig>());

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
    public void testDiscardOldest() throws Exception
    {
        initialize("TestKinesisAppender/testDiscardOldest.properties");

        // this is a dummy client: never actually run the writer thread, but
        // need to test the real writer
        MockKinesisClient mockClient = new MockKinesisClient()
        {
            @Override
            public PutRecordsResult putRecords(PutRecordsRequest request)
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
        initialize("TestKinesisAppender/testDiscardNewest.properties");

        // this is a dummy client: never actually run the writer thread, but
        // need to test the real writer
        MockKinesisClient mockClient = new MockKinesisClient()
        {
            @Override
            public PutRecordsResult putRecords(PutRecordsRequest request)
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
        initialize("TestKinesisAppender/testDiscardNone.properties");

        // this is a dummy client: never actually run the writer thread, but
        // need to test the real writer
        MockKinesisClient mockClient = new MockKinesisClient()
        {
            @Override
            public PutRecordsResult putRecords(PutRecordsRequest request)
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
        initialize("TestKinesisAppender/testReconfigureDiscardProperties.properties");

        // another test where we don't actually do anything but need to verify actual writer

        appender.setThreadFactory(new NullThreadFactory());
        appender.setWriterFactory(new MockKinesisClient().newWriterFactory());

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
