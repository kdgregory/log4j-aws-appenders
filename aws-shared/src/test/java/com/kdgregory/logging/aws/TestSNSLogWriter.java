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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.StringUtil;

import com.kdgregory.logging.aws.internal.facade.SNSFacade;
import com.kdgregory.logging.aws.internal.facade.SNSFacadeException;
import com.kdgregory.logging.aws.internal.facade.SNSFacadeException.ReasonCode;
import com.kdgregory.logging.aws.sns.SNSLogWriter;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;
import com.kdgregory.logging.aws.sns.SNSWriterStatistics;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.factories.WriterFactory;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.common.util.InternalLogger;
import com.kdgregory.logging.testhelpers.sns.MockSNSFacade;
import com.kdgregory.logging.testhelpers.sns.TestableSNSLogWriter;


/**
 *  Performs mock-facade testing of the SNS writer.
 */
public class TestSNSLogWriter
extends AbstractLogWriterTest<SNSLogWriter,SNSWriterConfig,SNSWriterStatistics>
{
    // used for oversize message tests; per https://docs.aws.amazon.com/sns/latest/api/API_Publish.html
    private final static int SNS_MAX_MESSAGE_SIZE = 262144;

    private final static String TEST_TOPIC_NAME = "example";
    private final static String TEST_TOPIC_ARN  = "arn:aws:sns:us-east-1:123456789012:example";
    private final static String TEST_SUBJECT    = "test subject";

    // will be created by each test
    private MockSNSFacade mock;


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
        final SNSFacade facade = mock.newInstance();
        WriterFactory<SNSWriterConfig,SNSWriterStatistics> writerFactory
            = new WriterFactory<SNSWriterConfig,SNSWriterStatistics>()
            {
                @Override
                public LogWriter newLogWriter(
                        SNSWriterConfig passedConfig,
                        SNSWriterStatistics passedStats,
                        InternalLogger passedLogger)
                {
                    return new TestableSNSLogWriter(passedConfig, passedStats, passedLogger, facade);
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
        ((TestableSNSLogWriter)writer).waitForWriterThread();
    }



//----------------------------------------------------------------------------
//  JUnit scaffolding
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        // default configuration; suitable for happy-path tests
        config = new SNSWriterConfig()
                 .setTopicName(TEST_TOPIC_NAME)
                 .setSubject(TEST_SUBJECT)
                 .setDiscardThreshold(10000)
                 .setDiscardAction(DiscardAction.oldest);

        stats = new SNSWriterStatistics();
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
    public void testInitializationConfigurationByName() throws Exception
    {
        // ... note that we try setting batch delay but it has no effect
        // ... note also that we set topic name, but get back topic ARN

        config = new SNSWriterConfig()
                 .setTopicName(TEST_TOPIC_NAME)
                 .setTopicArn(null)
                 .setSubject(TEST_SUBJECT)
                 .setBatchDelay(123)
                 .setDiscardThreshold(456)
                 .setDiscardAction(DiscardAction.newest);

        mock = new MockSNSFacade(config, TEST_TOPIC_NAME);

        createWriter();
        assertTrue("writer is running", writer.isRunning());

        assertEquals("stats: topic name",                       TEST_TOPIC_NAME,        stats.getActualTopicName());
        assertEquals("stats: topic ARN",                        TEST_TOPIC_ARN,         stats.getActualTopicArn());
        assertEquals("stats: subject",                          TEST_SUBJECT,           stats.getActualSubject());

        assertEquals("writer batch delay",                      1L,                     writer.getBatchDelay());
        assertEquals("message queue discard policy",            DiscardAction.newest,   messageQueue.getDiscardAction());
        assertEquals("message queue discard threshold",         456,                    messageQueue.getDiscardThreshold());

        assertEquals("mock: lookupTopicInvocationCount",        1,                      mock.lookupTopicInvocationCount);
        assertEquals("mock: createTopicInvocationCount",        0,                      mock.createTopicInvocationCount);
        assertEquals("mock: publishInvocationCount",            0,                      mock.publishInvocationCount);
        assertEquals("mock: shutdownInvocationCount",           0,                      mock.shutdownInvocationCount);

        assertNull("stats: no error message",                                           stats.getLastErrorMessage());
        assertNull("stats: no exception",                                               stats.getLastError());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "log writer initialization complete.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testInitializationConfigurationByARN() throws Exception
    {
        // ... note that we try setting batch delay but it has no effect

        config = new SNSWriterConfig()
                 .setTopicName(null)
                 .setTopicArn(TEST_TOPIC_ARN)
                 .setSubject(TEST_SUBJECT)
                 .setBatchDelay(123)
                 .setDiscardThreshold(456)
                 .setDiscardAction(DiscardAction.newest);

        mock = new MockSNSFacade(config, TEST_TOPIC_NAME);

        createWriter();
        assertTrue("writer is running", writer.isRunning());

        assertEquals("stats: topic name",                       TEST_TOPIC_NAME,        stats.getActualTopicName());
        assertEquals("stats: topic ARN",                        TEST_TOPIC_ARN,         stats.getActualTopicArn());
        assertEquals("stats: subject",                          TEST_SUBJECT,           stats.getActualSubject());

        assertEquals("writer batch delay",                      1L,                     writer.getBatchDelay());
        assertEquals("message queue discard policy",            DiscardAction.newest,   messageQueue.getDiscardAction());
        assertEquals("message queue discard threshold",         456,                    messageQueue.getDiscardThreshold());

        assertEquals("mock: lookupTopicInvocationCount",        1,                      mock.lookupTopicInvocationCount);
        assertEquals("mock: createTopicInvocationCount",        0,                      mock.createTopicInvocationCount);
        assertEquals("mock: publishInvocationCount",            0,                      mock.publishInvocationCount);
        assertEquals("mock: shutdownInvocationCount",           0,                      mock.shutdownInvocationCount);

        assertNull("stats: no error message",                                           stats.getLastErrorMessage());
        assertNull("stats: no exception",                                               stats.getLastError());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "log writer initialization complete.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testInitializationInvalidConfiguration() throws Exception
    {
        config = new SNSWriterConfig()
                 .setTopicName(null)
                 .setTopicArn(null);

        mock = new MockSNSFacade(config);

        createWriter();
        assertFalse("writer is running", writer.isRunning());

        assertEquals("mock: lookupTopicInvocationCount",        0,                          mock.lookupTopicInvocationCount);
        assertEquals("mock: createTopicInvocationCount",        0,                          mock.createTopicInvocationCount);
        assertEquals("mock: publishInvocationCount",            0,                          mock.publishInvocationCount);
        assertEquals("mock: shutdownInvocationCount",           0,                          mock.shutdownInvocationCount);

        assertRegex("stats: reported message",                  "configuration error.*",    stats.getLastErrorMessage());
        assertNull("stats: no exception",                                                   stats.getLastError());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog(
                        "configuration error.*",
                        "log writer failed to initialize.*");
    }


    @Test
    public void testInitializationException() throws Exception
    {
        final SNSFacadeException cause = new SNSFacadeException(ReasonCode.UNEXPECTED_EXCEPTION, false, null);
        mock = new MockSNSFacade(config)
        {
            @Override
            public String lookupTopic()
            {
                throw cause;
            }
        };

        createWriter();
        assertFalse("writer is running", writer.isRunning());

        assertEquals("mock: lookupTopicInvocationCount",        1,                          mock.lookupTopicInvocationCount);
        assertEquals("mock: createTopicInvocationCount",        0,                          mock.createTopicInvocationCount);
        assertEquals("mock: publishInvocationCount",            0,                          mock.publishInvocationCount);
        assertEquals("mock: shutdownInvocationCount",           0,                          mock.shutdownInvocationCount);

        assertRegex("stats: reported message",                  "unable to configure",      stats.getLastErrorMessage());
        assertSame("stats reported exception",                  cause,                      stats.getLastError());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog(
                        "unable to configure",
                        "log writer failed to initialize.*");

        assertSame("exception logged",          cause,                  internalLogger.errorExceptions.get(0));
    }


    @Test
    public void testInitializationMissingTopic() throws Exception
    {
        // have to set either name or ARN to proceed to lookup, but this test doesn't care which is set
        config = new SNSWriterConfig()
                 .setTopicName("TEST_TOPIC_NAME")
                 .setTopicArn(null);

        mock = new MockSNSFacade(config);

        createWriter();
        assertFalse("writer is running", writer.isRunning());

        assertEquals("mock: lookupTopicInvocationCount",        1,                          mock.lookupTopicInvocationCount);
        assertEquals("mock: createTopicInvocationCount",        0,                          mock.createTopicInvocationCount);
        assertEquals("mock: publishInvocationCount",            0,                          mock.publishInvocationCount);
        assertEquals("mock: shutdownInvocationCount",           0,                          mock.shutdownInvocationCount);

        assertRegex("stats: reported message",                  "topic does not exist.*",   stats.getLastErrorMessage());
        assertNull("stats: no exception",                                                   stats.getLastError());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog(
                        "topic does not exist.*",
                        "log writer failed to initialize.*");
    }


    @Test
    public void testInitializationCreateTopic() throws Exception
    {
        config = new SNSWriterConfig()
                 .setTopicName(TEST_TOPIC_NAME)
                 .setAutoCreate(true);

        mock = new MockSNSFacade(config);

        createWriter();
        assertTrue("writer is running", writer.isRunning());

        assertEquals("mock: lookupTopicInvocationCount",        1,                      mock.lookupTopicInvocationCount);
        assertEquals("mock: createTopicInvocationCount",        1,                      mock.createTopicInvocationCount);
        assertEquals("mock: publishInvocationCount",            0,                      mock.publishInvocationCount);
        assertEquals("mock: shutdownInvocationCount",           0,                      mock.shutdownInvocationCount);

        assertNull("stats: no error message",                                           stats.getLastErrorMessage());
        assertNull("stats: no exception",                                               stats.getLastError());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "creating SNS topic: " + TEST_TOPIC_NAME,
                        "log writer initialization complete.*");
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testInitializationCreateTopicException() throws Exception
    {
        config = new SNSWriterConfig()
                 .setTopicName(TEST_TOPIC_NAME)
                 .setAutoCreate(true);

        final SNSFacadeException cause = new SNSFacadeException(ReasonCode.UNEXPECTED_EXCEPTION, false, null);
        mock = new MockSNSFacade(config)
        {
            @Override
            public String createTopic()
            {
                throw cause;
            }
        };

        createWriter();
        assertFalse("writer is running", writer.isRunning());

        assertEquals("mock: lookupTopicInvocationCount",        1,                      mock.lookupTopicInvocationCount);
        assertEquals("mock: createTopicInvocationCount",        1,                      mock.createTopicInvocationCount);
        assertEquals("mock: publishInvocationCount",            0,                      mock.publishInvocationCount);
        assertEquals("mock: shutdownInvocationCount",           0,                      mock.shutdownInvocationCount);

        assertEquals("stats: error message",                    "unable to configure",  stats.getLastErrorMessage());
        assertSame("stats: exception",                          cause,                  stats.getLastError());

        internalLogger.assertInternalDebugLog(
                        "log writer starting.*",
                        "creating SNS topic: " + TEST_TOPIC_NAME);
        internalLogger.assertInternalWarningLog();
        internalLogger.assertInternalErrorLog(
                        "unable to configure",
                        "log writer failed to initialize.*");
    }


    @Test
    public void testPublishHappyPath() throws Exception
    {
        mock = new MockSNSFacade(config, TEST_TOPIC_NAME);

        createWriter();
        assertTrue("writer is running", writer.isRunning());

        writer.addMessage(new LogMessage(0, "test message"));
        waitForWriterThread();

        assertEquals("message has been removed from queue",     0,                      messageQueue.size());

        assertEquals("mock: publish ARN",                       TEST_TOPIC_ARN,         mock.publishArn);
        assertEquals("mock: publish subject",                   TEST_SUBJECT,           mock.publishSubject);
        assertEquals("mock: last message written",              "test message",         mock.publishMessage.getMessage());

        assertEquals("mock: lookupTopicInvocationCount",        1,                      mock.lookupTopicInvocationCount);
        assertEquals("mock: createTopicInvocationCount",        0,                      mock.createTopicInvocationCount);
        assertEquals("mock: publishInvocationCount",            1,                      mock.publishInvocationCount);
        assertEquals("mock: shutdownInvocationCount",           0,                      mock.shutdownInvocationCount);

        assertStatisticsTotalMessagesSent(1);
    }


    @Test
    public void testPublishException() throws Exception
    {
        final SNSFacadeException cause = new SNSFacadeException(ReasonCode.UNEXPECTED_EXCEPTION, false, null);
        mock = new MockSNSFacade(config, TEST_TOPIC_NAME)
        {
            @Override
            public void publish(LogMessage message)
            {
                throw cause;
            }
        };

        createWriter();
        assertTrue("writer is running", writer.isRunning());

        writer.addMessage(new LogMessage(0, "test message"));
        waitForWriterThread();

        assertEquals("message remains on queue",                1,                      messageQueue.size());

        assertEquals("mock: lookupTopicInvocationCount",        1,                      mock.lookupTopicInvocationCount);
        assertEquals("mock: createTopicInvocationCount",        0,                      mock.createTopicInvocationCount);
        assertEquals("mock: publishInvocationCount",            1,                      mock.publishInvocationCount);
        assertEquals("mock: shutdownInvocationCount",           0,                      mock.shutdownInvocationCount);

        assertRegex("stats: error message",                     "failed to publish.*",  stats.getLastErrorMessage());
        assertSame("stats: exception",                          cause,                  stats.getLastError());

        assertStatisticsTotalMessagesSent(0);
    }


    @Test
    public void testChangeSubject() throws Exception
    {
        mock = new MockSNSFacade(config, TEST_TOPIC_NAME);

        createWriter();
        assertTrue("writer is running", writer.isRunning());

        writer.addMessage(new LogMessage(0, "test message 1"));
        waitForWriterThread();

        assertEquals("mock: publish ARN",                       TEST_TOPIC_ARN,         mock.publishArn);
        assertEquals("mock: publish subject",                   TEST_SUBJECT,           mock.publishSubject);
        assertEquals("mock: last message written",              "test message 1",       mock.publishMessage.getMessage());

        assertEquals("stats: actual subject before change",     TEST_SUBJECT,           stats.getActualSubject());

        writer.setSubject("something else");

        assertEquals("stats: actual subject after change",      "something else",       stats.getActualSubject());

        writer.addMessage(new LogMessage(0, "test message 2"));
        waitForWriterThread();

        assertEquals("mock: publish ARN",                       TEST_TOPIC_ARN,         mock.publishArn);
        assertEquals("mock: publish subject",                   "something else",       mock.publishSubject);
        assertEquals("mock: last message written",              "test message 2",       mock.publishMessage.getMessage());

        assertStatisticsTotalMessagesSent(2);
    }


    @Test
    public void testDiscardEmptyMessage() throws Exception
    {
        mock = new MockSNSFacade(config, TEST_TOPIC_NAME);

        createWriter();
        assertTrue("writer is running", writer.isRunning());

        writer.addMessage(new LogMessage(0, ""));
        writer.addMessage(new LogMessage(0, "this goes through"));
        waitForWriterThread();

        assertEquals("mock: publish ARN",                       TEST_TOPIC_ARN,         mock.publishArn);
        assertEquals("mock: publish subject",                   TEST_SUBJECT,           mock.publishSubject);
        assertEquals("mock: last message written",              "this goes through",    mock.publishMessage.getMessage());

        assertStatisticsTotalMessagesSent(1);

        internalLogger.assertInternalWarningLog("discarded empty message");
    }


    @Test
    public void testDiscardOversizedMessage() throws Exception
    {
        mock = new MockSNSFacade(config, TEST_TOPIC_NAME);

        createWriter();
        assertTrue("writer is running", writer.isRunning());

        // using different characters at the end of the message makes JUnit output easer to read
        final String bigMessage         = StringUtil.repeat('X', SNS_MAX_MESSAGE_SIZE - 1) + "Y";
        final String biggerMessage      = bigMessage + "Z";

        writer.addMessage(new LogMessage(0, biggerMessage));
        writer.addMessage(new LogMessage(0, bigMessage));
        waitForWriterThread();

        assertEquals("mock: publish ARN",                       TEST_TOPIC_ARN,         mock.publishArn);
        assertEquals("mock: publish subject",                   TEST_SUBJECT,           mock.publishSubject);
        assertEquals("mock: last message written",              bigMessage,             mock.publishMessage.getMessage());

        assertStatisticsTotalMessagesSent(1);

        internalLogger.assertInternalWarningLog("discarded oversize message.*");
    }


    @Test
    public void testTruncateOversizedMessage() throws Exception
    {
        config.setTruncateOversizeMessages(true);
        mock = new MockSNSFacade(config, TEST_TOPIC_NAME);

        createWriter();
        assertTrue("writer is running", writer.isRunning());

        // using different characters at the end of the message makes JUnit output easer to read
        final String bigMessage         = StringUtil.repeat('X', SNS_MAX_MESSAGE_SIZE - 1) + "Y";
        final String biggerMessage      = bigMessage + "Z";

        writer.addMessage(new LogMessage(0, biggerMessage));
        waitForWriterThread();

        assertEquals("mock: publish ARN",                       TEST_TOPIC_ARN,         mock.publishArn);
        assertEquals("mock: publish subject",                   TEST_SUBJECT,           mock.publishSubject);
        assertEquals("mock: last message written",              bigMessage,             mock.publishMessage.getMessage());

        assertStatisticsTotalMessagesSent(1);

        internalLogger.assertInternalWarningLog("truncated oversize message.*");
    }


//    @Test
//    public void testShutdown() throws Exception
//    {
//        // this test is the only place that we expicitly test shutdown logic, to avoid cluttering
//        // the "operation" tests; it's otherwise identical to the "by name" test
//
//        // it actually tests functionality in AbstractAppender, but I've replicated for all concrete
//        // subclasses simply because it's a key piece of functionality
//
//        config.setTopicName(TEST_TOPIC_NAME);
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
//        assertEquals("publish: invocation count",   1,                  mock.publishInvocationCount);
//        assertEquals("publish: arn",                TEST_TOPIC_ARN,     mock.lastPublishArn);
//        assertEquals("publish: subject",            null,               mock.lastPublishSubject);
//        assertEquals("publish: body",               "message one",      mock.lastPublishMessage);
//
//        // another call to stop should be ignored -- sleep to ensure times would be different
//        Thread.sleep(100);
//        writer.stop();
//        assertEquals("second call to stop() should be no-op", shutdownTime, getShutdownTime());
//
//        joinWriterThread();
//
//        assertEquals("shutdown: invocation count",  1,                  mock.shutdownInvocationCount);
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
//        config.setTopicName(TEST_TOPIC_NAME);
//
//        // appender is expected to set batch delay in synchronous mode
//        config.setBatchDelay(1);
//
//        // we just have one thread, so don't want any locks getting in the way
//        mock.disableThreadSynchronization();
//
//        writer = (SNSLogWriter)mock.newWriterFactory().newLogWriter(config, stats, internalLogger);
//        messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);
//
//        assertEquals("before init, stats: topic name",          TEST_TOPIC_NAME,        stats.getActualTopicName());
//        assertNull("before init, stats: topic ARN",                                     stats.getActualTopicArn());
//
//        writer.initialize();
//
//        assertEquals("after init, invocations of listTopics",   1,                      mock.listTopicsInvocationCount);
//        assertEquals("after init, invocations of createTopic",  0,                      mock.createTopicInvocationCount);
//        assertEquals("after init, invocations of publish",      0,                      mock.publishInvocationCount);
//
//        assertNull("after init, stats: no errors",                                      stats.getLastError());
//        assertEquals("after init, stats: topic name",           TEST_TOPIC_NAME,        stats.getActualTopicName());
//        assertEquals("after init, stats: topic ARN",            TEST_TOPIC_ARN,         stats.getActualTopicArn());
//
//        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
//
//        assertEquals("message is waiting in queue",             1,                      messageQueue.queueSize());
//        assertEquals("publish: invocation count",               0,                      mock.publishInvocationCount);
//
//        writer.processBatch(System.currentTimeMillis());
//
//        assertEquals("after publish, invocation count",         1,                      mock.publishInvocationCount);
//        assertEquals("after publish, arn",                      TEST_TOPIC_ARN,         mock.lastPublishArn);
//        assertEquals("after publish, subject",                  null,                   mock.lastPublishSubject);
//        assertEquals("after publish, body",                     "message one",          mock.lastPublishMessage);
//
//        assertStatisticsTotalMessagesSent(1);
//        assertEquals("messages sent in batch",                  1,                      stats.getMessagesSentLastBatch());
//
//        assertEquals("shutdown not called before cleanup",      0,                      mock.shutdownInvocationCount);
//        writer.cleanup();
//        assertEquals("shutdown called after cleanup",           1,                      mock.shutdownInvocationCount);
//
//        internalLogger.assertInternalDebugLog();
//        internalLogger.assertInternalErrorLog();
//    }
}