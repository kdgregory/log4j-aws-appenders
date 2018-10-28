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

import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.ClassUtil;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.*;

import com.kdgregory.logging.aws.sns.SNSWriterStatistics;
import com.kdgregory.logging.aws.sns.SNSLogWriter;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;
import com.kdgregory.logging.aws.sns.SNSWriterFactory;
import com.kdgregory.logging.aws.testhelpers.TestingException;
import com.kdgregory.logging.aws.testhelpers.sns.MockSNSClient;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.common.util.MessageQueue;


/**
 *  Performs mock-client testing of the SNS writer.
 */
public class TestSNSLogWriter
extends AbstractLogWriterTest<SNSLogWriter,SNSWriterConfig,SNSWriterStatistics,AmazonSNS>
{
    private final static String TEST_TOPIC_NAME = "example";
    private final static String TEST_TOPIC_ARN  = "arn:aws:sns:us-east-1:123456789012:example";

//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

    /**
     *  Rather than re-create each time, we initialize in setUp(), replace in
     *  tests that need to do so.
     */
    private MockSNSClient mock;


    /**
     *  Constructs and initializes a writer on a background thread, waiting for
     *  initialization to either complete or fail. Returns the writer.
     */
    private void createWriter()
    throws Exception
    {
        createWriter(mock.newWriterFactory());
    }


    // the following variable and function are used by testStaticClientFactory

    private static MockSNSClient staticFactoryMock;

    public static AmazonSNS createMockClient()
    {
        staticFactoryMock = new MockSNSClient(TEST_TOPIC_NAME, Arrays.asList(TEST_TOPIC_NAME));
        return staticFactoryMock.createClient();
    }

//----------------------------------------------------------------------------
//  JUnit scaffolding
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        config = new SNSWriterConfig(
                null,                   // topicName
                null,                   // topicArn
                false,                  // autoCreate
                null,                   // subject
                1000,                   // discardThreshold
                DiscardAction.oldest,
                null,                   // clientFactoryMethod
                null);                  // clientEndpoint

        stats = new SNSWriterStatistics();

        mock = new MockSNSClient(
                TEST_TOPIC_NAME,
                Arrays.asList(TEST_TOPIC_NAME));

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
        // we don't want to initialize the writer, so will create it outselves

        writer = new SNSLogWriter(config, stats, internalLogger, dummyClientFactory);
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

        assertEquals("first publish, invocation count",                         1,                  mock.publishInvocationCount);
        assertEquals("first publish, arn",                                      TEST_TOPIC_ARN,     mock.lastPublishArn);
        assertEquals("first publish, subject",                                  null,               mock.lastPublishSubject);
        assertEquals("first publish, body",                                     "message one",      mock.lastPublishMessage);
        assertStatisticsMessagesSent("first publish, messages sent per stats",  1);

        mock.allowWriterThread();

        assertEquals("second publish, invocation count",                        2,                  mock.publishInvocationCount);
        assertEquals("second publish, arn",                                     TEST_TOPIC_ARN,     mock.lastPublishArn);
        assertEquals("second publish, subject",                                 null,               mock.lastPublishSubject);
        assertEquals("second publish, body",                                    "message two",      mock.lastPublishMessage);
        assertStatisticsMessagesSent("second publish, messages sent per stats", 2);

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalErrorLog();
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

        assertStatisticsMessagesSent(1);

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalErrorLog();
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

        assertStatisticsErrorMessage(".*not exist.*" + TEST_TOPIC_NAME + ".*");
        assertNull("stats: topic name",                     stats.getActualTopicName());
        assertNull("stats: topic ARN",                      stats.getActualTopicArn());

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalErrorLog(".*not exist.*" + TEST_TOPIC_NAME + ".*");
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

        assertStatisticsMessagesSent(1);

        internalLogger.assertInternalDebugLog(".*creat.*" + TEST_TOPIC_NAME + ".*");
        internalLogger.assertInternalErrorLog();
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

        assertEquals("first publish, invocation count",                         1,                      mock.publishInvocationCount);
        assertEquals("first publish, arn",                                      TEST_TOPIC_ARN,         mock.lastPublishArn);
        assertEquals("first publish, subject",                                  null,                   mock.lastPublishSubject);
        assertEquals("first publish, body",                                     "message one",          mock.lastPublishMessage);
        assertStatisticsMessagesSent("first publish, messages sent per stats",  1);

        mock.allowWriterThread();

        assertEquals("second publish, invocation count",                        2,                      mock.publishInvocationCount);
        assertEquals("second publish, arn",                                     TEST_TOPIC_ARN,         mock.lastPublishArn);
        assertEquals("second publish, subject",                                 null,                   mock.lastPublishSubject);
        assertEquals("second publish, body",                                    "message two",          mock.lastPublishMessage);
        assertStatisticsMessagesSent("second publish, messages sent per stats", 2);

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalErrorLog();
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

        assertStatisticsMessagesSent(1);

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalErrorLog();
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

        assertStatisticsErrorMessage(".*not exist.*" + TEST_TOPIC_ARN + ".*");
        assertNull("stats: topic name",                     stats.getActualTopicName());
        assertNull("stats: topic ARN",                      stats.getActualTopicArn());

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalErrorLog(".*not exist.*" + TEST_TOPIC_ARN + ".*");
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

        assertEquals("after publish, invocation count",         1,                      mock.publishInvocationCount);
        assertEquals("after publish, arn",                      TEST_TOPIC_ARN,         mock.lastPublishArn);
        assertEquals("after publish, subject",                  "example",              mock.lastPublishSubject);
        assertEquals("after publish, body",                     "message one",          mock.lastPublishMessage);
        assertStatisticsMessagesSent("after publish, messages sent", 1);

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testInvalidTopicName() throws Exception
    {
        config.topicName = "x%$!";
        createWriter();

        assertStatisticsErrorMessage("invalid topic name: .*"); // invalid name has special regex characters

        assertEquals("invocations of listTopics",               0,                      mock.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",              0,                      mock.createTopicInvocationCount);
        assertNull("topic name, from statistics",                                       stats.getActualTopicName());
        assertNull("topic ARN, from statistics",                                        stats.getActualTopicArn());
        assertEquals("message queue set to discard all",        0,                      messageQueue.getDiscardThreshold());
        assertEquals("message queue set to discard all",        DiscardAction.oldest,   messageQueue.getDiscardAction());

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalErrorLog(".*invalid.*topic.*");
    }


    @Test
    public void testInitializationErrorHandling() throws Exception
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

        assertEquals("invocation count: listTopics",                    1,                          mock.listTopicsInvocationCount);

        assertStatisticsErrorMessage("unable to configure.*");
        assertStatisticsException(TestingException.class, "arbitrary failure");

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalErrorLog("unable to configure.*");
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
                    throw new TestingException("no notifications for you");
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

        assertStatisticsMessagesSent(1);

        assertStatisticsErrorMessage(".*no notifications for you");
        assertStatisticsException(TestingException.class, "no notifications for you");

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testDiscardOldest() throws Exception
    {
        config.discardAction = DiscardAction.oldest;
        config.discardThreshold = 10;

        // this test doesn't need a background thread running

        writer = new SNSLogWriter(config, stats, internalLogger, dummyClientFactory);
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

        writer = new SNSLogWriter(config, stats, internalLogger, dummyClientFactory);
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

        writer = new SNSLogWriter(config, stats, internalLogger, dummyClientFactory);
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

        writer = new SNSLogWriter(config, stats, internalLogger, dummyClientFactory);
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

        createWriter(new SNSWriterFactory());

        assertTrue("writer successfully initialized",                                       writer.isInitializationComplete());
        assertNotNull("factory called (local flag)",                                        staticFactoryMock);

        assertEquals("invocations of listTopics",               1,                          staticFactoryMock.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",              0,                          staticFactoryMock.createTopicInvocationCount);
        assertEquals("invocations of publish",                  0,                          staticFactoryMock.publishInvocationCount);

        assertNull("stats: no initialization message",                                      stats.getLastErrorMessage());
        assertNull("stats: no initialization error",                                        stats.getLastError());
        assertEquals("stats: topic name",                       TEST_TOPIC_NAME,            stats.getActualTopicName());
        assertEquals("stats: topic ARN",                        TEST_TOPIC_ARN,             stats.getActualTopicArn());

        internalLogger.assertInternalDebugLog(".*created client from factory.*");
        internalLogger.assertInternalErrorLog();
    }
}
