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
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.lang.StringUtil;
import static net.sf.kdgcommons.test.StringAsserts.*;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.*;

import com.kdgregory.aws.logging.common.DefaultThreadFactory;
import com.kdgregory.aws.logging.common.DiscardAction;
import com.kdgregory.aws.logging.common.LogMessage;
import com.kdgregory.aws.logging.common.MessageQueue;
import com.kdgregory.aws.logging.sns.SNSAppenderStatistics;
import com.kdgregory.aws.logging.sns.SNSLogWriter;
import com.kdgregory.aws.logging.sns.SNSWriterConfig;
import com.kdgregory.aws.logging.testhelpers.TestableInternalLogger;
import com.kdgregory.aws.logging.testhelpers.TestingException;
import com.kdgregory.aws.logging.testhelpers.sns.MockSNSClient;


/**
 *  Performs mock-client testing of the SNS writer.
 *
 *  TODO: add tests for endpoint configuration.
 */
public class TestSNSLogWriter
{
    private final static String TEST_TOPIC_NAME = "example";
    private final static String TEST_TOPIC_ARN  = "arn:aws:sns:us-east-1:123456789012:example";

//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

    /**
     *  Default writer config -- very little is defaulted.
     */
    private SNSWriterConfig config = new SNSWriterConfig(
        null,                   // topicName
        null,                   // topicArn
        false,                  // autoCreate
        null,                   // subject
        1000,                   // discardThreshold
        DiscardAction.oldest,
        null,                   // clientFactoryMethod
        null);                  // clientEndpoint


    /**
     *  An appender statistics object, so that we don't have to create for
     *  each test.
     */
    private SNSAppenderStatistics stats = new SNSAppenderStatistics();


    /**
     *  Used to accumulate logging messages from the writer.
     */
    private TestableInternalLogger internalLogger = new TestableInternalLogger();


    /**
     *  The default mock object; there's only one topic that it knows about.
     */
    private MockSNSClient mock = new MockSNSClient(
                                    TEST_TOPIC_NAME,
                                    Arrays.asList(TEST_TOPIC_NAME));


    /**
     *  This will be assigned by createWriter();
     */
    private SNSLogWriter writer;


    /**
     *  Extracted from the writer created by createWriter().
     */
    private MessageQueue messageQueue;


    /**
     *  This will be set by the writer thread's uncaught exception handler. It
     *  should never happen with these tests.
     */
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
    private SNSLogWriter createWriter()
    throws Exception
    {
        writer = (SNSLogWriter)mock.newWriterFactory(internalLogger).newLogWriter(config, stats);
        messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

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

    private static MockSNSClient staticFactoryMock = null;

    public static AmazonSNS createMockClient()
    {
        staticFactoryMock = new MockSNSClient(TEST_TOPIC_NAME, Arrays.asList(TEST_TOPIC_NAME));
        return staticFactoryMock.createClient();
    }

//----------------------------------------------------------------------------
//  JUnit scaffolding
//----------------------------------------------------------------------------

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
        // we don't want to initialize the writer, so will create it outselves

        writer = new SNSLogWriter(config, stats, internalLogger);
        messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        // the writer uses the config object for all of its configuration,
        // so we just look for the pieces that it exposes or passes on

        assertEquals("writer batch delay",                      1L,                     writer.getBatchDelay());
        assertEquals("message queue discard policy",            DiscardAction.oldest,   messageQueue.getDiscardAction());
        assertEquals("message queue discard threshold",         1000,                   messageQueue.getDiscardThreshold());
    }


    @Test
    public void testOperationByName() throws Exception
    {
        config.topicName = TEST_TOPIC_NAME;
        createWriter();

        assertEquals("after init, invocations of listTopics",   1,                      mock.listTopicsInvocationCount);
        assertEquals("after init, invocations of createTopic",  0,                      mock.createTopicInvocationCount);
        assertEquals("after init, invocations of publish",      0,                      mock.publishInvocationCount);

        assertNull("after init, stats: no errors",                                      stats.getLastError());
        assertEquals("after init, stats: topic name",           TEST_TOPIC_NAME,        stats.getActualTopicName());
        assertEquals("after init, stats: topic ARN",            TEST_TOPIC_ARN,         stats.getActualTopicArn());

        // the SNS writer should use batch sizes of 1, regardless of config, so we'll
        // add both messages at the same time, then wait separately to verify batching

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));

        mock.allowWriterThread();

        assertEquals("first publish, invocation count",         1,                      mock.publishInvocationCount);
        assertEquals("first publish, arn",                      TEST_TOPIC_ARN,         mock.lastPublishArn);
        assertEquals("first publish, subject",                  null,                   mock.lastPublishSubject);
        assertEquals("first publish, body",                     "message one",          mock.lastPublishMessage);
        assertEquals("first publish, sent statistics",          1,                      stats.getMessagesSent());

        mock.allowWriterThread();

        assertEquals("second publish, invocation count",        2,                      mock.publishInvocationCount);
        assertEquals("second publish, arn",                     TEST_TOPIC_ARN,         mock.lastPublishArn);
        assertEquals("second publish, subject",                 null,                   mock.lastPublishSubject);
        assertEquals("second publish, body",                    "message two",          mock.lastPublishMessage);
        assertEquals("second publish, sent statistics",         2,                      stats.getMessagesSent());

        assertEquals("debug message count",                     0,                      internalLogger.debugMessages.size());
        assertEquals("error message count",                     0,                      internalLogger.errorMessages.size());
    }


    @Test
    public void testOperationByNameMultipleTopicLists() throws Exception
    {
        mock = new MockSNSClient(TEST_TOPIC_NAME, Arrays.asList("argle", "bargle", TEST_TOPIC_NAME), 2);

        config.topicName = TEST_TOPIC_NAME;
        createWriter();

        assertEquals("after init, invocations of listTopics",   2,                      mock.listTopicsInvocationCount);
        assertEquals("after init, invocations of createTopic",  0,                      mock.createTopicInvocationCount);
        assertEquals("after init, invocations of publish",      0,                      mock.publishInvocationCount);

        assertNull("after init, stats: no errors",                                      stats.getLastError());
        assertEquals("after init, stats: topic name",           TEST_TOPIC_NAME,        stats.getActualTopicName());
        assertEquals("after init, stats: topic ARN",            TEST_TOPIC_ARN,         stats.getActualTopicArn());

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        assertEquals("after publish, invocation count",         1,                      mock.publishInvocationCount);
        assertEquals("after publish, arn",                      TEST_TOPIC_ARN,         mock.lastPublishArn);
        assertEquals("after publish, subject",                  null,                   mock.lastPublishSubject);
        assertEquals("after publish, body",                     "message one",          mock.lastPublishMessage);
        assertEquals("after publish, sent statistics",          1,                      stats.getMessagesSent());

        assertEquals("log: debug message count",                0,                      internalLogger.debugMessages.size());
        assertEquals("log: error message count",                0,                      internalLogger.errorMessages.size());
    }


    @Test
    public void testOperationByNameNoExistingTopic() throws Exception
    {
        mock = new MockSNSClient(TEST_TOPIC_NAME, Arrays.asList("argle", "bargle"));

        config.topicName = TEST_TOPIC_NAME;
        createWriter();

        assertEquals("invocations of listTopics",           1,                      mock.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",          0,                      mock.createTopicInvocationCount);
        assertEquals("message queue set to discard all",    0,                      messageQueue.getDiscardThreshold());
        assertEquals("message queue set to discard all",    DiscardAction.oldest,   messageQueue.getDiscardAction());

        assertEquals("stats: topic name",                   null,                   stats.getActualTopicName());
        assertEquals("stats: topic ARN",                    null,                   stats.getActualTopicArn());
        assertRegex("stats: error message",                 ".*not exist.*" + TEST_TOPIC_NAME + ".*",
                                                            stats.getLastErrorMessage());

        assertEquals("log: debug message count",            0,                      internalLogger.debugMessages.size());
        assertEquals("log: error message count",            1,                      internalLogger.errorMessages.size());
        assertRegex("log: error message",                   ".*not exist.*" + TEST_TOPIC_NAME + ".*",
                                                            internalLogger.errorMessages.get(0));
    }


    @Test
    public void testOperationByNameNoExistingTopicAutoCreate() throws Exception
    {
        mock = new MockSNSClient(TEST_TOPIC_NAME, Arrays.asList("argle", "bargle"));

        config.topicName = TEST_TOPIC_NAME;
        config.autoCreate = true;
        createWriter();

        assertEquals("after init, invocations of listTopics",   1,                      mock.listTopicsInvocationCount);
        assertEquals("after init, invocations of createTopic",  1,                      mock.createTopicInvocationCount);
        assertEquals("after init, invocations of publish",      0,                      mock.publishInvocationCount);

        assertNull("after init, stats: no errors",                                      stats.getLastError());
        assertEquals("after init, stats: topic name",           TEST_TOPIC_NAME,        stats.getActualTopicName());
        assertEquals("after init, stats: topic ARN",            TEST_TOPIC_ARN,         stats.getActualTopicArn());

        // the SNS writer should use batch sizes of 1, regardless of config, so we'll
        // add both messages at the same time, then wait separately to verify batching

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        assertEquals("after publish, invocation count",         1,                      mock.publishInvocationCount);
        assertEquals("after publish, arn",                      TEST_TOPIC_ARN,         mock.lastPublishArn);
        assertEquals("after publish, subject",                  null,                   mock.lastPublishSubject);
        assertEquals("after publish, body",                     "message one",          mock.lastPublishMessage);
        assertEquals("after publish, sent statistics",          1,                      stats.getMessagesSent());

        assertEquals("log: debug message count",                1,                      internalLogger.debugMessages.size());
        assertEquals("log: error message count",                0,                      internalLogger.errorMessages.size());
        assertRegex("log: indicates create",                    ".*creat.*" + TEST_TOPIC_NAME + ".*",
                                                                internalLogger.debugMessages.get(0));
    }


    @Test
    public void testOperationByArn() throws Exception
    {
        config.topicArn = TEST_TOPIC_ARN;
        createWriter();

        assertEquals("after init, invocations of listTopics",   1,                      mock.listTopicsInvocationCount);
        assertEquals("after init, invocations of createTopic",  0,                      mock.createTopicInvocationCount);
        assertEquals("after init, invocations of publish",      0,                      mock.publishInvocationCount);

        assertNull("after init, stats: no errors",                                      stats.getLastError());
        assertEquals("after init, stats: topic name",           TEST_TOPIC_NAME,        stats.getActualTopicName());
        assertEquals("after init, stats: topic ARN",            TEST_TOPIC_ARN,         stats.getActualTopicArn());

        // the SNS writer should use batch sizes of 1, regardless of config, so we'll
        // add both messages at the same time, then wait separately to verify batching

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));

        mock.allowWriterThread();

        assertEquals("first publish, invocation count",         1,                      mock.publishInvocationCount);
        assertEquals("first publish, arn",                      TEST_TOPIC_ARN,         mock.lastPublishArn);
        assertEquals("first publish, subject",                  null,                   mock.lastPublishSubject);
        assertEquals("first publish, body",                     "message one",          mock.lastPublishMessage);
        assertEquals("first publish, sent statistics",          1,                      stats.getMessagesSent());

        mock.allowWriterThread();

        assertEquals("second publish, invocation count",        2,                      mock.publishInvocationCount);
        assertEquals("second publish, arn",                     TEST_TOPIC_ARN,         mock.lastPublishArn);
        assertEquals("second publish, subject",                 null,                   mock.lastPublishSubject);
        assertEquals("second publish, body",                    "message two",          mock.lastPublishMessage);
        assertEquals("second publish, sent statistics",         2,                      stats.getMessagesSent());

        assertEquals("log: debug message count",                0,                      internalLogger.debugMessages.size());
        assertEquals("log: error message count",                0,                      internalLogger.errorMessages.size());
    }


    @Test
    public void testOperationByArnMultipleTopicLists() throws Exception
    {
        mock = new MockSNSClient(TEST_TOPIC_NAME, Arrays.asList("argle", "bargle", TEST_TOPIC_NAME), 2);

        config.topicArn = TEST_TOPIC_ARN;
        createWriter();

        assertEquals("after init, invocations of listTopics",   2,                      mock.listTopicsInvocationCount);
        assertEquals("after init, invocations of createTopic",  0,                      mock.createTopicInvocationCount);
        assertEquals("after init, invocations of publish",      0,                      mock.publishInvocationCount);

        assertNull("after init, stats: no errors",                                      stats.getLastError());
        assertEquals("after init, stats: topic name",           TEST_TOPIC_NAME,        stats.getActualTopicName());
        assertEquals("after init, stats: topic ARN",            TEST_TOPIC_ARN,         stats.getActualTopicArn());

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        assertEquals("after publish, invocation count",         1,                      mock.publishInvocationCount);
        assertEquals("after publish, arn",                      TEST_TOPIC_ARN,         mock.lastPublishArn);
        assertEquals("after publish, subject",                  null,                   mock.lastPublishSubject);
        assertEquals("after publish, body",                     "message one",          mock.lastPublishMessage);
        assertEquals("after publish, sent statistics",          1,                      stats.getMessagesSent());

        assertEquals("log: debug message count",                0,                      internalLogger.debugMessages.size());
        assertEquals("log: error message count",                0,                      internalLogger.errorMessages.size());
    }


    @Test
    public void testOperationByArnNoExistingTopic() throws Exception
    {
        mock = new MockSNSClient(TEST_TOPIC_NAME, Arrays.asList("argle", "bargle"));

        config.topicArn = TEST_TOPIC_ARN;
        createWriter();

        assertEquals("invocations of listTopics",           1,                      mock.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",          0,                      mock.createTopicInvocationCount);
        assertEquals("message queue set to discard all",    0,                      messageQueue.getDiscardThreshold());
        assertEquals("message queue set to discard all",    DiscardAction.oldest,   messageQueue.getDiscardAction());

        assertEquals("stats: topic name",                   null,                   stats.getActualTopicName());
        assertEquals("stats: topic ARN",                    null,                   stats.getActualTopicArn());
        assertRegex("stats: error message",                 ".*not exist.*" + TEST_TOPIC_ARN + ".*",
                                                            stats.getLastErrorMessage());

        assertEquals("log: debug message count",            0,                      internalLogger.debugMessages.size());
        assertEquals("log: error message count",            1,                      internalLogger.errorMessages.size());
        assertRegex("log: error message",                   ".*not exist.*" + TEST_TOPIC_ARN + ".*",
                                                            internalLogger.errorMessages.get(0));
    }


    @Test
    public void testSubject() throws Exception
    {
        config.topicName = TEST_TOPIC_NAME;
        config.subject = "example";
        createWriter();

        assertEquals("after init, invocations of listTopics",   1,                      mock.listTopicsInvocationCount);
        assertEquals("after init, invocations of createTopic",  0,                      mock.createTopicInvocationCount);
        assertEquals("after init, invocations of publish",      0,                      mock.publishInvocationCount);

        assertNull("after init, stats: no errors",                                      stats.getLastError());
        assertEquals("after init, stats: topic name",           TEST_TOPIC_NAME,        stats.getActualTopicName());
        assertEquals("after init, stats: topic ARN",            TEST_TOPIC_ARN,         stats.getActualTopicArn());

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        assertEquals("first publish, invocation count",         1,                      mock.publishInvocationCount);
        assertEquals("first publish, arn",                      TEST_TOPIC_ARN,         mock.lastPublishArn);
        assertEquals("first publish, subject",                  "example",              mock.lastPublishSubject);
        assertEquals("first publish, body",                     "message one",          mock.lastPublishMessage);
        assertEquals("first publish, sent statistics",          1,                      stats.getMessagesSent());

        assertEquals("log: debug message count",                0,                      internalLogger.debugMessages.size());
        assertEquals("log: error message count",                0,                      internalLogger.errorMessages.size());
    }


    @Test
    public void testInvalidTopicName() throws Exception
    {
        config.topicName = "x%$!";
        createWriter();

        String initializationMessage = stats.getLastErrorMessage();

        assertRegex("initialization message (was: " + initializationMessage + ")",
                                                                ".*invalid.*topic.*",  initializationMessage);
        assertEquals("invocations of listTopics",               0,                      mock.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",              0,                      mock.createTopicInvocationCount);
        assertEquals("topic name, from statistics",             null,                   stats.getActualTopicName());
        assertEquals("topic ARN, from statistics",              null,                   stats.getActualTopicArn());
        assertEquals("message queue set to discard all",        0,                      messageQueue.getDiscardThreshold());
        assertEquals("message queue set to discard all",        DiscardAction.oldest,   messageQueue.getDiscardAction());

        assertEquals("debug message count",                     0,                      internalLogger.debugMessages.size());
        assertEquals("error message count",                     1,                      internalLogger.errorMessages.size());
        assertRegex("error message",                            ".*invalid.*topic.*",  internalLogger.errorMessages.get(0));
    }


    @Test
    public void testExceptionInInitializer() throws Exception
    {
        mock = new MockSNSClient(TEST_TOPIC_NAME, Arrays.asList("somethingElse"))
        {
            @Override
            protected ListTopicsResult listTopics(ListTopicsRequest request)
            {
                throw new TestingException("arbitrary failure");
            }
        };

        config.topicName = TEST_TOPIC_NAME;
        createWriter();

        String errorMessage = stats.getLastErrorMessage();

        assertEquals("invocation count: listTopics",                    1,                          mock.listTopicsInvocationCount);

        assertRegex("stats: error message (was: " + errorMessage + ")", "unable to configure.*",    errorMessage);
        assertEquals("stats: exception",                                TestingException.class,     stats.getLastError().getClass());
        assertEquals("stats: exception message",                        "arbitrary failure",        stats.getLastError().getMessage());
        assertTrue("stats: exception trace",                                                        stats.getLastErrorStacktrace().size() > 0);
        assertNotNull("stats: exception timestamp",                                                 stats.getLastErrorTimestamp());

        assertEquals("log: debug message count",                        0,                          internalLogger.debugMessages.size());
        assertEquals("log: error message count",                        1,                          internalLogger.errorMessages.size());
        assertRegex("log: error message",                               "unable to configure.*",    internalLogger.errorMessages.get(0));
    }


    @Test
    public void testBatchErrorHandling() throws Exception
    {
        mock = new MockSNSClient(TEST_TOPIC_NAME, Arrays.asList(TEST_TOPIC_NAME))
        {
            @Override
            protected PublishResult publish(PublishRequest request)
            {
                // first one fails, subsequent succeeds
                if (publishInvocationCount % 3 == 1)
                {
                    throw new TestingException("no notifications for you!");
                }
                else
                {
                    return super.publish(request);
                }
            }
        };

        config.topicName = TEST_TOPIC_NAME;
        createWriter();

        assertNull("no initialization error",                                       stats.getLastError());
        assertEquals("stats: topic name",                   TEST_TOPIC_NAME,        stats.getActualTopicName());
        assertEquals("stats: topic ARN",                    TEST_TOPIC_ARN,         stats.getActualTopicArn());
        assertEquals("invocations of listTopics",           1,                      mock.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",          0,                      mock.createTopicInvocationCount);
        assertEquals("invocations of publish",              0,                      mock.publishInvocationCount);

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));

        // the first attempt should fail, so we'll wait for a second

        mock.allowWriterThread();
        mock.allowWriterThread();

        assertEquals("publish, invocation count",           2,                      mock.publishInvocationCount);
        assertEquals("publish, arn",                        TEST_TOPIC_ARN,         mock.lastPublishArn);
        assertEquals("publish, subject",                    null,                   mock.lastPublishSubject);
        assertEquals("publish, body",                       "message one",          mock.lastPublishMessage);
        assertEquals("publish, sent statistics",            1,                      stats.getMessagesSent());

        assertNotEmpty("statistics retains error message",                          stats.getLastErrorMessage());
        assertNotNull("statistics retains error timestamp",                         stats.getLastErrorMessage());
        assertNotNull("statistics retains error throwable",                         stats.getLastError());
        assertTrue("statistics retains error trace",                                stats.getLastErrorStacktrace().size() > 0);

        assertEquals("log: debug message count",            0,                          internalLogger.debugMessages.size());
        assertEquals("log: error message count",            0,                          internalLogger.errorMessages.size());
    }


    @Test
    public void testDiscardOldest() throws Exception
    {
        config.discardAction = DiscardAction.oldest;
        config.discardThreshold = 10;

        // this test doesn't need a background thread running

        writer = new SNSLogWriter(config, stats, internalLogger);
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

        writer = new SNSLogWriter(config, stats, internalLogger);
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

        writer = new SNSLogWriter(config, stats, internalLogger);
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

        writer = new SNSLogWriter(config, stats, internalLogger);
        messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

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
        config.topicName = TEST_TOPIC_NAME;
        config.clientFactoryMethod = getClass().getName() + ".createMockClient";

        // we have to manually initialize this writer so that it won't get the default mock client

        writer = new SNSLogWriter(config, stats, internalLogger);
        new DefaultThreadFactory().startLoggingThread(writer, defaultUncaughtExceptionHandler);

        for (int ii = 0 ; ii < 100 ; ii++)
        {
            if (writer.isInitializationComplete())
                break;
            else
                Thread.sleep(50);
        }

        assertTrue("writer successfully initialized",                                       writer.isInitializationComplete());
        assertNotNull("factory called (local flag)",                                        staticFactoryMock);
        assertEquals("factory called (writer flag)",            config.clientFactoryMethod, writer.getClientFactoryUsed());

        assertEquals("invocations of listTopics",               1,                          staticFactoryMock.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",              0,                          staticFactoryMock.createTopicInvocationCount);
        assertEquals("invocations of publish",                  0,                          staticFactoryMock.publishInvocationCount);

        assertNull("stats: no initialization message",                                      stats.getLastErrorMessage());
        assertNull("stats: no initialization error",                                        stats.getLastError());
        assertEquals("stats: topic name",                       TEST_TOPIC_NAME,            stats.getActualTopicName());
        assertEquals("stats: topic ARN",                        TEST_TOPIC_ARN,             stats.getActualTopicArn());

        assertRegex("log: debug message indicating factory",    ".*created client from factory.*" + getClass().getName() + ".*",
                                                                internalLogger.debugMessages.get(0));
    }
}
