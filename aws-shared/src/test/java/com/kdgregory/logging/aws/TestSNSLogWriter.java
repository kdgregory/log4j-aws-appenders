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
import net.sf.kdgcommons.lang.StringUtil;
import static net.sf.kdgcommons.test.NumericAsserts.*;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.*;

import com.kdgregory.logging.aws.sns.SNSWriterStatistics;
import com.kdgregory.logging.aws.sns.SNSLogWriter;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;
import com.kdgregory.logging.aws.sns.SNSWriterFactory;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.common.util.MessageQueue;
import com.kdgregory.logging.testhelpers.TestingException;
import com.kdgregory.logging.testhelpers.sns.MockSNSClient;


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
                null,                   // subject
                false,                  // autoCreate
                false,                  // truncateOversizeMessages
                1000,                   // discardThreshold
                DiscardAction.oldest,   // discardAction
                null,                   // clientFactoryMethod
                null,                   // assumedRole
                null,                   // clientRegion
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
        // note: the client endpoint configuration is ignored when creating a writer
        config = new SNSWriterConfig(
                "topic-name",                       // topicName
                "topic-arn",                        // topicArn
                "subject",                          // subject
                true,                               // autoCreate
                true,                               // truncateOversizeMessages
                123,                                // discardThreshold
                DiscardAction.newest,               // discardAction
                "com.example.factory.Method",       // clientFactoryMethod
                "SomeRole",                         // assumedRole
                "us-west-1",                        // clientRegion
                "sns.us-west-1.amazonaws.com");     // clientEndpoint

        assertEquals("topic name",                  "topic-name",                   config.topicName);
        assertEquals("log stream name",             "topic-arn",                    config.topicArn);
        assertEquals("subject",                     "subject",                      config.subject);
        assertTrue("auto-create",                                                   config.autoCreate);
        assertTrue("truncate large messages",                                       config.truncateOversizeMessages);
        assertEquals("factory method",              "com.example.factory.Method",   config.clientFactoryMethod);
        assertEquals("assumed role",                "SomeRole",                     config.assumedRole);
        assertEquals("client region",               "us-west-1",                    config.clientRegion);
        assertEquals("client endpoint",             "sns.us-west-1.amazonaws.com",  config.clientEndpoint);

        writer = new SNSLogWriter(config, stats, internalLogger, dummyClientFactory);
        messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        // the writer uses the config object for all of its configuration,
        // so we just look for the pieces that it exposes or passes on

        assertEquals("writer batch delay",                      1L,                     writer.getBatchDelay());
        assertEquals("message queue discard policy",            DiscardAction.newest,   messageQueue.getDiscardAction());
        assertEquals("message queue discard threshold",         123,                    messageQueue.getDiscardThreshold());
    }


    @Test
    public void testOperationByName() throws Exception
    {
        config.topicName = TEST_TOPIC_NAME;
        createWriter();

        assertNull("after init, stats: no errors",                                      stats.getLastError());
        assertEquals("after init, stats: topic name",           TEST_TOPIC_NAME,        stats.getActualTopicName());
        assertEquals("after init, stats: topic ARN",            TEST_TOPIC_ARN,         stats.getActualTopicArn());

        assertEquals("after init, invocations of listTopics",   1,                      mock.listTopicsInvocationCount);
        assertEquals("after init, invocations of createTopic",  0,                      mock.createTopicInvocationCount);
        assertEquals("after init, invocations of publish",      0,                      mock.publishInvocationCount);

        // the SNS writer should use batch sizes of 1, regardless of config, so we'll
        // add both messages at the same time, then wait separately to verify batching

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));

        mock.allowWriterThread();

        assertEquals("first publish, invocation count",                                         1,                  mock.publishInvocationCount);
        assertEquals("first publish, arn",                                                      TEST_TOPIC_ARN,     mock.lastPublishArn);
        assertEquals("first publish, subject",                                                  null,               mock.lastPublishSubject);
        assertEquals("first publish, body",                                                     "message one",      mock.lastPublishMessage);

        assertStatisticsTotalMessagesSent("statistics: total messages after first publish",     1);
        assertEquals("statistics: last batch messages after first publish",                     1,                  stats.getMessagesSentLastBatch());

        mock.allowWriterThread();

        assertEquals("second publish, invocation count",                                        2,                  mock.publishInvocationCount);
        assertEquals("second publish, arn",                                                     TEST_TOPIC_ARN,     mock.lastPublishArn);
        assertEquals("second publish, subject",                                                 null,               mock.lastPublishSubject);
        assertEquals("second publish, body",                                                    "message two",      mock.lastPublishMessage);

        assertStatisticsTotalMessagesSent("statistics: total messages after second publish",    2);
        assertEquals("statistics: last batch messages after second publish",                    1,                  stats.getMessagesSentLastBatch());

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "log writer initialization complete.*");
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

        assertStatisticsTotalMessagesSent(1);
        assertEquals("statistics: last batch messages",         1,                      stats.getMessagesSentLastBatch());

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "log writer initialization complete.*");
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
        assertNotNull("stats: topic name",                  stats.getActualTopicName());    // comes from configu
        assertNull("stats: topic ARN",                      stats.getActualTopicArn());     // would come from init

        internalLogger.assertInternalDebugLog("log writer starting.*");
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

        assertStatisticsTotalMessagesSent(1);

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              ".*creat.*" + TEST_TOPIC_NAME + ".*",
                                              "log writer initialization complete.*");
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

        assertEquals("first publish, invocation count",                                         1,                      mock.publishInvocationCount);
        assertEquals("first publish, arn",                                                      TEST_TOPIC_ARN,         mock.lastPublishArn);
        assertEquals("first publish, subject",                                                  null,                   mock.lastPublishSubject);
        assertEquals("first publish, body",                                                     "message one",          mock.lastPublishMessage);

        assertStatisticsTotalMessagesSent("statistics: total messages after first publish",     1);
        assertEquals("statistics: last batch messages after first publish",                     1,                  stats.getMessagesSentLastBatch());

        mock.allowWriterThread();

        assertEquals("second publish, invocation count",                                        2,                      mock.publishInvocationCount);
        assertEquals("second publish, arn",                                                     TEST_TOPIC_ARN,         mock.lastPublishArn);
        assertEquals("second publish, subject",                                                 null,                   mock.lastPublishSubject);
        assertEquals("second publish, body",                                                    "message two",          mock.lastPublishMessage);

        assertStatisticsTotalMessagesSent("statistics: total messages after second publish",    2);
        assertEquals("statistics: last batch messages after second publish",                    1,                  stats.getMessagesSentLastBatch());

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "log writer initialization complete.*");
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

        assertStatisticsTotalMessagesSent(1);

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "log writer initialization complete.*");
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
        assertNull("stats: topic name",                     stats.getActualTopicName());    // would come from init
        assertNotNull("stats: topic ARN",                   stats.getActualTopicArn());     // comes from config

        internalLogger.assertInternalDebugLog("log writer starting.*");
        internalLogger.assertInternalErrorLog(".*not exist.*" + TEST_TOPIC_ARN + ".*");
    }


    @Test
    public void testSubject() throws Exception
    {
        final String testSubject = "This is OK";

        config.topicName = TEST_TOPIC_NAME;
        config.subject = testSubject;
        createWriter();

        assertEquals("after init, invocations of listTopics",   1,                      mock.listTopicsInvocationCount);
        assertEquals("after init, invocations of createTopic",  0,                      mock.createTopicInvocationCount);
        assertEquals("after init, invocations of publish",      0,                      mock.publishInvocationCount);

        assertNull("after init, stats: no errors",                                      stats.getLastError());
        assertEquals("after init, stats: topic name",           TEST_TOPIC_NAME,        stats.getActualTopicName());
        assertEquals("after init, stats: topic ARN",            TEST_TOPIC_ARN,         stats.getActualTopicArn());
        assertEquals("after init, stats: subject",              testSubject,            stats.getActualSubject());

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        assertEquals("after publish, invocation count",         1,                      mock.publishInvocationCount);
        assertEquals("after publish, arn",                      TEST_TOPIC_ARN,         mock.lastPublishArn);
        assertEquals("after publish, subject",                  testSubject,            mock.lastPublishSubject);
        assertEquals("after publish, body",                     "message one",          mock.lastPublishMessage);
        assertStatisticsTotalMessagesSent("after publish, messages sent", 1);

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "log writer initialization complete.*");
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testChangeSubject() throws Exception
    {
        final String firstSubject = "First Subject";
        final String secondSubject = "Second Subject";

        config.topicName = TEST_TOPIC_NAME;
        config.subject = firstSubject;
        createWriter();

        assertEquals("after init, config: subject",             firstSubject,           config.subject);
        assertEquals("after init, stats: subject",              firstSubject,           stats.getActualSubject());
        assertNull("after init, stats: no errors",                                      stats.getLastError());

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));
        mock.allowWriterThread();

        assertEquals("after first publish, invocation count",   1,                      mock.publishInvocationCount);
        assertEquals("after first publish, subject",            firstSubject,           mock.lastPublishSubject);
        assertEquals("after first publish, body",               "message one",          mock.lastPublishMessage);
        assertStatisticsTotalMessagesSent("after first publish, messages sent", 1);

        writer.setSubject(secondSubject);

        assertEquals("after subject change, config",            secondSubject,          config.subject);
        assertEquals("after subject change, stats",             secondSubject,          stats.getActualSubject());

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message two"));
        mock.allowWriterThread();

        assertEquals("after second publish, invocation count",  2,                      mock.publishInvocationCount);
        assertEquals("after second publish, subject",           secondSubject,           mock.lastPublishSubject);
        assertEquals("after second publish, body",              "message two",          mock.lastPublishMessage);
        assertStatisticsTotalMessagesSent("after first publish, messages sent", 2);

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "log writer initialization complete.*");
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
        assertNotNull("topic name, from statistics",                                    stats.getActualTopicName());        // comes from config
        assertNull("topic ARN, from statistics",                                        stats.getActualTopicArn());         // would come from init
        assertEquals("message queue set to discard all",        0,                      messageQueue.getDiscardThreshold());
        assertEquals("message queue set to discard all",        DiscardAction.oldest,   messageQueue.getDiscardAction());

        internalLogger.assertInternalDebugLog("log writer starting.*");
        internalLogger.assertInternalErrorLog(".*invalid.*topic.*");
    }


    @Test
    public void testInvalidSubjectTooLong() throws Exception
    {
        config.topicName = TEST_TOPIC_NAME;
        config.subject = StringUtil.repeat('A', 100);
        createWriter();

        assertStatisticsErrorMessage("invalid subject.*too long.*");

        assertEquals("invocations of listTopics",               0,                      mock.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",              0,                      mock.createTopicInvocationCount);
        assertNotNull("topic name, from statistics",                                    stats.getActualTopicName());        // comes from config
        assertNull("topic ARN, from statistics",                                        stats.getActualTopicArn());         // would come from init
        assertEquals("message queue set to discard all",        0,                      messageQueue.getDiscardThreshold());
        assertEquals("message queue set to discard all",        DiscardAction.oldest,   messageQueue.getDiscardAction());

        internalLogger.assertInternalDebugLog("log writer starting.*");
        internalLogger.assertInternalErrorLog("invalid.*subject.*too long.*");
    }


    @Test
    public void testInvalidSubjectBadCharacters() throws Exception
    {
        config.topicName = TEST_TOPIC_NAME;
        config.subject = "This is \t not OK";
        createWriter();

        assertStatisticsErrorMessage("invalid subject.*disallowed characters.*");

        assertEquals("invocations of listTopics",               0,                      mock.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",              0,                      mock.createTopicInvocationCount);
        assertNotNull("topic name, from statistics",                                    stats.getActualTopicName());        // comes from config
        assertNull("topic ARN, from statistics",                                        stats.getActualTopicArn());         // would come from init
        assertEquals("message queue set to discard all",        0,                      messageQueue.getDiscardThreshold());
        assertEquals("message queue set to discard all",        DiscardAction.oldest,   messageQueue.getDiscardAction());

        internalLogger.assertInternalDebugLog("log writer starting.*");
        internalLogger.assertInternalErrorLog("invalid subject.*disallowed characters.*");
    }


    @Test
    public void testInvalidSubjectBeginsWithSpace() throws Exception
    {
        config.topicName = TEST_TOPIC_NAME;
        config.subject = " not OK";
        createWriter();

        assertStatisticsErrorMessage("invalid subject.*starts with space.*");

        assertEquals("invocations of listTopics",               0,                      mock.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",              0,                      mock.createTopicInvocationCount);
        assertNotNull("topic name, from statistics",                                    stats.getActualTopicName());        // comes from config
        assertNull("topic ARN, from statistics",                                        stats.getActualTopicArn());         // would come from init
        assertEquals("message queue set to discard all",        0,                      messageQueue.getDiscardThreshold());
        assertEquals("message queue set to discard all",        DiscardAction.oldest,   messageQueue.getDiscardAction());

        internalLogger.assertInternalDebugLog("log writer starting.*");
        internalLogger.assertInternalErrorLog("invalid.*subject.*starts with space.*");
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

        internalLogger.assertInternalDebugLog("log writer starting.*");
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

        // this attempt will fail
        mock.allowWriterThread();

        // we could spin waiting for stats to be updated, but a sleep should suffice
        Thread.sleep(50);

        assertEquals("first try, messages sent",            0,                      stats.getMessagesSentLastBatch());
        assertEquals("first try, messages requeued",        1,                      stats.getMessagesRequeuedLastBatch());

        // this attempt will succeed
        mock.allowWriterThread();

        assertEquals("publish, invocation count",           2,                      mock.publishInvocationCount);
        assertEquals("publish, arn",                        TEST_TOPIC_ARN,         mock.lastPublishArn);
        assertEquals("publish, subject",                    null,                   mock.lastPublishSubject);
        assertEquals("publish, body",                       "message one",          mock.lastPublishMessage);

        assertStatisticsTotalMessagesSent(1);

        assertEquals("second try, messages sent",           1,                      stats.getMessagesSentLastBatch());
        assertEquals("second try, messages requeued",       0,                      stats.getMessagesRequeuedLastBatch());

        assertStatisticsErrorMessage(".*no notifications for you");
        assertStatisticsException(TestingException.class, "no notifications for you");

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "log writer initialization complete.*");
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testOversizeMessageDiscard() throws Exception
    {
        final int snsMaxMessageSize     = 262144;  // per https://docs.aws.amazon.com/sns/latest/api/API_Publish.html

        // using different characters at the end of the message makes JUnit output easer to read
        final String bigMessage         = StringUtil.repeat('X', snsMaxMessageSize - 1) + "Y";
        final String biggerMessage      = bigMessage + "Z";

        config.topicName = TEST_TOPIC_NAME;
        config.subject = "example";

        createWriter();

        // have to write both messages at once, in this order, otherwise we don't know that the first was discarded
        writer.addMessage(new LogMessage(System.currentTimeMillis(), biggerMessage));
        writer.addMessage(new LogMessage(System.currentTimeMillis(), bigMessage));
        mock.allowWriterThread();

        assertEquals("publish: invocation count",        1,                  mock.publishInvocationCount);
        assertEquals("publish: last call #/messages",    bigMessage,         mock.lastPublishMessage);

        internalLogger.assertInternalWarningLog(
            "discarded oversize.*" + (snsMaxMessageSize + 1) + ".*"
            );
    }


    @Test
    public void testOversizeMessageTruncate() throws Exception
    {
        final int snsMaxMessageSize     = 262144;  // per https://docs.aws.amazon.com/sns/latest/api/API_Publish.html

        // using different characters at the end of the message makes JUnit output easer to read
        final String bigMessage         = StringUtil.repeat('X', snsMaxMessageSize - 1) + "Y";
        final String biggerMessage      = bigMessage + "Z";

        config.topicName = TEST_TOPIC_NAME;
        config.subject = "example";
        config.truncateOversizeMessages = true;
        createWriter();

        // first message should succeed
        writer.addMessage(new LogMessage(System.currentTimeMillis(), bigMessage));
        mock.allowWriterThread();

        assertEquals("publish: invocation count",        1,                  mock.publishInvocationCount);
        assertEquals("publish: last call #/messages",    bigMessage,         mock.lastPublishMessage);

        internalLogger.assertInternalWarningLog();

        // second message should be truncated
        writer.addMessage(new LogMessage(System.currentTimeMillis(), biggerMessage));
        mock.allowWriterThread();

        assertEquals("publish: invocation count",        2,                  mock.publishInvocationCount);
        assertEquals("publish: last call #/messages",    bigMessage,         mock.lastPublishMessage);

        internalLogger.assertInternalWarningLog(
            "truncated oversize.*" + (snsMaxMessageSize + 1) + ".*"
            );
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

        assertNotNull("factory called (local flag)",                                        staticFactoryMock);

        assertEquals("invocations of listTopics",               1,                          staticFactoryMock.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",              0,                          staticFactoryMock.createTopicInvocationCount);
        assertEquals("invocations of publish",                  0,                          staticFactoryMock.publishInvocationCount);

        assertNull("stats: no initialization message",                                      stats.getLastErrorMessage());
        assertNull("stats: no initialization error",                                        stats.getLastError());
        assertEquals("stats: topic name",                       TEST_TOPIC_NAME,            stats.getActualTopicName());
        assertEquals("stats: topic ARN",                        TEST_TOPIC_ARN,             stats.getActualTopicArn());

        internalLogger.assertInternalDebugLog("log writer starting.*",
                                              "creating client via factory.*" + config.clientFactoryMethod,
                                              "log writer initialization complete.*");
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testShutdown() throws Exception
    {
        // this test is the only place that we expicitly test shutdown logic, to avoid cluttering
        // the "operation" tests; it's otherwise identical to the "by name" test

        // it actually tests functionality in AbstractAppender, but I've replicated for all concrete
        // subclasses simply because it's a key piece of functionality

        config.topicName = TEST_TOPIC_NAME;
        createWriter();

        assertEquals("after creation, shutdown time should be infinite", Long.MAX_VALUE, getShutdownTime());

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));

        // the immediate stop should interrupt waitForMessage, but there's no guarantee
        writer.stop();

        long now = System.currentTimeMillis();
        long shutdownTime = getShutdownTime();
        assertInRange("after stop(), shutdown time should be based on batch delay", now, now + config.batchDelay + 100, shutdownTime);

        // the batch should still be processed
        mock.allowWriterThread();

        assertEquals("publish: invocation count",   1,                  mock.publishInvocationCount);
        assertEquals("publish: arn",                TEST_TOPIC_ARN,     mock.lastPublishArn);
        assertEquals("publish: subject",            null,               mock.lastPublishSubject);
        assertEquals("publish: body",               "message one",      mock.lastPublishMessage);

        // another call to stop should be ignored -- sleep to ensure times would be different
        Thread.sleep(100);
        writer.stop();
        assertEquals("second call to stop() should be no-op", shutdownTime, getShutdownTime());

        joinWriterThread();

        assertEquals("shutdown: invocation count",  1,                  mock.shutdownInvocationCount);

        internalLogger.assertInternalDebugLog(
            "log writer starting.*",
            "log writer initialization complete.*",
            "log.writer shut down.*");
        internalLogger.assertInternalErrorLog();
    }


    @Test
    public void testSynchronousOperation() throws Exception
    {
        // appender is expected to set batch delay in synchronous mode
        config.batchDelay = 1;
        config.topicName = TEST_TOPIC_NAME;

        // we just have one thread, so don't want any locks getting in the way
        mock.disableThreadSynchronization();

        writer = (SNSLogWriter)mock.newWriterFactory().newLogWriter(config, stats, internalLogger);
        messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        assertEquals("before init, stats: topic name",          TEST_TOPIC_NAME,        stats.getActualTopicName());
        assertNull("before init, stats: topic ARN",                                     stats.getActualTopicArn());

        writer.initialize();

        assertEquals("after init, invocations of listTopics",   1,                      mock.listTopicsInvocationCount);
        assertEquals("after init, invocations of createTopic",  0,                      mock.createTopicInvocationCount);
        assertEquals("after init, invocations of publish",      0,                      mock.publishInvocationCount);

        assertNull("after init, stats: no errors",                                      stats.getLastError());
        assertEquals("after init, stats: topic name",           TEST_TOPIC_NAME,        stats.getActualTopicName());
        assertEquals("after init, stats: topic ARN",            TEST_TOPIC_ARN,         stats.getActualTopicArn());

        writer.addMessage(new LogMessage(System.currentTimeMillis(), "message one"));

        assertEquals("message is waiting in queue",             1,                      messageQueue.queueSize());
        assertEquals("publish: invocation count",               0,                      mock.publishInvocationCount);

        writer.processBatch(System.currentTimeMillis());

        assertEquals("after publish, invocation count",         1,                      mock.publishInvocationCount);
        assertEquals("after publish, arn",                      TEST_TOPIC_ARN,         mock.lastPublishArn);
        assertEquals("after publish, subject",                  null,                   mock.lastPublishSubject);
        assertEquals("after publish, body",                     "message one",          mock.lastPublishMessage);

        assertStatisticsTotalMessagesSent(1);
        assertEquals("messages sent in batch",                  1,                      stats.getMessagesSentLastBatch());

        assertEquals("shutdown not called before cleanup",      0,                      mock.shutdownInvocationCount);
        writer.cleanup();
        assertEquals("shutdown called after cleanup",           1,                      mock.shutdownInvocationCount);

        internalLogger.assertInternalDebugLog();
        internalLogger.assertInternalErrorLog();
    }
}