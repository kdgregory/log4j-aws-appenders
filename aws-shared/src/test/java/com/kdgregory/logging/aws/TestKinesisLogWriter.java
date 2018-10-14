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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.lang.StringUtil;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.*;
import com.amazonaws.util.BinaryUtils;

import com.kdgregory.logging.aws.kinesis.KinesisWriterStatistics;
import com.kdgregory.logging.aws.kinesis.KinesisLogWriter;
import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;
import com.kdgregory.logging.aws.kinesis.KinesisWriterFactory;
import com.kdgregory.logging.aws.testhelpers.TestingException;
import com.kdgregory.logging.aws.testhelpers.kinesis.MockKinesisClient;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.common.util.MessageQueue;


/**
 *  Performs mock-client testing of the Kinesis writer.
 */
public class TestKinesisLogWriter
extends AbstractLogWriterTest<KinesisLogWriter,KinesisWriterConfig,KinesisWriterStatistics,AmazonKinesis>
{
    private final static String DEFAULT_STREAM_NAME     = "argle";
    private final static String DEFAULT_PARTITION_KEY   = "bargle";

//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

    /**
     *  Rather than re-create each time, we initialize in setUp(), replace in
     *  tests that need to do so.
     */
    private MockKinesisClient mock;


    /**
     *  Creates a writer using the current mock client, waiting for it to be initialized.
     */
    private void createWriter()
    throws Exception
    {
        createWriter(mock.newWriterFactory());
    }


    // the following variable and function are used by testStaticClientFactory

    private static MockKinesisClient staticFactoryMock;

    public static AmazonKinesis createMockClient()
    {
        staticFactoryMock = new MockKinesisClient(DEFAULT_STREAM_NAME);
        return staticFactoryMock.createClient();
    }

//----------------------------------------------------------------------------
//  JUnit scaffolding
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        config = new KinesisWriterConfig(
            DEFAULT_STREAM_NAME,
            DEFAULT_PARTITION_KEY,
            DEFAULT_PARTITION_KEY.length(),
            100,                            // batchDelay
            10000,                          // discardThreshold
            DiscardAction.oldest,
            null,                           // clientFactoryMethod
            null,                           // clientEndpoint
            false,                          // autoCreate
            0,                              // shardCount,
            null);                          // retentionPeriod

        stats = new KinesisWriterStatistics();

        mock = new MockKinesisClient(DEFAULT_STREAM_NAME);

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
        config = new KinesisWriterConfig(
            DEFAULT_STREAM_NAME,
            DEFAULT_PARTITION_KEY,
            DEFAULT_PARTITION_KEY.length(),
            123,                            // batchDelay
            456,                          // discardThreshold
            DiscardAction.newest,
            null,                           // clientFactoryMethod
            null,                           // clientEndpoint
            false,                          // autoCreate
            0,                              // shardCount,
            null);                          // retentionPeriod

        writer = new KinesisLogWriter(config, stats, internalLogger, dummyClientFactory);
        messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        // the writer uses the config object for most of its configuration,
        // so we just look for the pieces that it exposes or passes on

        assertEquals("writer batch delay",                  123L,                       writer.getBatchDelay());
        assertEquals("message queue discard policy",        DiscardAction.newest,       messageQueue.getDiscardAction());
        assertEquals("message queue discard threshold",     456,                        messageQueue.getDiscardThreshold());
        assertEquals("stats: actual stream name",           DEFAULT_STREAM_NAME,        stats.getActualStreamName());
    }


    @Test
    public void testWriterWithExistingStream() throws Exception
    {
        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        assertEquals("describeStream: invocation count",            1,                      mock.describeStreamInvocationCount);
        assertEquals("describeStream: stream name",                 DEFAULT_STREAM_NAME,    mock.describeStreamStreamName);
        assertEquals("createStream: invocation count",              0,                      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,                      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords: invocation count",                1,                      mock.putRecordsInvocationCount);
        assertEquals("putRecords: source record count",             1,                      mock.putRecordsSourceRecords.size());
        assertEquals("putRecords: source record partition key",     DEFAULT_PARTITION_KEY,  mock.putRecordsSourceRecords.get(0).getPartitionKey());
        assertEquals("putRecords: source record content",           "message one",
                                                                    new String(
                                                                        BinaryUtils.copyAllBytesFrom(mock.putRecordsSourceRecords.get(0).getData()),
                                                                        "UTF-8"));

        assertEquals("stats: actual stream name",                   DEFAULT_STREAM_NAME,    stats.getActualStreamName());
        assertEquals("stats: sent message count",                   1,                      stats.getMessagesSent());

        assertEquals("debug message count",                         0,                      internalLogger.debugMessages.size());
        assertEquals("error message count",                         0,                      internalLogger.errorMessages.size());
    }


    @Test
    public void testWriterCreatesStream() throws Exception
    {
        config.autoCreate = true;
        config.shardCount = 3;
        config.retentionPeriod = 48;

        mock = new MockKinesisClient(1);

        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        // writer calls describeStream once to see if stream exists, twice while waiting
        // for it to become active, then once more before calling putRecords

        assertEquals("describeStream: invocation count",            4,                      mock.describeStreamInvocationCount);
        assertEquals("describeStream: stream name",                 DEFAULT_STREAM_NAME,    mock.describeStreamStreamName);
        assertEquals("createStream: invocation count",              1,                      mock.createStreamInvocationCount);
        assertEquals("createStream: stream name",                   DEFAULT_STREAM_NAME,    mock.createStreamStreamName);
        assertEquals("createStream: shard count",                   Integer.valueOf(3),     mock.createStreamShardCount);
        assertEquals("increaseRetentionPeriod invocation count",    1,                      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("increaseRetentionPeriod stream name",         DEFAULT_STREAM_NAME,    mock.increaseRetentionPeriodStreamName);
        assertEquals("increaseRetentionPeriod hours",               Integer.valueOf(48),    mock.increaseRetentionPeriodHours);
        assertEquals("putRecords: invocation count",                1,                      mock.putRecordsInvocationCount);
        assertEquals("putRecords: source record count",             1,                      mock.putRecordsSourceRecords.size());
        assertEquals("putRecords: source record partition key",     DEFAULT_PARTITION_KEY, mock.putRecordsSourceRecords.get(0).getPartitionKey());
        assertEquals("putRecords: source record content",           "message one",
                                                                    new String(
                                                                        BinaryUtils.copyAllBytesFrom(mock.putRecordsSourceRecords.get(0).getData()),
                                                                        "UTF-8"));

        assertEquals("actual stream name, from statistics",         DEFAULT_STREAM_NAME,    stats.getActualStreamName());
        assertEquals("sent message count, from statistics",         1,                      stats.getMessagesSent());

        assertEquals("debug message count",                         1,                      internalLogger.debugMessages.size());
        assertRegex("debug message indicates creating stream",      ".*creat.*stream.*",    internalLogger.debugMessages.get(0));
        assertEquals("error message count",                         0,                      internalLogger.errorMessages.size());
    }


    @Test
    public void testMissingStreamNoAutoCreate() throws Exception
    {
        mock = new MockKinesisClient();

        createWriter();

        assertEquals("describeStream: invocation count",            1,                      mock.describeStreamInvocationCount);
        assertEquals("describeStream: stream name",                 DEFAULT_STREAM_NAME,    mock.describeStreamStreamName);
        assertEquals("createStream: invocation count",              0,                      mock.createStreamInvocationCount);
        assertEquals("putRecords: invocation count",                0,                      mock.putRecordsInvocationCount);

        String initializationMessage = stats.getLastErrorMessage();

        assertTrue("initialization message indicates invalid stream name (was: " + initializationMessage + ")",
                   initializationMessage.contains("does not exist"));
        assertTrue("initialization message contains stream name (was: " + initializationMessage + ")",
                   initializationMessage.contains(DEFAULT_STREAM_NAME));

        assertEquals("debug message count",                         0,                      internalLogger.debugMessages.size());
        assertEquals("error message count",                         1,                      internalLogger.errorMessages.size());
        assertRegex("error message indicates problem",              ".*auto-create not enabled",
                                                                    internalLogger.errorMessages.get(0));
    }


    @Test
    public void testInvalidStreamName() throws Exception
    {
        config.streamName = "I'm No Good!";

        createWriter();

        assertEquals("describeStream: invocation count",            0,                      mock.describeStreamInvocationCount);
        assertEquals("createStream: invocation count",              0,                      mock.createStreamInvocationCount);
        assertEquals("putRecords: invocation count",                0,                      mock.putRecordsInvocationCount);

        String initializationMessage = stats.getLastErrorMessage();

        assertTrue("initialization message indicates invalid stream name (was: " + initializationMessage + ")",
                   initializationMessage.contains("invalid stream name"));
        assertTrue("initialization message contains invalid name (was: " + initializationMessage + ")",
                   initializationMessage.contains(config.streamName));

        assertEquals("debug message count",                         0,                      internalLogger.debugMessages.size());
        assertEquals("error message count",                         1,                      internalLogger.errorMessages.size());
        assertRegex("error message",                                ".*invalid.*stream.*",  internalLogger.errorMessages.get(0));
    }


    @Test
    public void testInvalidPartitionKey() throws Exception
    {
        config.partitionKey = StringUtil.repeat('X', 300);

        createWriter();

        assertEquals("describeStream: invocation count",            0,                      mock.describeStreamInvocationCount);
        assertEquals("createStream: invocation count",              0,                      mock.createStreamInvocationCount);
        assertEquals("putRecords: invocation count",                0,                      mock.putRecordsInvocationCount);

        String initializationMessage = stats.getLastErrorMessage();

        assertTrue("initialization message indicates invalid partition key (was: " + initializationMessage + ")",
                   initializationMessage.contains("invalid partition key"));

        assertEquals("debug message count",                         0,                      internalLogger.debugMessages.size());
        assertEquals("error message count",                         1,                      internalLogger.errorMessages.size());
        assertRegex("error message",                                ".*invalid.*key.*",     internalLogger.errorMessages.get(0));
    }


    @Test
    public void testRateLimitedDescribe() throws Exception
    {
        mock = new MockKinesisClient(DEFAULT_STREAM_NAME)
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

        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        assertEquals("describeStream: invocation count",            3,                      mock.describeStreamInvocationCount);
        assertEquals("createStream: invocation count",              0,                      mock.createStreamInvocationCount);
        assertEquals("putRecords: invocation count",                1,                      mock.putRecordsInvocationCount);
    }


    @Test
    public void testRandomPartitionKey() throws Exception
    {
        config.partitionKey = "";

        final Set<String> partitionKeys = new HashSet<String>();
        mock = new MockKinesisClient(DEFAULT_STREAM_NAME)
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

        createWriter();

        for (int ii = 0 ; ii < 10 ; ii++)
        {
            writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        }

        mock.allowWriterThread();

        // it's possibly but unlikely for this to fail -- we could randomly get same value
        assertEquals("number of partition keys", 10, partitionKeys.size());

        for (String key : partitionKeys)
        {
            assertRegex("partition key is some number of digits (was: " + key + ")", "\\d+", key);
        }
    }


    @Test
    public void testInitializationErrorHandling() throws Exception
    {
        mock = new MockKinesisClient(DEFAULT_STREAM_NAME)
        {
            @Override
            protected DescribeStreamResult describeStream(DescribeStreamRequest request)
            {
                throw new TestingException("not now, not ever");
            }
        };

        createWriter();

        assertEquals("describeStream: invocation count",            1,                          mock.describeStreamInvocationCount);
        assertEquals("createStream: invocation count",              0,                          mock.createStreamInvocationCount);

        assertEquals("message queue set to discard all",            0,                          messageQueue.getDiscardThreshold());
        assertEquals("message queue set to discard all",            DiscardAction.oldest,       messageQueue.getDiscardAction());

        assertRegex("stats: error message",                         "unable to configure.*",    stats.getLastErrorMessage());
        assertEquals("stats: exception",                            TestingException.class,     stats.getLastError().getClass());
        assertEquals("stats: exception message",                    "not now, not ever",        stats.getLastError().getMessage());
        assertTrue("stats: exception trace",                                                    stats.getLastErrorStacktrace().size() > 0);
        assertNotNull("stats: exception timestamp",                                             stats.getLastErrorTimestamp());

        assertEquals("log: debug message count",                    0,                          internalLogger.debugMessages.size());
        assertEquals("log: error message count",                    1,                          internalLogger.errorMessages.size());
        assertRegex("log: error message",                           "unable to configure.*",    internalLogger.errorMessages.get(0));
    }


    @Test
    public void testBatchErrorHandling() throws Exception
    {
        mock = new MockKinesisClient(DEFAULT_STREAM_NAME)
        {
            @Override
            public PutRecordsResult putRecords(PutRecordsRequest request)
            {
                throw new TestingException("I don't wanna do the work");
            }
        };

        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));

        // we need to call twice to avoid race conditions between setting the error in statistics and
        // reading those statistics from the main thread

        mock.allowWriterThread();
        mock.allowWriterThread();

        assertEquals("putRecords: invocation count",            2,                              mock.putRecordsInvocationCount);
        assertEquals("putRecords: number of messages",          1,                              mock.putRecordsSourceRecords.size());

        assertEquals("stats: number of messages sent",          0,                              stats.getMessagesSent());
        assertRegex("stats: error message",                    "failed to send.*",                                    stats.getLastErrorMessage());
        assertNotNull("stats: error timestamp",                                                 stats.getLastErrorTimestamp());
        assertEquals("stats: exception retained",               TestingException.class,         stats.getLastError().getClass());
        assertEquals("stats: exception message",                "I don't wanna do the work",    stats.getLastError().getMessage());
        assertTrue("stats: error stacktrace",                                                   stats.getLastErrorStacktrace().size() > 0);
        assertTrue("message queue still accepts messages",                                      messageQueue.getDiscardThreshold() > 0);

        assertEquals("log: debug message count",                0,                              internalLogger.debugMessages.size());
        assertEquals("log: error message count",                2,                              internalLogger.errorMessages.size());
        assertRegex("log: error message (0)",                   "failed to send.*",             internalLogger.errorMessages.get(0));
        assertEquals("log: exception (0)",                      TestingException.class,         internalLogger.errorExceptions.get(0).getClass());
        assertRegex("log: error message (1)",                   "failed to send.*",             internalLogger.errorMessages.get(1));
        assertEquals("log: exception (1)",                      TestingException.class,         internalLogger.errorExceptions.get(1).getClass());

        // the background thread will try to assemble another batch right away, so we can't examine
        // the message queue; instead we'll wait for the writer to call PutRecords again

        mock.allowWriterThread();

        assertEquals("putRecords called again",                 3,                              mock.putRecordsInvocationCount);
        assertEquals("putRecords: number of messages",          1,                              mock.putRecordsSourceRecords.size());
    }


    @Test
    public void testRecordErrorHandling() throws Exception
    {
        // the mock client will report an error on every third record
        mock = new MockKinesisClient(DEFAULT_STREAM_NAME)
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

        createWriter();

        for (int ii = 0 ; ii < 10 ; ii++)
        {
            writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        }

        mock.allowWriterThread();

        assertEquals("first batch, putRecords invocation count",        1,      mock.putRecordsInvocationCount);
        assertEquals("first batch, number of successful messages",      7,      mock.putRecordsSuccesses.size());
        assertEquals("first batch, number of failed messages",          3,      mock.putRecordsFailures.size());
        assertEquals("first batch, messages from statistics",           7,      stats.getMessagesSent());

        PutRecordsRequestEntry savedFailure1 = mock.putRecordsFailures.get(0);
        PutRecordsRequestEntry savedFailure2 = mock.putRecordsFailures.get(1);
        PutRecordsRequestEntry savedFailure3 = mock.putRecordsFailures.get(2);

        mock.allowWriterThread();

        assertEquals("second batch, putRecords invocation count",       2,      mock.putRecordsInvocationCount);
        assertEquals("second batch, number of successful messages",     2,      mock.putRecordsSuccesses.size());
        assertEquals("second batch, number of failed messages",         1,      mock.putRecordsFailures.size());
        assertEquals("first batch, messages from statistics",           9,      stats.getMessagesSent());

        assertTrue("first failure is now first success",
                   Arrays.equals(
                       BinaryUtils.copyAllBytesFrom(savedFailure1.getData()),
                       BinaryUtils.copyAllBytesFrom(mock.putRecordsSuccesses.get(0).getData())));
        assertTrue("third failure is now second success (second failure failed again)",
                   Arrays.equals(
                       BinaryUtils.copyAllBytesFrom(savedFailure3.getData()),
                       BinaryUtils.copyAllBytesFrom(mock.putRecordsSuccesses.get(1).getData())));

        mock.allowWriterThread();

        assertEquals("third batch, putRecords invocation count",        3,      mock.putRecordsInvocationCount);
        assertEquals("third batch, number of successful messages",      1,      mock.putRecordsSuccesses.size());
        assertEquals("third batch, number of failed messages",          0,      mock.putRecordsFailures.size());
        assertEquals("first batch, messages from statistics",           10,     stats.getMessagesSent());

        assertTrue("second original failure is now a success",
                   Arrays.equals(
                       BinaryUtils.copyAllBytesFrom(savedFailure2.getData()),
                       BinaryUtils.copyAllBytesFrom(mock.putRecordsSuccesses.get(0).getData())));
    }


    @Test
    public void testDiscardOldest() throws Exception
    {
        config.discardAction = DiscardAction.oldest;
        config.discardThreshold = 10;

        // this test doesn't need a background thread running

        writer = new KinesisLogWriter(config, stats, internalLogger, dummyClientFactory);
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

        writer = new KinesisLogWriter(config, stats, internalLogger, dummyClientFactory);
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

        writer = new KinesisLogWriter(config, stats, internalLogger, dummyClientFactory);
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

        writer = new KinesisLogWriter(config, stats, internalLogger, dummyClientFactory);
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
        final int numMessages = 750;

        createWriter();
        for (int ii = 0 ; ii < numMessages ; ii++)
        {
            writer.addMessage(new LogMessage(System.currentTimeMillis(), testMessage));
        }

        // first batch should stop at 500

        mock.allowWriterThread();

        assertEquals("describeStream: invocation count",            1,                      mock.describeStreamInvocationCount);
        assertEquals("createStream: invocation count",              0,                      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,                      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords: invocation count",                1,                      mock.putRecordsInvocationCount);
        assertEquals("putRecords: source record count",             500,                    mock.putRecordsSourceRecords.size());
        assertEquals("putRecords: source record partition key",     DEFAULT_PARTITION_KEY,  mock.putRecordsSourceRecords.get(0).getPartitionKey());
        assertEquals("putRecords: source record content",           testMessage,
                                                                    new String(
                                                                        BinaryUtils.copyAllBytesFrom(mock.putRecordsSourceRecords.get(499).getData()),
                                                                        "UTF-8"));

        // second batch batch should pick up remaining records

        mock.allowWriterThread();

        assertEquals("describeStream: invocation count",            1,                      mock.describeStreamInvocationCount);
        assertEquals("createStream: invocation count",              0,                      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,                      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords: invocation count",                2,                      mock.putRecordsInvocationCount);
        assertEquals("putRecords: source record count",             250,                    mock.putRecordsSourceRecords.size());
        assertEquals("putRecords: source record partition key",     DEFAULT_PARTITION_KEY,  mock.putRecordsSourceRecords.get(0).getPartitionKey());
        assertEquals("putRecords: source record content",           testMessage,
                                                                    new String(
                                                                        BinaryUtils.copyAllBytesFrom(mock.putRecordsSourceRecords.get(249).getData()),
                                                                        "UTF-8"));

        // this sleep is a hack to enable the writer thread to update stats
        Thread.sleep(50);

        assertEquals("total messages sent, from statistics",        numMessages,            stats.getMessagesSent());

        assertEquals("debug message count",                         0,                      internalLogger.debugMessages.size());
        assertEquals("error message count",                         0,                      internalLogger.errorMessages.size());
    }

    @Test
    public void testSizeBasedBatching() throws Exception
    {
        // don't let discard threshold get in the way of the test
        config.discardThreshold = Integer.MAX_VALUE;
        config.discardAction = DiscardAction.none;

        // increasing delay because it will take time to create the messages
        config.batchDelay = 300;

        final String testMessage = StringUtil.randomAlphaString(32768, 32768);
        final int numMessages = 200;

        final int expected1stBatchCount = (5 * 1024 * 1024) / (testMessage.length() + DEFAULT_PARTITION_KEY.length());
        final int expected2ndBatchCount = numMessages - expected1stBatchCount;

        createWriter();
        for (int ii = 0 ; ii < numMessages ; ii++)
        {
            writer.addMessage(new LogMessage(System.currentTimeMillis(), testMessage));
        }

        // first batch should stop just under 5 megabytes -- including record overhead

        mock.allowWriterThread();

        assertEquals("describeStream: invocation count",            1,                      mock.describeStreamInvocationCount);
        assertEquals("createStream: invocation count",              0,                      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,                      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords: invocation count",                1,                      mock.putRecordsInvocationCount);
        assertEquals("putRecords: source record count",             expected1stBatchCount,  mock.putRecordsSourceRecords.size());
        assertEquals("putRecords: source record partition key",     DEFAULT_PARTITION_KEY,  mock.putRecordsSourceRecords.get(0).getPartitionKey());
        assertEquals("putRecords: source record content",           testMessage,
                                                                    new String(
                                                                        BinaryUtils.copyAllBytesFrom(mock.putRecordsSourceRecords.get(expected1stBatchCount - 1).getData()),
                                                                        "UTF-8"));

        // second batch batch should pick up remaining records

        mock.allowWriterThread();

        assertEquals("describeStream: invocation count",            1,                      mock.describeStreamInvocationCount);
        assertEquals("createStream: invocation count",              0,                      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,                      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords: invocation count",                2,                      mock.putRecordsInvocationCount);
        assertEquals("putRecords: source record count",             expected2ndBatchCount,  mock.putRecordsSourceRecords.size());
        assertEquals("putRecords: source record partition key",     DEFAULT_PARTITION_KEY,  mock.putRecordsSourceRecords.get(0).getPartitionKey());
        assertEquals("putRecords: source record content",           testMessage,
                                                                    new String(
                                                                        BinaryUtils.copyAllBytesFrom(mock.putRecordsSourceRecords.get(expected2ndBatchCount - 1).getData()),
                                                                        "UTF-8"));

        // this sleep is a hack to enable the writer thread to update stats
        Thread.sleep(50);

        assertEquals("total messages sent, from statistics",        numMessages,            stats.getMessagesSent());

        assertEquals("debug message count",                         0,                      internalLogger.debugMessages.size());
        assertEquals("error message count",                         0,                      internalLogger.errorMessages.size());
    }


    @Test
    public void testStaticClientFactory() throws Exception
    {
        config.clientFactoryMethod = this.getClass().getName() + ".createMockClient";

        // we don't want the default mock client

        createWriter(new KinesisWriterFactory());

        assertTrue("writer successfully initialized",                                       writer.isInitializationComplete());
        assertNotNull("factory called (local flag)",                                        staticFactoryMock);

        assertEquals("describeStream: invocation count",        1,                          staticFactoryMock.describeStreamInvocationCount);
        assertEquals("createStream: invocation count",          0,                          staticFactoryMock.createStreamInvocationCount);
        assertEquals("putRecords: invocation count",            0,                          staticFactoryMock.putRecordsInvocationCount);

        assertNull("stats: no initialization message",                                      stats.getLastErrorMessage());
        assertNull("stats: no initialization error",                                        stats.getLastError());
        assertEquals("stats: actual stream name",               DEFAULT_STREAM_NAME,        stats.getActualStreamName());

        assertRegex("log: debug message indicating factory",    ".*created client from factory.*" + getClass().getName() + ".*",
                                                                internalLogger.debugMessages.get(0));
    }
}
