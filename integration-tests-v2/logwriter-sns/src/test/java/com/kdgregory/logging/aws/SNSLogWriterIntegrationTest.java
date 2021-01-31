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

import java.util.Collections;
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

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import com.kdgregory.logging.aws.facade.SNSFacade;
import com.kdgregory.logging.aws.sns.SNSConstants;
import com.kdgregory.logging.aws.sns.SNSLogWriter;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;
import com.kdgregory.logging.aws.sns.SNSWriterFactory;
import com.kdgregory.logging.aws.sns.SNSWriterStatistics;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.util.DefaultThreadFactory;
import com.kdgregory.logging.common.util.MessageQueue.DiscardAction;
import com.kdgregory.logging.testhelpers.SNSTestHelper;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;


public class SNSLogWriterIntegrationTest
{
    // "helper" clients are shared by all tests
    private static SnsClient helperSNSclient;
    private static SqsClient helperSQSclient;

    // these are created by the "alternate region" tests
    private SnsClient altSNSclient;
    private SqsClient altSQSclient;

    // this client is used in testFactoryMethod(), should be null everywhere else
    private static SnsClient factoryClient;

    // this is for logging within the test
    private Logger localLogger = LoggerFactory.getLogger(getClass());

    // this can be modified before calling init()
    private SNSWriterConfig config = new SNSWriterConfig()
                                     .setSubject(DEFAULT_SUBJECT)
                                     .setAutoCreate(true)
                                     .setDiscardThreshold(10000)
                                     .setDiscardAction(DiscardAction.oldest);

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


    private void init(String testName, SnsClient snsClient, SqsClient sqsClient)
    throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        testHelper = new SNSTestHelper(snsClient, sqsClient);

        testHelper.createTopicAndQueue();

        config.setTopicName(testHelper.getTopicName());

        stats = new SNSWriterStatistics();
        internalLogger = new TestableInternalLogger();
        factory = new SNSWriterFactory();
        writer = (SNSLogWriter)factory.newLogWriter(config, stats, internalLogger);

        new DefaultThreadFactory("test").startWriterThread(writer, null);
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


    public static SnsClient staticClientFactory()
    {
        factoryClient = SnsClient.builder().build();
        return factoryClient;
    }

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    {
        helperSNSclient = SnsClient.builder().build();
        helperSQSclient = SqsClient.builder().build();
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
            altSNSclient.close();
        }

        if (altSQSclient != null)
        {
            altSQSclient.close();
        }

        if (factoryClient != null)
        {
            factoryClient.close();
            factoryClient = null;
        }

        localLogger.info("finished");
        MDC.clear();
    }


    @AfterClass
    public static void afterClass()
    {
        helperSNSclient.close();
        helperSQSclient.close();
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

        assertEquals("internal error log", Collections.emptyList(), internalLogger.errorMessages);

        testHelper.deleteTopicAndQueue();
    }


    @Test
    public void testFactoryMethod() throws Exception
    {
        final int numMessages = 11;

        config.setClientFactoryMethod(getClass().getName() + ".staticClientFactory");
        init("testFactoryMethod", helperSNSclient, helperSQSclient);

        new MessageWriter(numMessages).run();

        List<Map<String,Object>> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages, DEFAULT_SUBJECT);

        assertNotNull("factory method was called", factoryClient);

        Object facade = ClassUtil.getFieldValue(writer, "facade", SNSFacade.class);
        Object client = ClassUtil.getFieldValue(facade, "client", SnsClient.class);
        assertSame("factory-created client used by writer", factoryClient, client);

        assertEquals("internal error log", Collections.emptyList(), internalLogger.errorMessages);

        testHelper.deleteTopicAndQueue();
    }


    @Test
    public void testAlternateRegion() throws Exception
    {
        final int numMessages = 11;

        altSNSclient = SnsClient.builder().region(Region.US_WEST_1).build();
        altSQSclient = SqsClient.builder().region(Region.US_WEST_1).build();

        config.setClientRegion("us-west-1");
        init("testAlternateRegion", altSNSclient, altSQSclient);

        new MessageWriter(numMessages).run();

        List<Map<String,Object>> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages, DEFAULT_SUBJECT);

        assertNull("topic does not exist in default region",
                   (new SNSTestHelper(testHelper, helperSNSclient, helperSQSclient)).lookupTopic());

        assertEquals("internal error log", Collections.emptyList(), internalLogger.errorMessages);

        testHelper.deleteTopicAndQueue();
    }


    @Test
    public void testAlternateEndpoint() throws Exception
    {
        final int numMessages = 11;

        altSNSclient = SnsClient.builder().region(Region.US_EAST_2).build();
        altSQSclient = SqsClient.builder().region(Region.US_WEST_1).build();

        config.setClientEndpoint("https://sns.us-east-2.amazonaws.com")
              .setClientRegion("us-east-2");
        init("testAlternateEndpoint", altSNSclient, altSQSclient);

        new MessageWriter(numMessages).run();

        List<Map<String,Object>> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages, DEFAULT_SUBJECT);

        assertNull("topic does not exist in default region",
                   (new SNSTestHelper(testHelper, helperSNSclient, helperSQSclient)).lookupTopic());

        assertEquals("internal error log", Collections.emptyList(), internalLogger.errorMessages);

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

        config.setTruncateOversizeMessages(true);
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

        assertEquals("internal error log", Collections.emptyList(), internalLogger.errorMessages);

        testHelper.deleteTopicAndQueue();
    }
}
