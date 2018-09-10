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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.LogLog;

import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.test.StringAsserts;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.CreateStreamRequest;
import com.amazonaws.services.kinesis.model.CreateStreamResult;
import com.amazonaws.services.kinesis.model.DescribeStreamRequest;
import com.amazonaws.services.kinesis.model.DescribeStreamResult;
import com.amazonaws.services.kinesis.model.LimitExceededException;
import com.amazonaws.services.kinesis.model.PutRecordsRequest;
import com.amazonaws.services.kinesis.model.PutRecordsRequestEntry;
import com.amazonaws.services.kinesis.model.PutRecordsResult;
import com.amazonaws.services.kinesis.model.PutRecordsResultEntry;
import com.amazonaws.util.BinaryUtils;

import com.kdgregory.aws.logwriters.common.DiscardAction;
import com.kdgregory.aws.logwriters.common.LogMessage;
import com.kdgregory.aws.logwriters.internal.AbstractLogWriter;
import com.kdgregory.aws.logwriters.internal.DefaultThreadFactory;
import com.kdgregory.aws.logwriters.internal.MessageQueue;
import com.kdgregory.aws.logwriters.kinesis.KinesisAppenderStatistics;
import com.kdgregory.aws.logwriters.kinesis.KinesisLogWriter;
import com.kdgregory.aws.logwriters.kinesis.KinesisWriterFactory;
import com.kdgregory.log4j.testhelpers.InlineThreadFactory;
import com.kdgregory.log4j.testhelpers.NullThreadFactory;
import com.kdgregory.log4j.testhelpers.TestingException;
import com.kdgregory.log4j.testhelpers.aws.kinesis.MockKinesisClient;
import com.kdgregory.log4j.testhelpers.aws.kinesis.MockKinesisWriterFactory;
import com.kdgregory.log4j.testhelpers.aws.kinesis.TestableKinesisAppender;

/**
 *  These tests exercise the interaction between the appender and CloudWatch, via
 *  an actual writer. To do that, they mock out the CloudWatch client. Most of
 *  these tests spin up an actual writer thread, so must coordinate interaction
 *  between that thread and the test (main) thread.
 */
public class TestKinesisLogWriter
{
    private Logger logger;
    private TestableKinesisAppender appender;


    private void initialize(String propsName)
    throws Exception
    {
        URL config = ClassLoader.getSystemResource(propsName);
        assertNotNull("was able to retrieve config", config);
        PropertyConfigurator.configure(config);

        logger = Logger.getLogger(getClass());

        Logger rootLogger = Logger.getRootLogger();
        appender = (TestableKinesisAppender)rootLogger.getAppender("default");

        appender.setThreadFactory(new InlineThreadFactory());
        appender.setWriterFactory(new MockKinesisWriterFactory(appender));
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

    private static MockKinesisClient staticFactoryMock = null;

    public static AmazonKinesis createMockClient()
    {
        staticFactoryMock = new MockKinesisClient();
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
        appender.close();
        LogLog.setQuietMode(false);
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

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

        assertEquals("actual stream name, from statistics",     "argle",    appender.getAppenderStatistics().getActualStreamName());
        assertEquals("sent message count, from statistics",     1,          appender.getAppenderStatistics().getMessagesSent());
    }


    @Test
    public void testWriterCreatesStream() throws Exception
    {
        initialize("TestKinesisAppender/testWriterCreatesStream.properties");

        MockKinesisClient mockClient = new MockKinesisClient();

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        logger.debug("example message");
        mockClient.allowWriterThread();

        // writer calls describeStream once to see if stream exists, a second time
        // to verify that it's active -- could perhaps combine those calls?

        assertEquals("describeStream: invocation count",        3,          mockClient.describeStreamInvocationCount);
        assertEquals("describeStream: stream name",             "foo-0",    mockClient.describeStreamStreamName);
        assertEquals("createStream: invocation count",          1,          mockClient.createStreamInvocationCount);
        assertEquals("createStream: stream name",               "foo-0",    mockClient.createStreamStreamName);
        assertEquals("putRecords: invocation count",            1,          mockClient.putRecordsInvocationCount);
        assertEquals("putRecords: source record count",         1,          mockClient.putRecordsSourceRecords.size());
        assertEquals("putRecords: source record partition key", "bar",      mockClient.putRecordsSourceRecords.get(0).getPartitionKey());
        assertEquals("putRecords: source record content",       "example message\n",
                                                                new String(
                                                                    BinaryUtils.copyAllBytesFrom(mockClient.putRecordsSourceRecords.get(0).getData()),
                                                                    "UTF-8"));

        assertEquals("actual stream name, from statistics",     "foo-0",    appender.getAppenderStatistics().getActualStreamName());
        assertEquals("sent message count, from statistics",     1,          appender.getAppenderStatistics().getMessagesSent());
    }


    @Test
    public void testMissingStreamNoAutoCreate() throws Exception
    {
        initialize("TestKinesisAppender/testMissingStreamNoAutoCreate.properties");

        MockKinesisClient mockClient = new MockKinesisClient();

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        logger.debug("this triggers writer creation");

        waitForInitialization();
        String initializationMessage = appender.getAppenderStatistics().getLastErrorMessage();

        assertEquals("describeStream: invocation count", 1, mockClient.describeStreamInvocationCount);

        assertTrue("initialization message indicates invalid stream name (was: " + initializationMessage + ")",
                   initializationMessage.contains("does not exist"));
        assertTrue("initialization message contains stream name (was: " + initializationMessage + ")",
                   initializationMessage.contains("foo"));
    }


    @Test
    public void testInvalidStreamName() throws Exception
    {
        initialize("TestKinesisAppender/testInvalidStreamName.properties");

        MockKinesisClient mockClient = new MockKinesisClient();

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        logger.debug("this triggers writer creation");

        waitForInitialization();
        String initializationMessage = appender.getAppenderStatistics().getLastErrorMessage();

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

        waitForInitialization();
        String initializationMessage = appender.getAppenderStatistics().getLastErrorMessage();

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
        String initializationMessage = appender.getAppenderStatistics().getLastErrorMessage();
        Throwable initializationError = appender.getAppenderStatistics().getLastError();

        KinesisLogWriter writer = (KinesisLogWriter)appender.getWriter();
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        assertEquals("describeStream: invocation count",    1,          mockClient.describeStreamInvocationCount);
        assertEquals("describeStream: stream name",         "foo",      mockClient.describeStreamStreamName);
        assertEquals("createStream: invocation count",      1,          mockClient.createStreamInvocationCount);
        assertEquals("createStream: stream name",           "foo",      mockClient.createStreamStreamName);

        assertNotNull("writer still exists",                                            writer);
        assertTrue("initialization message was non-blank",                              ! initializationMessage.equals(""));
        assertEquals("initialization exception retained",   TestingException.class,     initializationError.getClass());
        assertEquals("initialization error message",        "not now, not ever",        initializationError.getMessage());

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
    public void testBatchErrorHandling() throws Exception
    {
        initialize("TestKinesisAppender/testBatchErrorHandling.properties");

        MockKinesisClient mockClient = new MockKinesisClient()
        {
            @Override
            public PutRecordsResult putRecords(PutRecordsRequest request)
            {
                throw new TestingException("add more shards!");
            }
        };

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        logger.debug("a message");

        mockClient.allowWriterThread();

        AbstractLogWriter writer = (AbstractLogWriter)appender.getWriter();
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);
        KinesisAppenderStatistics appenderStats = appender.getAppenderStatistics();
        Throwable lastError = appenderStats.getLastError();

        assertEquals("number of calls to PutRecords",           1,                          mockClient.putRecordsInvocationCount);
        assertNotNull("writer still exists",                                                writer);
        assertEquals("stats reports no messages sent",          0,                          appenderStats.getMessagesSent());
        assertTrue("error message was non-blank",                                           ! appenderStats.getLastErrorMessage().equals(""));
        assertEquals("exception retained",                      TestingException.class,     lastError.getClass());
        assertEquals("exception message",                       "add more shards!",         lastError.getMessage());
        assertTrue("message queue still accepts messages",                                  messageQueue.getDiscardThreshold() > 0);

        // the background thread will try to assemble another batch right away, so we can't examine
        // the message queue; instead we'll wait for the writer to call PutRecords again

        mockClient.allowWriterThread();

        assertEquals("PutRecords called again",                 2,                          mockClient.putRecordsInvocationCount);
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

        KinesisLogWriter writer = (KinesisLogWriter)appender.getWriter();
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);
        List<LogMessage> messages = messageQueue.toList();

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

        KinesisLogWriter writer = (KinesisLogWriter)appender.getWriter();
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);
        List<LogMessage> messages = messageQueue.toList();

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

        KinesisLogWriter writer = (KinesisLogWriter)appender.getWriter();
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);
        List<LogMessage> messages = messageQueue.toList();

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

        KinesisLogWriter writer = (KinesisLogWriter)appender.getWriter();
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

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
        initialize("TestKinesisAppender/testStaticClientFactory.properties");
        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(new KinesisWriterFactory());

        // first message triggers writer creation

        logger.debug("example message");

        waitForInitialization();

        AbstractLogWriter writer = (AbstractLogWriter)appender.getWriter();

        assertNotNull("factory was called to create client",    staticFactoryMock);
        assertNull("no initialization message",                 appender.getAppenderStatistics().getLastErrorMessage());
        assertNull("no initialization error",                   appender.getAppenderStatistics().getLastError());
        assertEquals("factory method called",                   "com.kdgregory.log4j.aws.TestKinesisLogWriter.createMockClient",
                                                                 writer.getClientFactoryUsed());

        // although we should be happy at this point, we'll actually verify that the
        // message got written; assertions copied from testWriterWithExistingStream()

        staticFactoryMock.allowWriterThread();

        assertEquals("describeStream: invocation count",        1,          staticFactoryMock.describeStreamInvocationCount);
        assertEquals("describeStream: stream name",             "argle",    staticFactoryMock.describeStreamStreamName);
        assertEquals("createStream: invocation count",          0,          staticFactoryMock.createStreamInvocationCount);
        assertEquals("putRecords: invocation count",            1,          staticFactoryMock.putRecordsInvocationCount);
        assertEquals("putRecords: source record count",         1,          staticFactoryMock.putRecordsSourceRecords.size());
        assertEquals("putRecords: source record partition key", "bargle",   staticFactoryMock.putRecordsSourceRecords.get(0).getPartitionKey());
        assertEquals("putRecords: source record content",       "example message\n",
                                                                new String(
                                                                    BinaryUtils.copyAllBytesFrom(staticFactoryMock.putRecordsSourceRecords.get(0).getData()),
                                                                    "UTF-8"));
    }


    @Test
    public void testRandomPartitionKey() throws Exception
    {
        initialize("TestKinesisAppender/testRandomPartitionKey.properties");

        final Set<String> partitionKeys = new HashSet<String>();
        MockKinesisClient mockClient = new MockKinesisClient()
        {
            @Override
            protected PutRecordsResult putRecords(PutRecordsRequest request)
            {
                for (PutRecordsRequestEntry entry : request.getRecords())
                {
                    partitionKeys.add(entry.getPartitionKey());
                }
                return super.putRecords(request);
            }
        };

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        for (int ii = 0 ; ii < 10 ; ii++)
        {
            logger.debug("example message");
        }
        mockClient.allowWriterThread();

        // it's possibly but unlikely for this to fail -- we could randomly get same value
        assertEquals("number of partition keys", 10, partitionKeys.size());

        for (String key : partitionKeys)
        {
            StringAsserts.assertRegex("partition key is 8 digits (was: " + key + ")", "\\d{8}", key);
        }
    }
}
