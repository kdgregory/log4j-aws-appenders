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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.StringUtil;
import static net.sf.kdgcommons.test.StringAsserts.*;
import static net.sf.kdgcommons.test.NumericAsserts.*;

import com.kdgregory.logging.aws.kinesis.KinesisConstants.StreamStatus;
import com.kdgregory.logging.aws.facade.KinesisFacade;
import com.kdgregory.logging.aws.facade.KinesisFacadeException;
import com.kdgregory.logging.aws.facade.KinesisFacadeException.ReasonCode;
import com.kdgregory.logging.aws.kinesis.KinesisLogWriter;
import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;
import com.kdgregory.logging.aws.kinesis.KinesisWriterStatistics;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.internal.Utils;
import com.kdgregory.logging.common.util.InternalLogger;
import com.kdgregory.logging.common.util.MessageQueue.DiscardAction;
import com.kdgregory.logging.common.util.WriterFactory;
import com.kdgregory.logging.testhelpers.kinesis.MockKinesisFacade;
import com.kdgregory.logging.testhelpers.kinesis.TestableKinesisLogWriter;


/**
 *  Performs mock-facade testing of <code>KinesisLogWriter</code>.
 *  <p>
 *  The goal of these tests is to verify the invocaton and retry logic of the writer.
 */
public class TestKinesisLogWriter
extends AbstractLogWriterTest<KinesisLogWriter,KinesisWriterConfig,KinesisWriterStatistics>
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
                 .setDiscardAction(DiscardAction.oldest)
                 .setUseShutdownHook(false)
                 .setInitializationTimeout(250);

        stats = new KinesisWriterStatistics();
    }


    @After
    public void tearDown()
    throws Throwable
    {
        if (writer != null)
        {
            writer.stop();
            ((TestableKinesisLogWriter)writer).releaseWriterThread();
        }

        if (uncaughtException != null)
            throw uncaughtException;
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testInitialization() throws Exception
    {
        // the goal here is to verify propagation from the writer to its objects,
        // so we'll explicitly set the fields that we expect to be propagated

        config.setBatchDelay(123)
              .setDiscardThreshold(456)
              .setDiscardAction(DiscardAction.newest);

        mock = new MockKinesisFacade(config);
        createWriter();

        assertTrue("writer is running", writer.isRunning());

        assertEquals("retrieveStreamStatus() invocationCount",      1,                          mock.retrieveStreamStatusInvocationCount);

        assertEquals("writer batch delay",                          123L,                       writer.getBatchDelay());
        assertEquals("message queue discard policy",                DiscardAction.newest,       messageQueue.getDiscardAction());
        assertEquals("message queue discard threshold",             456,                        messageQueue.getDiscardThreshold());

        assertEquals("stats: actual stream name",                   DEFAULT_STREAM_NAME,        stats.getActualStreamName());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
                        "log writer initialization complete.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testInitializationInvalidConfiguration() throws Exception
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
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
                        "waiting for stream " + DEFAULT_STREAM_NAME + " to become active",
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

        // initial status check, plus 5 until timeout

        assertEquals("retrieveStreamStatus() invocationCount",      6,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              0,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                0,                          mock.putRecordsInvocationCount);

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
                        "waiting for stream " + DEFAULT_STREAM_NAME + " to become active");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog(
                        "timeout waiting for stream " + DEFAULT_STREAM_NAME + " to become active",
                        "log writer failed to initialize.*");
    }


    @Test
    public void testWaitForStreamReadyException() throws Exception
    {
        mock = new MockKinesisFacade(config)
        {
            @Override
            public StreamStatus retrieveStreamStatus()
            {
                throw new RuntimeException("standin for network timeout SDK exception");
            }
        };
        createWriter();

        assertFalse("writer is running", writer.isRunning());

        assertEquals("retrieveStreamStatus() invocationCount",      1,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              0,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                0,                          mock.putRecordsInvocationCount);

        assertEquals("message queue discard threshold reduced",     0,                          messageQueue.getDiscardThreshold());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "checking status of stream: " + DEFAULT_STREAM_NAME);
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog(
                        "exception during initialization.*",
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
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
                        "creating kinesis stream: " + DEFAULT_STREAM_NAME,
                        "waiting for stream " + DEFAULT_STREAM_NAME + " to become active",
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

        // initial status check, plus 5 until timeout

        assertEquals("retrieveStreamStatus() invocationCount",      6,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              1,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                0,                          mock.putRecordsInvocationCount);

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
                        "creating kinesis stream: " + DEFAULT_STREAM_NAME,
                        "waiting for stream " + DEFAULT_STREAM_NAME + " to become active");
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
                throw new KinesisFacadeException(ReasonCode.THROTTLING, false, cause);
            }
        };
        createWriter();

        assertFalse("writer is running", writer.isRunning());

        // initial status check only

        assertEquals("retrieveStreamStatus() invocationCount",      1,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              1,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                0,                          mock.putRecordsInvocationCount);

        assertEquals("message queue discard threshold reduced",     0,                          messageQueue.getDiscardThreshold());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
                        "creating kinesis stream: " + DEFAULT_STREAM_NAME);
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog(
                        "exception during initialization",
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
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
                        "creating kinesis stream: " + DEFAULT_STREAM_NAME,
                        "waiting for stream " + DEFAULT_STREAM_NAME + " to become active",
                        "setting retention period on stream \"" + DEFAULT_STREAM_NAME + "\" to 48 hours",
                        "waiting for stream " + DEFAULT_STREAM_NAME + " to become active",
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
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
                        "creating kinesis stream: " + DEFAULT_STREAM_NAME,
                        "waiting for stream " + DEFAULT_STREAM_NAME + " to become active",
                        "setting retention period on stream \"" + DEFAULT_STREAM_NAME + "\" to 48 hours",
                        "waiting for stream " + DEFAULT_STREAM_NAME + " to become active");
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
                throw new KinesisFacadeException(ReasonCode.THROTTLING, false, cause);
            }
        };
        createWriter();

        assertFalse("writer is running", writer.isRunning());

        // retrieveStreamStatus(): one to discover it doesn't exist, two after creation, then the throw

        assertEquals("retrieveStreamStatus() invocationCount",      3,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              1,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        1,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                0,                          mock.putRecordsInvocationCount);

        assertEquals("message queue discard threshold reduced",     0,                          messageQueue.getDiscardThreshold());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
                        "creating kinesis stream: " + DEFAULT_STREAM_NAME,
                        "waiting for stream " + DEFAULT_STREAM_NAME + " to become active",
                        "setting retention period on stream \"" + DEFAULT_STREAM_NAME + "\" to 48 hours");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog(
                        "exception during initialization",
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

        // retrieveStreamStatus(): one to discover it doesn't exist, five after creation, then timeout

        assertEquals("retrieveStreamStatus() invocationCount",      6,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              1,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                0,                          mock.putRecordsInvocationCount);

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
                        "creating kinesis stream: " + DEFAULT_STREAM_NAME,
                        "waiting for stream " + DEFAULT_STREAM_NAME + " to become active");
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
                        "log writer starting.*",
                        "checking status of stream: " + DEFAULT_STREAM_NAME);
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
        assertNotSame("putRecords() thread",                        Thread.currentThread(),     mock.putRecordsThread);
        assertEquals("putRecords() batch size",                     2,                          mock.putRecordsBatch.size());
        assertEquals("putRecords() first message",                  "message one",              mock.putRecordsBatch.get(0).getMessage());
        assertEquals("putRecords() second message",                 "message two",              mock.putRecordsBatch.get(1).getMessage());

        assertStatisticsTotalMessagesSent(2);
        assertEquals("statistics: last batch size",                 2,                          stats.getLastBatchSize());
        assertEquals("statistics: last batch messages sent",        2,                          stats.getMessagesSentLastBatch());
        assertEquals("statistics: last batch messages requeued",    0,                          stats.getMessagesRequeuedLastBatch());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
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
        assertEquals("statistics: last batch size",                 4,                          stats.getLastBatchSize());
        assertEquals("statistics: last batch messages sent",        2,                          stats.getMessagesSentLastBatch());
        assertEquals("statistics: last batch messages requeued",    2,                          stats.getMessagesRequeuedLastBatch());

        waitForWriterThread();

        assertEquals("retrieveStreamStatus() invocationCount",      1,                          mock.retrieveStreamStatusInvocationCount);
        assertEquals("createStream() invocationCount",              0,                          mock.createStreamInvocationCount);
        assertEquals("setRetentionPeriod() invocationCount",        0,                          mock.setRetentionPeriodInvocationCount);
        assertEquals("putRecords() invocationCount",                2,                          mock.putRecordsInvocationCount);

        assertEquals("putRecords() batch size",                     2,                          mock.putRecordsBatch.size());
        assertEquals("putRecords() first message",                  "message three",            mock.putRecordsBatch.get(0).getMessage());
        assertEquals("putRecords() second message",                 "message four",             mock.putRecordsBatch.get(1).getMessage());

        assertStatisticsTotalMessagesSent(4);
        assertEquals("statistics: last batch size",                 2,                          stats.getLastBatchSize());
        assertEquals("statistics: last batch messages sent",        2,                          stats.getMessagesSentLastBatch());
        assertEquals("statistics: last batch messages requeued",    0,                          stats.getMessagesRequeuedLastBatch());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
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
                    throw new KinesisFacadeException(ReasonCode.THROTTLING, true, null);
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
        assertEquals("statistics: last batch size",                 2,                          stats.getLastBatchSize());
        assertEquals("statistics: last batch messages sent",        2,                          stats.getMessagesSentLastBatch());
        assertEquals("statistics: last batch messages requeued",    0,                          stats.getMessagesRequeuedLastBatch());
        assertEquals("statistics: number of throttles",             1,                          stats.getThrottledWrites());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
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
                throw new KinesisFacadeException(ReasonCode.THROTTLING, true, null);
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
        assertEquals("statistics: last batch size",                 2,                          stats.getLastBatchSize());
        assertEquals("statistics: last batch messages sent",        0,                          stats.getMessagesSentLastBatch());
        assertEquals("statistics: last batch messages requeued",    2,                          stats.getMessagesRequeuedLastBatch());
        assertEquals("statistics: number of throttles",             4,                          stats.getThrottledWrites());

        assertEquals("messages remain on message queuue",           2,                          messageQueue.size());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
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
                throw new KinesisFacadeException(ReasonCode.UNEXPECTED_EXCEPTION, false, cause);
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
        assertEquals("statistics: last batch size",                 2,                          stats.getLastBatchSize());
        assertEquals("statistics: last batch messages sent",        0,                          stats.getMessagesSentLastBatch());
        assertEquals("statistics: last batch messages requeued",    2,                          stats.getMessagesRequeuedLastBatch());

        assertEquals("messages remain on message queuue",           2,                          messageQueue.size());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
                        "log writer initialization complete.*");
        internalLogger.assertInternalWarningLog(
                        "exception while sending batch");
        internalLogger.assertInternalErrorLog();

        assertUltimateCause("reported underlying exception", cause, internalLogger.warnExceptions.get(0));
    }


    @Test
    public void testBatchLogging() throws Exception
    {
        config.setEnableBatchLogging(true);
        mock = new MockKinesisFacade(config);
        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        waitForWriterThread();

        assertEquals("putRecords() batch size",                     1,                          mock.putRecordsBatch.size());

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message three"));
        waitForWriterThread();

        assertEquals("putRecords() batch size",                     2,                          mock.putRecordsBatch.size());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
                        "log writer initialization complete.*",
                        "about to write batch of 1 message\\(s\\)",
                        "wrote batch of 1 message\\(s\\); 0 rejected",
                        "about to write batch of 2 message\\(s\\)",
                        "wrote batch of 2 message\\(s\\); 0 rejected");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testBatchLoggingPartial() throws Exception
    {
        config.setEnableBatchLogging(true);
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

        assertEquals("putRecords() batch size",                     4,                          mock.putRecordsBatch.size());

        waitForWriterThread();

        assertEquals("putRecords() batch size",                     2,                          mock.putRecordsBatch.size());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
                        "log writer initialization complete.*",
                        "about to write batch of 4 message\\(s\\)",
                        "wrote batch of 4 message\\(s\\); 2 rejected",
                        "about to write batch of 2 message\\(s\\)",
                        "wrote batch of 2 message\\(s\\); 0 rejected");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
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

        config.setTruncateOversizeMessages(false);

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

        // this is the default case, no need to set config

        mock = new MockKinesisFacade(config);
        createWriter();

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


    @Test
    public void testSynchronousOperation() throws Exception
    {
        config.setSynchronousMode(true);
        mock = new MockKinesisFacade(config);

        createWriter();

        assertTrue("writer is running",                             writer.isRunning());
        assertTrue("writer is in synchronous mode",                 writer.isSynchronous());
        assertSame("writer initialized on main thread",             Thread.currentThread(),     writerThread);

        assertEquals("retrieveStreamStatus() invocationCount",      1,                          mock.retrieveStreamStatusInvocationCount);

        ((TestableKinesisLogWriter)writer).disableThreadSynchronization();

        writer.addMessage(new LogMessage(0, "message one"));
        assertEquals("message has been removed from queue",     0,                      messageQueue.size());

        writer.addMessage(new LogMessage(0, "message two"));
        assertEquals("message has been removed from queue",     0,                      messageQueue.size());

        assertEquals("messages have been removed from queue",       0,                          messageQueue.size());
        assertEquals("putRecords() invocationCount",                2,                          mock.putRecordsInvocationCount);
        assertSame("putRecords() thread",                           Thread.currentThread(),     mock.putRecordsThread);
        assertEquals("putRecords() batch size",                     1,                          mock.putRecordsBatch.size());
        assertEquals("putRecords() last message",                   "message two",              mock.putRecordsBatch.get(0).getMessage());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
                        "log writer initialization complete.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testShutdown() throws Exception
    {
        // we want a longish delay so that the main thread can do stuff while waiting,
        // but not so long that the test takes forever
        final int batchDelay = 500;
        final int stopDelay = batchDelay / 2;
        config.setBatchDelay(batchDelay);

        mock = new MockKinesisFacade(config);
        createWriter();

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));

        // at this point the writer should be blocked by the semaphore; we can't just call
        // stop() in the main thread and then waitForWriter(), because we couldn't tell the
        // difference with normal batch processing; so we'll call stop() from a new thread

        AtomicInteger messagesOnQueueAtStop = new AtomicInteger();
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                Utils.sleepQuietly(stopDelay);
                messagesOnQueueAtStop.set(messageQueue.size());
                writer.stop();
            }
        }).start();

        assertEquals("messages on queue before writer release",     2,                                  messageQueue.size());

        long writerReleasedAt = System.currentTimeMillis();
        waitForWriterThread();
        long mainReturnedAt = System.currentTimeMillis();

        assertTrue("writer is still running",                                                           writer.isRunning());
        assertEquals("messages on queue when stop() called",        0,                                  messagesOnQueueAtStop.get());

        assertEquals("putRecords() invocationCount",                1,                                  mock.putRecordsInvocationCount);
        assertEquals("putRecords: #/messages",                      2,                                  mock.putRecordsBatch.size());
        assertInRange("time to process",                            stopDelay - 10, stopDelay + 100,    mainReturnedAt - writerReleasedAt);

        // after the stop, writer should wait for batchDelay for any more messages, so let it run again
        // ... if this never returns, we know that the shutdown processing was incorrect
        waitForWriterThread();

        // the writer thread should be fully done at this point; the join() won't return if not
        ((TestableKinesisLogWriter)writer).writerThread.join();
        long shutdownTime = System.currentTimeMillis();

        assertEquals("facade shutdown called",                      1,                                  mock.shutdownInvocationCount);
        assertFalse("writer has stopped",                           writer.isRunning());
        assertInRange("time to process",                            batchDelay - 100, batchDelay + 100, shutdownTime - mainReturnedAt);

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "checking status of stream: " + DEFAULT_STREAM_NAME,
                        "log writer initialization complete.*",
                        "log.writer shut down.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }
}