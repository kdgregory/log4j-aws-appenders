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


import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.lang.StringUtil;

import org.slf4j.Logger;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;

import com.kdgregory.logging.aws.sns.SNSConstants;
import com.kdgregory.logging.aws.sns.SNSLogWriter;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;
import com.kdgregory.logging.aws.sns.SNSWriterFactory;
import com.kdgregory.logging.aws.sns.SNSWriterStatistics;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.factories.DefaultThreadFactory;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.testhelpers.SNSTestHelper;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;


public class SNSLogWriterIntegrationTest
{
    // "helper" clients are shared by all tests
    private static AmazonSNSClient helperSNSclient;
    private static AmazonSQSClient helperSQSclient;

    // these are created by the "alternate region" tests
    private AmazonSNSClient altSNSclient;
    private AmazonSQSClient altSQSclient;

    // this client is used in testFactoryMethod(), should be null everywhere else
    private static AmazonSNSClient factoryClient;

    // this is for logging within the test
    private Logger localLogger = LoggerFactory.getLogger(getClass());

    // this can be modified before calling init()
    private SNSWriterConfig config = new SNSWriterConfig(null, null, DEFAULT_SUBJECT, true, false, 10000, DiscardAction.oldest, null, null, null, null);

    // these are all assigned by init()
    private SNSTestHelper testHelper;
    private TestableInternalLogger internalLogger;
    private SNSWriterStatistics stats;
    private SNSWriterFactory factory;
    private SNSLogWriter writer;

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    private final static String DEFAULT_SUBJECT = "integration test";


    private void init(String testName, AmazonSNS snsClient, AmazonSQS sqsClient)
    throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        testHelper = new SNSTestHelper(snsClient, sqsClient);

        testHelper.createTopicAndQueue();

        config.topicName = testHelper.getTopicName();

        stats = new SNSWriterStatistics();
        internalLogger = new TestableInternalLogger();
        factory = new SNSWriterFactory();
        writer = (SNSLogWriter)factory.newLogWriter(config, stats, internalLogger);

        new DefaultThreadFactory("test").startLoggingThread(writer, false, null);
    }


    private class MessageWriter
    extends com.kdgregory.logging.testhelpers.MessageWriter
    {
        public MessageWriter(int numMessages)
        {
            super(numMessages);
        }

        @Override
        protected void writeLogMessage(String message)
        {
            writer.addMessage(new LogMessage(System.currentTimeMillis(), message));
        }
    }


    public static AmazonSNSClient staticClientFactory()
    {
        factoryClient = new AmazonSNSClient();
        return factoryClient;
    }

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    {
        // constructor because we're running against 1.11.0
        helperSNSclient = new AmazonSNSClient();
        helperSQSclient = new AmazonSQSClient();
    }

    @After
    public void tearDown()
    {
        if (writer != null)
        {
            writer.stop();
        }

        if (altSNSclient != null)
        {
            altSNSclient.shutdown();
        }

        if (altSQSclient != null)
        {
            altSQSclient.shutdown();
        }

        if (factoryClient != null)
        {
            factoryClient.shutdown();
            factoryClient = null;
        }

        localLogger.info("finished");
        MDC.clear();
    }


    @AfterClass
    public static void afterClass()
    {
        helperSNSclient.shutdown();
        helperSQSclient.shutdown();
    }


//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void smoketest() throws Exception
    {
        final int numMessages = 11;

        init("smoketest", helperSNSclient, helperSQSclient);

        new MessageWriter(numMessages).run();

        List<Map<String,Object>> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages, DEFAULT_SUBJECT);

        testHelper.deleteTopicAndQueue();
    }


    @Test
    public void testSubjectChange() throws Exception
    {
        final int numMessages = 5;
        final String secondSubject = "this is not the default";

        init("testSubjectChange", helperSNSclient, helperSQSclient);

        new MessageWriter(numMessages).run();
        List<Map<String,Object>> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("first batch of messages, size", numMessages, messages.size());
        testHelper.assertMessageContent(messages, DEFAULT_SUBJECT);

        writer.setSubject(secondSubject);

        new MessageWriter(numMessages).run();
        messages = testHelper.retrieveMessages(numMessages);

        assertEquals("second batch of messages, size", numMessages, messages.size());
        testHelper.assertMessageContent(messages, secondSubject);

        testHelper.deleteTopicAndQueue();
    }


    @Test
    public void testFactoryMethod() throws Exception
    {
        final int numMessages = 11;

        config.clientFactoryMethod = getClass().getName() + ".staticClientFactory";
        init("testFactoryMethod", helperSNSclient, helperSQSclient);

        new MessageWriter(numMessages).run();

        List<Map<String,Object>> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages, DEFAULT_SUBJECT);

        assertNotNull("factory method was called", factoryClient);
        assertSame("factory-created client used by writer", factoryClient, ClassUtil.getFieldValue(writer, "client", AmazonSNS.class));

        testHelper.deleteTopicAndQueue();
    }


    @Test
    public void testAlternateRegion() throws Exception
    {
        final int numMessages = 11;

        // default region for constructor is always us-east-1
        altSNSclient = new AmazonSNSClient().withRegion(Regions.US_WEST_1);
        altSQSclient = new AmazonSQSClient().withRegion(Regions.US_WEST_1);

        config.clientRegion = "us-west-1";
        init("testAlternateRegion", altSNSclient, altSQSclient);

        new MessageWriter(numMessages).run();

        List<Map<String,Object>> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages, DEFAULT_SUBJECT);

        assertNull("topic does not exist in default region",
                   (new SNSTestHelper(testHelper, helperSNSclient, helperSQSclient)).lookupTopic());

        testHelper.deleteTopicAndQueue();
    }


    @Test
    public void testAlternateEndpoint() throws Exception
    {
        final int numMessages = 11;

        // the goal here is to verify that we can use a region that didn't exist when 1.11.0 came out
        // BEWARE: my default region is us-east-1, so I use us-east-2 as the alternate
        //         if that is your default, then the test will fail
        altSNSclient = new AmazonSNSClient().withEndpoint("sns.us-east-2.amazonaws.com");
        altSQSclient = new AmazonSQSClient().withEndpoint("sqs.us-east-2.amazonaws.com");

        config.clientEndpoint = "sns.us-east-2.amazonaws.com";
        init("testAlternateEndpoint", altSNSclient, altSQSclient);

        new MessageWriter(numMessages).run();

        List<Map<String,Object>> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages, DEFAULT_SUBJECT);

        assertNull("topic does not exist in default region",
                   (new SNSTestHelper(testHelper, helperSNSclient, helperSQSclient)).lookupTopic());

        testHelper.deleteTopicAndQueue();
    }


    @Test
    public void testOversizeMessageTruncation() throws Exception
    {
        final int numMessages = 11;

        // this test verifies that what I think is the maximum message size is acceptable to SNS
        final int maxMessageSize = SNSConstants.MAX_MESSAGE_BYTES;

        final String expectedMessage = StringUtil.repeat('X', maxMessageSize - 1) + "Y";
        final String messageToWrite = expectedMessage + "Z";

        config.truncateOversizeMessages = true;
        init("testOversizeMessageTruncation", helperSNSclient, helperSQSclient);

        new MessageWriter(numMessages)
        {
            @Override
            protected void writeLogMessage(String ignored)
            {
                super.writeLogMessage(messageToWrite);
            }
        }.run();

        List<Map<String,Object>> messages = testHelper.retrieveMessages(numMessages);
        Set<String> messageBodies = new HashSet<>();
        for (Map<String,Object> message : messages)
        {
            messageBodies.add((String)message.get(SNSTestHelper.SQS_KEY_MESSAGE));
        }

        assertEquals("all messages should be truncated to same value",  1, messageBodies.size());
        assertEquals("message was truncated",                           expectedMessage, messageBodies.iterator().next());

        testHelper.deleteTopicAndQueue();
    }
}
