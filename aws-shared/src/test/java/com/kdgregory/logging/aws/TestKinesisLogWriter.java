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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.StringUtil;
import static net.sf.kdgcommons.test.StringAsserts.*;

import com.kdgregory.logging.aws.internal.facade.KinesisFacade;
import com.kdgregory.logging.aws.internal.facade.KinesisFacadeException;
import com.kdgregory.logging.aws.internal.facade.KinesisFacadeException.ReasonCode;
import com.kdgregory.logging.aws.kinesis.KinesisConstants.StreamStatus;
import com.kdgregory.logging.aws.kinesis.KinesisLogWriter;
import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;
import com.kdgregory.logging.aws.kinesis.KinesisWriterStatistics;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.factories.WriterFactory;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.common.util.InternalLogger;
import com.kdgregory.logging.testhelpers.kinesis.MockKinesisFacade;
import com.kdgregory.logging.testhelpers.kinesis.TestableKinesisLogWriter;


/**
 *  Performs mock-client testing of the Kinesis writer.
 */
public class TestKinesisLogWriter
extends AbstractLogWriterTest<KinesisLogWriter,KinesisWriterConfig,KinesisWriterStatistics,Object>
{
    private final static String DEFAULT_STREAM_NAME     = "argle";
    private final static String DEFAULT_PARTITION_KEY   = "bargle";

    private MockKinesisFacade mock;

//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

    /**
     *  Creates a new writer and starts it on a background thread. This uses
     *  the current configuration and mock instance.
     */
    private void createWriter()
    throws Exception
    {
        final KinesisFacade facade = mock.newInstance();
        WriterFactory<KinesisWriterConfig,KinesisWriterStatistics> writerFactory
            = new WriterFactory<KinesisWriterConfig,KinesisWriterStatistics>()
            {
                @Override
                public LogWriter newLogWriter(
                        KinesisWriterConfig passedConfig,
                        KinesisWriterStatistics passedStats,
                        InternalLogger passedLogger)
                {
                    return new TestableKinesisLogWriter(passedConfig, passedStats, passedLogger, facade);
                }
            };

        super.createWriter(writerFactory);
    }


    /**
     *  A convenience function that knows the writer supports a semaphore (so
     *  that we don't need to cast within testcases).
     */
    private void waitForWriterThread()
    throws Exception
    {
        ((TestableKinesisLogWriter)writer).waitForWriterThread();
    }

//----------------------------------------------------------------------------
//  JUnit scaffolding
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        config = new KinesisWriterConfig()
                 .setStreamName(DEFAULT_STREAM_NAME)
                 .setPartitionKey(DEFAULT_PARTITION_KEY)
                 .setBatchDelay(100)
                 .setDiscardThreshold(10000)
                 .setDiscardAction(DiscardAction.oldest);

        stats = new KinesisWriterStatistics();
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
        // the goal here is to verify propagation from the writer to its objects,
        // so we'll explicitly set the fields that we expect to be propagated

        config.setBatchDelay(123)
              .setDiscardThreshold(456)
              .setDiscardAction(DiscardAction.newest);

        mock = new MockKinesisFacade(config);
        createWriter();

        assertTrue("writer is running", writer.isRunning());

        assertEquals("writer batch delay",                          123L,                       writer.getBatchDelay());
        assertEquals("message queue discard policy",                DiscardAction.newest,       messageQueue.getDiscardAction());
        assertEquals("message queue discard threshold",             456,                        messageQueue.getDiscardThreshold());

        assertEquals("stats: actual stream name",                   DEFAULT_STREAM_NAME,        stats.getActualStreamName());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "log writer initialization complete.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testInvalidConfiguration() throws Exception
    {
        // with this test I'm just verifying that we report messages; the tests on
        // config should have exhaustively explored possible error conditions

        config = new KinesisWriterConfig();

        mock = new MockKinesisFacade(config);
        createWriter();

        assertFalse("writer is running", writer.isRunning());

        internalLogger.assertInternalDebugLog(
                            "log writer starting.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog(
                            "configuration error: missing stream name",
                            "configuration error: missing partition key",
                            "log writer failed to initialize.*");
    }


    @Test
    public void testReconfigureDiscardProperties() throws Exception
    {
        config.setDiscardAction(DiscardAction.none);
        config.setDiscardThreshold(123);

        mock = new MockKinesisFacade(config);
        createWriter();

        assertEquals("initial discard threshold",   123,                    messageQueue.getDiscardThreshold());
        assertEquals("initial discard action",      DiscardAction.none,     messageQueue.getDiscardAction());

        writer.setDiscardAction(DiscardAction.newest);
        writer.setDiscardThreshold(456);

        assertEquals("updated discard threshold",   456,                    messageQueue.getDiscardThreshold());
        assertEquals("updated discard action",      DiscardAction.newest,   messageQueue.getDiscardAction());
    }


    @Test
    public void testWaitForStreamReady() throws Exception
    {
        mock = new MockKinesisFacade(config, StreamStatus.UPDATING, StreamStatus.UPDATING, StreamStatus.ACTIVE);
        createWriter();

        assertTrue("writer is running", writer.isRunning());

        assertEquals("retrieveStreamStatus() invocationCount",      3,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              0,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                0,                          mock.putRecordsInvocationCount);

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "log writer initialization complete.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testWaitForStreamReadyTimeout() throws Exception
    {
        mock = new MockKinesisFacade(config, StreamStatus.CREATING);
        createWriter();

        assertFalse("writer is running", writer.isRunning());

        // initial status check, plus 4 until timeout

        assertEquals("retrieveStreamStatus() invocationCount",      5,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              0,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                0,                          mock.putRecordsInvocationCount);

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog(
                        "timeout waiting for stream " + DEFAULT_STREAM_NAME + " to become active",
                        "log writer failed to initialize.*");
    }


    @Test
    public void testCreateStream() throws Exception
    {
        config.setAutoCreate(true);

        // this should be the default value, but let's be explicit
        config.setRetentionPeriod(null);

        mock = new MockKinesisFacade(config, StreamStatus.DOES_NOT_EXIST, StreamStatus.CREATING, StreamStatus.ACTIVE);
        createWriter();

        assertTrue("writer is running", writer.isRunning());

        // retrieveStreamStatus(): one to discover it doesn't exist, two after creation

        assertEquals("retrieveStreamStatus() invocationCount",      3,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              1,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                0,                          mock.putRecordsInvocationCount);

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "creating kinesis stream: " + DEFAULT_STREAM_NAME,
                        "log writer initialization complete.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testCreateStreamTimeout() throws Exception
    {
        config.setAutoCreate(true);

        mock = new MockKinesisFacade(config, StreamStatus.DOES_NOT_EXIST, StreamStatus.CREATING);
        createWriter();

        assertFalse("writer is running", writer.isRunning());

        // initial status check, plus 4 until timeout

        assertEquals("retrieveStreamStatus() invocationCount",      5,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              1,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                0,                          mock.putRecordsInvocationCount);

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "creating kinesis stream: " + DEFAULT_STREAM_NAME);
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog(
                        "timeout waiting for stream " + DEFAULT_STREAM_NAME + " to become active",
                        "log writer failed to initialize.*");
    }


    @Test
    public void testCreateStreamException() throws Exception
    {
        config.setAutoCreate(true);

        final RuntimeException cause = new RuntimeException();
        mock = new MockKinesisFacade(config, StreamStatus.DOES_NOT_EXIST, StreamStatus.CREATING)
        {
            @Override
            public void createStream()
            {
                throw new KinesisFacadeException("unexpected", ReasonCode.THROTTLING, false, cause);
            }
        };
        createWriter();

        assertFalse("writer is running", writer.isRunning());

        // initial status check only

        assertEquals("retrieveStreamStatus() invocationCount",      1,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              1,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                0,                          mock.putRecordsInvocationCount);

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "creating kinesis stream: " + DEFAULT_STREAM_NAME);
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog(
                        "unable to configure stream: " + DEFAULT_STREAM_NAME,
                        "log writer failed to initialize.*");

        assertUltimateCause("reported underlying exception", cause, internalLogger.errorExceptions.get(0));
    }


    @Test
    public void testCreateStreamWithRetentionPeriod() throws Exception
    {
        config.setAutoCreate(true);
        config.setRetentionPeriod(48);

        mock = new MockKinesisFacade(config, StreamStatus.DOES_NOT_EXIST, StreamStatus.CREATING, StreamStatus.ACTIVE, StreamStatus.UPDATING, StreamStatus.ACTIVE);
        createWriter();

        assertTrue("writer is running", writer.isRunning());

        // retrieveStreamStatus(): one to discover it doesn't exist, two after creation, two after update

        assertEquals("retrieveStreamStatus() invocationCount",      5,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              1,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        1,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                0,                          mock.putRecordsInvocationCount);

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "creating kinesis stream: " + DEFAULT_STREAM_NAME,
                        "setting retention period on stream \"" + DEFAULT_STREAM_NAME + "\" to 48 hours",
                        "log writer initialization complete.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testCreateStreamWithRetentionPeriodTimeout() throws Exception
    {
        config.setAutoCreate(true);
        config.setRetentionPeriod(48);

        mock = new MockKinesisFacade(config, StreamStatus.DOES_NOT_EXIST, StreamStatus.CREATING, StreamStatus.ACTIVE, StreamStatus.UPDATING);
        createWriter();

        assertFalse("writer is running", writer.isRunning());

        // retrieveStreamStatus(): one to discover it doesn't exist, two after creation, four after update

        assertEquals("retrieveStreamStatus() invocationCount",      7,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              1,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        1,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                0,                          mock.putRecordsInvocationCount);

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "creating kinesis stream: " + DEFAULT_STREAM_NAME,
                        "setting retention period on stream \"" + DEFAULT_STREAM_NAME + "\" to 48 hours");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog(
                        "timeout waiting for stream " + DEFAULT_STREAM_NAME + " to become active",
                        "log writer failed to initialize.*");
    }


    @Test
    public void testCreateStreamWithRetentionPeriodException() throws Exception
    {
        config.setAutoCreate(true);
        config.setRetentionPeriod(48);

        final RuntimeException cause = new RuntimeException();
        mock = new MockKinesisFacade(config, StreamStatus.DOES_NOT_EXIST, StreamStatus.CREATING, StreamStatus.ACTIVE)
        {
            @Override
            public void setRetentionPeriod()
            {
                throw new KinesisFacadeException("unexpected", ReasonCode.THROTTLING, false, cause);
            }
        };
        createWriter();

        assertFalse("writer is running", writer.isRunning());

        // retrieveStreamStatus(): one to discover it doesn't exist, two after creation, then the throw

        assertEquals("retrieveStreamStatus() invocationCount",      3,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              1,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        1,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                0,                          mock.putRecordsInvocationCount);

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "creating kinesis stream: " + DEFAULT_STREAM_NAME,
                        "setting retention period on stream \"" + DEFAULT_STREAM_NAME + "\" to 48 hours");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog(
                        "unable to configure stream: " + DEFAULT_STREAM_NAME,
                        "log writer failed to initialize.*");

        assertUltimateCause("reported underlying exception", cause, internalLogger.errorExceptions.get(0));
    }


    @Test
    public void testCreateStreamWithRetentionPeriodCreateTimeout() throws Exception
    {
        // a condition turned up by coverage checks

        config.setAutoCreate(true);
        config.setRetentionPeriod(48);

        mock = new MockKinesisFacade(config, StreamStatus.DOES_NOT_EXIST, StreamStatus.CREATING);
        createWriter();

        assertFalse("writer is running", writer.isRunning());

        // retrieveStreamStatus(): one to discover it doesn't exist, four after creation, then timeout

        assertEquals("retrieveStreamStatus() invocationCount",      5,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              1,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                0,                          mock.putRecordsInvocationCount);

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "creating kinesis stream: " + DEFAULT_STREAM_NAME);
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog(
                        "timeout waiting for stream " + DEFAULT_STREAM_NAME + " to become active",
                        "log writer failed to initialize.*");
    }


    @Test
    public void testNoStreamNoCreate() throws Exception
    {
        // this is the default value, but let's be explicit
        config.setAutoCreate(false);

        mock = new MockKinesisFacade(config, StreamStatus.DOES_NOT_EXIST);
        createWriter();

        assertFalse("writer is running", writer.isRunning());

        assertEquals("retrieveStreamStatus() invocationCount",      1,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              0,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                0,                          mock.putRecordsInvocationCount);

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog(
                        "stream \"" + DEFAULT_STREAM_NAME + "\" does not exist and auto-create not enabled",
                        "log writer failed to initialize.*");
    }


    @Test
    public void testWriteHappyPath() throws Exception
    {
        mock = new MockKinesisFacade(config);
        createWriter();

        assertEquals("stats: actual stream name",                   DEFAULT_STREAM_NAME,        stats.getActualStreamName());

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));
        waitForWriterThread();

        assertEquals("retrieveStreamStatus() invocationCount",      1,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              0,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                1,                          mock.putRecordsInvocationCount);

        assertEquals("putRecords() batch size",                     2,                          mock.putRecordsBatch.size());
        assertEquals("putRecords() first message",                  "message one",              mock.putRecordsBatch.get(0).getMessage());
        assertEquals("putRecords() second message",                 "message two",              mock.putRecordsBatch.get(1).getMessage());

        assertStatisticsTotalMessagesSent(2);
        assertEquals("statistics: last batch messages sent",        2,                          stats.getMessagesSentLastBatch());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "log writer initialization complete.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testWritePartial() throws Exception
    {
        mock = new MockKinesisFacade(config)
        {
            @Override
            public List<LogMessage> putRecords(List<LogMessage> batch)
            {
                return (batch.size() < 2)
                     ? Collections.emptyList()
                     : batch.subList(2, batch.size());
            }
        };
        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message three"));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message four"));

        waitForWriterThread();

        assertEquals("retrieveStreamStatus() invocationCount",      1,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              0,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                1,                          mock.putRecordsInvocationCount);

        assertEquals("putRecords() batch size",                     4,                          mock.putRecordsBatch.size());
        assertEquals("putRecords() first message",                  "message one",              mock.putRecordsBatch.get(0).getMessage());
        assertEquals("putRecords() second message",                 "message two",              mock.putRecordsBatch.get(1).getMessage());

        assertStatisticsTotalMessagesSent(2);
        assertEquals("statistics: last batch messages sent",        2,                          stats.getMessagesSentLastBatch());

        waitForWriterThread();

        assertEquals("retrieveStreamStatus() invocationCount",      1,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              0,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                2,                          mock.putRecordsInvocationCount);

        assertEquals("putRecords() batch size",                     2,                          mock.putRecordsBatch.size());
        assertEquals("putRecords() first message",                  "message three",            mock.putRecordsBatch.get(0).getMessage());
        assertEquals("putRecords() second message",                 "message four",             mock.putRecordsBatch.get(1).getMessage());

        assertStatisticsTotalMessagesSent(4);
        assertEquals("statistics: last batch messages sent",        2,                          stats.getMessagesSentLastBatch());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "log writer initialization complete.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testWriteThrottleRetry() throws Exception
    {
        mock = new MockKinesisFacade(config)
        {
            @Override
            public List<LogMessage> putRecords(List<LogMessage> batch)
            {
                if (putRecordsInvocationCount == 1)
                    throw new KinesisFacadeException("throttling", ReasonCode.THROTTLING, true);
                else
                    return super.putRecords(batch);
            }
        };
        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));
        waitForWriterThread();

        assertEquals("retrieveStreamStatus() invocationCount",      1,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              0,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                2,                          mock.putRecordsInvocationCount);

        assertEquals("putRecords() batch size",                     2,                          mock.putRecordsBatch.size());
        assertEquals("putRecords() first message",                  "message one",              mock.putRecordsBatch.get(0).getMessage());
        assertEquals("putRecords() second message",                 "message two",              mock.putRecordsBatch.get(1).getMessage());

        assertStatisticsTotalMessagesSent(2);
        assertEquals("statistics: last batch messages sent",        2,                          stats.getMessagesSentLastBatch());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "log writer initialization complete.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testWriteUnrecoveredThrottling() throws Exception
    {
        mock = new MockKinesisFacade(config)
        {
            @Override
            public List<LogMessage> putRecords(List<LogMessage> batch)
            {
                throw new KinesisFacadeException("throttling", ReasonCode.THROTTLING, true);
            }
        };
        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));
        waitForWriterThread();

        // number of invocations is based on the RetryManager config in TestableKinesisLogWriter

        assertEquals("retrieveStreamStatus() invocationCount",      1,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              0,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                4,                          mock.putRecordsInvocationCount);

        assertStatisticsTotalMessagesSent(0);
        assertEquals("statistics: last batch messages sent",        0,                          stats.getMessagesSentLastBatch());

        assertEquals("messages remain on message queuue",           2,                          messageQueue.size());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "log writer initialization complete.*");
        internalLogger.assertInternalWarningLog(
                        "timeout while sending batch");
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testWriteException() throws Exception
    {
        final RuntimeException cause = new RuntimeException("something");
        mock = new MockKinesisFacade(config)
        {
            @Override
            public List<LogMessage> putRecords(List<LogMessage> batch)
            {
                throw new KinesisFacadeException("unexpected", ReasonCode.UNEXPECTED_EXCEPTION, false, cause);
            }
        };
        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));
        waitForWriterThread();

        assertEquals("retrieveStreamStatus() invocationCount",      1,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              0,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                1,                          mock.putRecordsInvocationCount);

        assertStatisticsTotalMessagesSent(0);
        assertEquals("statistics: last batch messages sent",        0,                          stats.getMessagesSentLastBatch());

        assertEquals("messages remain on message queuue",           2,                          messageQueue.size());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "log writer initialization complete.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog(
                        "exception while sending batch");

        assertUltimateCause("reported underlying exception", cause, internalLogger.errorExceptions.get(0));
    }


    @Test
    public void testDiscardEmptyMessage() throws Exception
    {
        mock = new MockKinesisFacade(config);
        createWriter();

        // need to add a non-empty message to verify batch-building

        writer.addMessage(new LogMessage(System.currentTimeMillis(), ""));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "OK"));
        waitForWriterThread();

        assertEquals("putRecords() invocationCount",                1,                          mock.putRecordsInvocationCount);
        assertEquals("putRecords() batch size",                     1,                          mock.putRecordsBatch.size());
        assertEquals("putRecords() message",                        "OK",                       mock.putRecordsBatch.get(0).getMessage());

        internalLogger.assertInternalWarningLog("discarded empty message");
    }


    @Test
    public void testDiscardOversizeMessage() throws Exception
    {
        final int kinesisMaxMessageSize = 1024 * 1024;  // per https://docs.aws.amazon.com/kinesis/latest/APIReference/API_PutRecords.html
        final int maxMessageSize        = kinesisMaxMessageSize - DEFAULT_PARTITION_KEY.length();   // DEFAULT_PARTITION_KEY is ASCII

        // using different characters at the end of the message makes JUnit output easer to read
        final String bigMessage                 = StringUtil.repeat('X', maxMessageSize - 1) + "Y";
        final String biggerMessage              = bigMessage + "X";

        mock = new MockKinesisFacade(config);
        createWriter();

        // send discarded message first, OK-size message to trigger batch-builidng

        writer.addMessage(new LogMessage(System.currentTimeMillis(), biggerMessage));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), bigMessage));
        waitForWriterThread();

        assertEquals("putRecords() invocationCount",                1,                          mock.putRecordsInvocationCount);
        assertEquals("putRecords() batch size",                     1,                          mock.putRecordsBatch.size());
        assertEquals("putRecords() message",                        bigMessage,                 mock.putRecordsBatch.get(0).getMessage());

        internalLogger.assertInternalWarningLog("discarded oversize message.*");
    }


    @Test
    public void testTruncateOversizeMessage() throws Exception
    {
        final int kinesisMaxMessageSize = 1024 * 1024;  // per https://docs.aws.amazon.com/kinesis/latest/APIReference/API_PutRecords.html
        final int maxMessageSize        = kinesisMaxMessageSize - DEFAULT_PARTITION_KEY.length();   // DEFAULT_PARTITION_KEY is ASCII

        // using different characters at the end of the message makes JUnit output easer to read
        final String bigMessage                 = StringUtil.repeat('X', maxMessageSize - 1) + "Y";
        final String biggerMessage              = bigMessage + "X";

        config.setTruncateOversizeMessages(true);

        mock = new MockKinesisFacade(config);
        createWriter();

        // send discarded message first, OK-size message to trigger batch-builidng

        writer.addMessage(new LogMessage(System.currentTimeMillis(), bigMessage));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), biggerMessage));
        waitForWriterThread();

        assertEquals("putRecords() invocationCount",                1,                          mock.putRecordsInvocationCount);
        assertEquals("putRecords() batch size",                     2,                          mock.putRecordsBatch.size());
        assertEquals("putRecords() first message",                  bigMessage,                 mock.putRecordsBatch.get(0).getMessage());
        assertEquals("putRecords() second message",                 bigMessage,                 mock.putRecordsBatch.get(1).getMessage());

        internalLogger.assertInternalWarningLog("truncated oversize message.*");
    }


    @Test
    public void testBatchConstructionByRecordCount() throws Exception
    {
        // don't let discard threshold get in the way of the test
        config.setDiscardAction(DiscardAction.none);
        config.setDiscardThreshold(Integer.MAX_VALUE);

        // increasing delay because it will may take time to add the messages -- 500 ms is way higher than we need
        config.setBatchDelay(500);

        mock = new MockKinesisFacade(config);
        createWriter();

        List<String> expectedMessages = new ArrayList<>();
        for (int ii = 0 ; ii < 750 ; ii++)
        {
            String message = String.valueOf(ii);
            expectedMessages.add(message);
            writer.addMessage(new LogMessage(System.currentTimeMillis(), message));
        }

        // based on count, this should be broken into batches of 500 and 250

        waitForWriterThread();

        assertEquals("putRecords() invocationCount",                1,                  mock.putRecordsInvocationCount);
        assertEquals("putRecords() batch size",                     500,                mock.putRecordsBatch.size());
        assertEquals("putRecords() batch 1 first message",          "0",                mock.putRecordsBatch.get(0).getMessage());
        assertEquals("putRecords() batch 1 last message",           "499",              mock.putRecordsBatch.get(499).getMessage());
        assertEquals("unsent messages remain on queue",             250,                messageQueue.size());

        waitForWriterThread();

        assertEquals("putRecords() invocationCount",                2,                  mock.putRecordsInvocationCount);
        assertEquals("putRecords() batch size",                     250,                mock.putRecordsBatch.size());
        assertEquals("putRecords() batch 2 first message",          "500",              mock.putRecordsBatch.get(0).getMessage());
        assertEquals("putRecords() batch 2 last message",           "749",              mock.putRecordsBatch.get(249).getMessage());
        assertEquals("no unsent messages remain on queue",          0,                  messageQueue.size());

        List<String> putRecordsHistory = mock.putRecordsHistory.stream().map(LogMessage::getMessage).collect(Collectors.toList());

        assertEquals("all messages sent, in order",                 expectedMessages,   putRecordsHistory);
    }


    @Test
    public void testBatchConstructionByTotalSize() throws Exception
    {
        // don't let discard threshold get in the way of the test
        config.setDiscardAction(DiscardAction.none);
        config.setDiscardThreshold(Integer.MAX_VALUE);

        // increasing delay because it will may take time to add the messages -- 500 ms is way higher than we need
        config.setBatchDelay(500);

        mock = new MockKinesisFacade(config);
        createWriter();

        String baseMessage = StringUtil.repeat('X', 32765); // leave room for ID
        int numMessages = 200;  // ~6.5 MB

        List<String> expectedMessages = new ArrayList<>();
        for (int ii = 0 ; ii < numMessages ; ii++)
        {
            String message = String.format("%03d", ii) + baseMessage;
            expectedMessages.add(message);
            writer.addMessage(new LogMessage(System.currentTimeMillis(), message));
        }

        // based on size, this will be broken into a 5 MB batch (that would in turn require 5 sends) and a 1.5 MB batch
        // taking partition key into account, that's translates into 159 and 41 records respectively

        waitForWriterThread();

        assertEquals("putRecords() invocationCount",                1,                  mock.putRecordsInvocationCount);
        assertEquals("putRecords() batch size",                     159,                mock.putRecordsBatch.size());
        assertRegex("putRecords() batch 1 first message",          "000X.*",            mock.putRecordsBatch.get(0).getMessage());
        assertRegex("putRecords() batch 1 last message",           "158X.*",            mock.putRecordsBatch.get(158).getMessage());
        assertEquals("unsent messages remain on queue",             41,                 messageQueue.size());

        waitForWriterThread();

        assertEquals("putRecords() invocationCount",                2,                  mock.putRecordsInvocationCount);
        assertEquals("putRecords() batch size",                     41,                 mock.putRecordsBatch.size());
        assertRegex("putRecords() batch 2 first message",           "159X.*",           mock.putRecordsBatch.get(0).getMessage());
        assertRegex("putRecords() batch 2 last message",            "199X.*",           mock.putRecordsBatch.get(40).getMessage());
        assertEquals("no unsent messages remain on queue",          0,                  messageQueue.size());

        List<String> putRecordsHistory = mock.putRecordsHistory.stream().map(LogMessage::getMessage).collect(Collectors.toList());

        assertEquals("all messages sent, in order",                 expectedMessages,   putRecordsHistory);
    }


//    @Test
//    public void testShutdown() throws Exception
//    {
//        // this test is the only place that we expicitly test shutdown logic, to avoid cluttering
//        // the "operation" tests; it's otherwise identical to the "existing stream" test
//
//        // it actually tests functionality in AbstractAppender, but I've replicated for all concrete
//        // subclasses simply because it's a key piece of functionality
//
//        createWriter();
//
//        assertEquals("after creation, shutdown time should be infinite", Long.MAX_VALUE, getShutdownTime());
//
//        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
//
//        // the immediate stop should interrupt waitForMessage, but there's no guarantee
//        writer.stop();
//
//        long now = System.currentTimeMillis();
//        long shutdownTime = getShutdownTime();
//        assertInRange("after stop(), shutdown time should be based on batch delay", now, now + config.getBatchDelay() + 100, shutdownTime);
//
//        // the batch should still be processed
//        mock.allowWriterThread();
//
//        assertEquals("putRecords: invocation count",        1,                          mock.putRecordsInvocationCount);
//        assertEquals("putRecords: source record count",     1,                          mock.putRecordsSourceRecords.size());
//
//        // another call to stop should be ignored -- sleep to ensure times would be different
//        Thread.sleep(100);
//        writer.stop();
//        assertEquals("second call to stop() should be no-op", shutdownTime, getShutdownTime());
//
//        joinWriterThread();
//
//        assertEquals("shutdown: invocation count",          1,                          mock.shutdownInvocationCount);
//
//        internalLogger.assertInternalDebugLog(
//            "log writer starting.*",
//            "log writer initialization complete.*",
//            "log.writer shut down.*");
//        internalLogger.assertInternalErrorLog();
//    }
//
//
//    @Test
//    public void testSynchronousOperation() throws Exception
//    {
//        // appender is expected to set batch delay in synchronous mode
//        config.setBatchDelay(1);
//
//        // we just have one thread, so don't want any locks getting in the way
//        mock.disableThreadSynchronization();
//
//        // the createWriter() method spins up a background thread, which we don't want
//        writer = (KinesisLogWriter)mock.newWriterFactory().newLogWriter(config, stats, internalLogger);
//        messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);
//
//        assertEquals("stats: actual stream name",                   DEFAULT_STREAM_NAME,        stats.getActualStreamName());
//        assertFalse("writer should not be initialized",             ClassUtil.getFieldValue(writer, "initializationComplete", Boolean.class).booleanValue());
//
//        writer.initialize();
//
//        assertTrue("writer has been initialized",                   ClassUtil.getFieldValue(writer, "initializationComplete", Boolean.class).booleanValue());
//        assertNull("no dispatch thread",                            ClassUtil.getFieldValue(writer, "dispatchThread", Thread.class));
//
//        assertEquals("describeStream: invocation count",            1,                          mock.describeStreamInvocationCount);
//        assertEquals("describeStream: stream name",                 DEFAULT_STREAM_NAME,        mock.describeStreamStreamName);
//
//        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
//
//        assertEquals("message is waiting in queue",                 1,                          messageQueue.queueSize());
//        assertEquals("putRecords: invocation count",                0,                          mock.putRecordsInvocationCount);
//
//        writer.processBatch(System.currentTimeMillis());
//
//        assertEquals("putRecords: invocation count",                1,                          mock.putRecordsInvocationCount);
//        assertEquals("putRecords: source record count",             1,                          mock.putRecordsSourceRecords.size());
//        assertEquals("putRecords: source record partition key",     DEFAULT_PARTITION_KEY,      mock.putRecordsSourceRecords.get(0).getPartitionKey());
//        assertEquals("putRecords: source record content",           "message one",              mock.getPuRecordsSourceText(0));
//        assertStatisticsTotalMessagesSent(1);
//        assertEquals("stats: messages sent batch",                  1,                          stats.getMessagesSentLastBatch());
//        assertEquals("stats: messages requeued batch",              0,                          stats.getMessagesRequeuedLastBatch());
//
//        // general assertions to verify that nothing unexpected happened
//        assertEquals("describeStream: invocation count",            1,                          mock.describeStreamInvocationCount);
//        assertEquals("describeStream: stream name",                 DEFAULT_STREAM_NAME,        mock.describeStreamStreamName);
//        assertEquals("createStream: invocation count",              0,                          mock.createStreamInvocationCount);
//        assertEquals("increaseRetentionPeriod invocation count",    0,                          mock.increaseRetentionPeriodInvocationCount);
//
//        assertEquals("shutdown not called before cleanup",          0,                          mock.shutdownInvocationCount);
//        writer.cleanup();
//        assertEquals("shutdown called after cleanup",               1,                          mock.shutdownInvocationCount);
//
//        // the "starting" and "initialization complete" messages are emitted in run(), so not present here
//        internalLogger.assertInternalDebugLog();
//        internalLogger.assertInternalErrorLog();
//    }
}