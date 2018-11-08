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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.w3c.dom.Element;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import net.sf.practicalxml.converter.json.Json2XmlConverter;
import net.sf.practicalxml.xpath.XPathWrapper;
import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.lang.StringUtil;
import static net.sf.kdgcommons.test.StringAsserts.*;

import com.amazonaws.services.logs.model.ResourceNotFoundException;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.*;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.*;

import com.kdgregory.logging.aws.sns.SNSWriterStatistics;
import com.kdgregory.logging.aws.sns.SNSLogWriter;


public class SNSAppenderIntegrationTest
{
    private Logger localLogger;

    private AmazonSNS localSNSclient;
    private AmazonSQS localSQSclient;

    private String runId;           // used to create unique names for queues and topic
    private String resourceName;    // used for both topic and queue
    private String topicArn;        // set after the topic has been created
    private String queueArn;        // set after the queue has been created
    private String queueUrl;        // ditto

    private static boolean localFactoryUsed;

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        localFactoryUsed = false;
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void smoketestByArn() throws Exception
    {
        init("SNSAppenderIntegrationTest/smoketestByArn.properties");
        localLogger.info("smoketestByArn: starting");

        createTopicAndQueue();

        final int numMessages = 11;

        Logger testLogger = Logger.getLogger("TestLogger");
        SNSAppender appender = (SNSAppender)testLogger.getAppender("test");
        SNSWriterStatistics appenderStats = appender.getAppenderStatistics();

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("smoketestByArn: reading messages");
        List<String> messages = retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        assertMessageContent(messages);

        assertEquals("actual topic name, from statistics",  resourceName,   appenderStats.getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   topicArn,       appenderStats.getActualTopicArn());
        assertEquals("messages written, from stats",        numMessages,    appenderStats.getMessagesSent());

        assertTrue("client factory used", localFactoryUsed);

        localLogger.info("smoketestByArn: finished");
    }


    @Test
    public void smoketestByName() throws Exception
    {
        init("SNSAppenderIntegrationTest/smoketestByName.properties");
        localLogger.info("smoketestByName: starting");

        createTopicAndQueue();

        final int numMessages = 11;

        Logger testLogger = Logger.getLogger("TestLogger");
        SNSAppender appender = (SNSAppender)testLogger.getAppender("test");
        SNSWriterStatistics appenderStats = appender.getAppenderStatistics();

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("smoketestByName: reading messages");
        List<String> messages = retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        assertMessageContent(messages, "Example");

        assertEquals("actual topic name, from statistics",  resourceName,   appenderStats.getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   topicArn,       appenderStats.getActualTopicArn());
        assertEquals("messages written, from stats",        numMessages,    appenderStats.getMessagesSent());

        assertFalse("client factory used", localFactoryUsed);

        localLogger.info("smoketestByName: finished");
    }


    @Test
    public void testTopicMissingAutoCreate() throws Exception
    {
        init("SNSAppenderIntegrationTest/testTopicMissingAutoCreate.properties");
        localLogger.info("testAutoCreate: starting");

        final int numMessages = 11;

        Logger testLogger = Logger.getLogger("TestLogger");
        SNSAppender appender = (SNSAppender)testLogger.getAppender("test");
        SNSWriterStatistics appenderStats = appender.getAppenderStatistics();

        (new MessageWriter(testLogger, numMessages)).run();

        // since we didn't hook a queue up to the topic we can't actually look at the messages
        // we also have to spin-loop on the appender to determine when it's been initialized

        SNSLogWriter writer = null;
        for (int ii = 0 ; ii < 60 ; ii++)
        {
            writer = ClassUtil.getFieldValue(appender, "writer", SNSLogWriter.class);
            Thread.sleep(1000);
        }
        assertNotNull("writer was created", writer);

        for (int ii = 0 ; ii < 60 ; ii++)
        {
            if (writer.isInitializationComplete())
                break;
            Thread.sleep(1000);
        }
        assertTrue("writer initialization complete", writer.isInitializationComplete());

        topicArn = lookupTopic(resourceName);
        assertNotEmpty("topic was created", topicArn);

        assertEquals("actual topic name, from statistics",  resourceName,   appenderStats.getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   topicArn,       appenderStats.getActualTopicArn());
        assertEquals("messages written, from stats",        numMessages,    appenderStats.getMessagesSent());

        localLogger.info("testAutoCreate: finished");
    }


    @Test
    public void testTopicMissingNoAutoCreate() throws Exception
    {
        init("SNSAppenderIntegrationTest/testTopicMissingNoAutoCreate.properties");
        localLogger.info("testTopicMissingNoAutoCreate: starting");

        final int numMessages = 11;

        Logger testLogger = Logger.getLogger("TestLogger");
        SNSAppender appender = (SNSAppender)testLogger.getAppender("test");
        SNSWriterStatistics appenderStats = appender.getAppenderStatistics();

        (new MessageWriter(testLogger, numMessages)).run();

        // since we didn't hook a queue up to the topic we can't actually look at the messages
        // we also have to spin-loop on the appender to determine when it's been initialized

        SNSLogWriter writer = null;
        for (int ii = 0 ; ii < 60 ; ii++)
        {
            writer = ClassUtil.getFieldValue(appender, "writer", SNSLogWriter.class);
            Thread.sleep(1000);
        }
        assertNotNull("writer was created", writer);

        // if we can't create the topic then initialization fails, so we'll spin looking for
        // an error to be reported via statistics

        String errorMessage = "";
        for (int ii = 0 ; ii < 60 ; ii++)
        {
            errorMessage = appenderStats.getLastErrorMessage();
            if (! StringUtil.isEmpty(errorMessage))
                break;
            Thread.sleep(1000);
        }
        assertNotEmpty("writer initialization failed", errorMessage);
        assertTrue("error message contains topic name (was: " + errorMessage + ")", errorMessage.contains(resourceName));

        topicArn = lookupTopic(resourceName);
        assertNull("topic was not created", topicArn);

        // note: if we don't initialize, we don't update name/ARN in statistics
        assertEquals("actual topic name, from statistics",  null,           appenderStats.getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   null,           appenderStats.getActualTopicArn());
        assertEquals("messages written, from stats",        0,              appenderStats.getMessagesSent());

        localLogger.info("testTopicMissingNoAutoCreate: finished");
    }


    @Test
    public void testMultiThread() throws Exception
    {
        init("SNSAppenderIntegrationTest/testMultiThread.properties");
        localLogger.info("testMultiThread: starting");

        createTopicAndQueue();

        final int numMessages = 11;
        final int numThreads = 3;
        final int totalMessages = numMessages * numThreads;

        Logger testLogger = Logger.getLogger("TestLogger");
        SNSAppender appender = (SNSAppender)testLogger.getAppender("test");
        SNSWriterStatistics appenderStats = appender.getAppenderStatistics();

        for (int ii = 0 ; ii < numThreads ; ii++)
        {
            new Thread(new MessageWriter(testLogger, numMessages)).start();
        }

        localLogger.info("testMultiThread: reading messages");
        List<String> messages = retrieveMessages(totalMessages);

        assertEquals("number of messages", totalMessages, messages.size());
        assertMessageContent(messages, "Example");

        assertEquals("actual topic name, from statistics",  resourceName,   appenderStats.getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   topicArn,       appenderStats.getActualTopicArn());
        assertEquals("messages written, from stats",        totalMessages,  appenderStats.getMessagesSent());

        localLogger.info("testMultiThread: finished");
    }


    @Test
    public void testMultiAppender() throws Exception
    {
        init("SNSAppenderIntegrationTest/testMultiAppender.properties");
        localLogger.info("testMultiAppender: starting");

        createTopicAndQueue();

        final int numMessages = 11;
        final int numAppenders = 2;
        final int totalMessages = numMessages * numAppenders;

        Logger testLogger = Logger.getLogger("TestLogger");

        SNSAppender appender1 = (SNSAppender)testLogger.getAppender("test1");
        SNSWriterStatistics stats1 = appender1.getAppenderStatistics();

        SNSAppender appender2 = (SNSAppender)testLogger.getAppender("test2");
        SNSWriterStatistics stats2 = appender2.getAppenderStatistics();

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("testMultiAppender: reading messages");
        List<String> messages = retrieveMessages(totalMessages);

        assertEquals("number of messages", totalMessages, messages.size());
        assertMessageContent(messages, "Example1", "Example2");

        assertEquals("actual topic name, appender1, from statistics",   resourceName,   stats1.getActualTopicName());
        assertEquals("actual topic ARN, appender1, from statistics",    topicArn,       stats1.getActualTopicArn());
        assertEquals("messages written, appender1, from stats",         numMessages,    stats1.getMessagesSent());

        assertEquals("actual topic name, appender2, from statistics",   resourceName,   stats2.getActualTopicName());
        assertEquals("actual topic ARN, appender2, from statistics",    topicArn,       stats2.getActualTopicArn());
        assertEquals("messages written, appender2, from stats",         numMessages,    stats2.getMessagesSent());

        localLogger.info("testMultiAppender: finished");
    }

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  The static client factory used by smoketestByArn()
     */
    public static AmazonSNS createClient()
    {
        localFactoryUsed = true;
        return AmazonSNSClientBuilder.defaultClient();
    }


    /**
     *  Loads the test-specific Log4J configuration and resets the environment.
     */
    public void init(String propertiesName)
    throws Exception
    {
        URL config = ClassLoader.getSystemResource(propertiesName);
        assertNotNull("missing configuration: " + propertiesName, config);

        LogManager.resetConfiguration();
        PropertyConfigurator.configure(config);

        localLogger = Logger.getLogger(getClass());

        runId = String.valueOf(System.currentTimeMillis());
        resourceName = "SNSAppenderIntegrationTest-" + runId;
        System.setProperty("SNSAppenderIntegrationTest.resourceName", resourceName);

        localSNSclient = AmazonSNSClientBuilder.defaultClient();
        localSQSclient = AmazonSQSClientBuilder.defaultClient();
    }


    private void createTopicAndQueue()
    throws Exception
    {
        localLogger.info("creating queue and topic with name: " + resourceName);
        createTopic();
        createQueue();
        subscribeQueueToTopic();
    }


    /**
     *  Creates the topic and waits for it to become available.
     */
    private void createTopic()
    throws Exception
    {
        CreateTopicRequest createTopicRequest = new CreateTopicRequest().withName(resourceName);
        CreateTopicResult createTopicResponse = localSNSclient.createTopic(createTopicRequest);
        topicArn = createTopicResponse.getTopicArn();

        System.setProperty("SNSAppenderIntegrationTest.topicArn", topicArn);

        for (int ii = 0 ; ii <  30 ; ii++)
        {
            try
            {
                GetTopicAttributesRequest attribsRequest = new GetTopicAttributesRequest().withTopicArn(topicArn);
                localSNSclient.getTopicAttributes(attribsRequest);
                // no exception means the topic is available
                return;
            }
            catch (NotFoundException ex)
            {
                // ignored; topic isn't ready
            }
            Thread.sleep(1000);
        }

        throw new IllegalStateException("topic not ready within 30 seconds");
    }


    /**
     *  Creates the queue and waits for it to become available (assuming that
     *  we won't get attributes until it's available).
     */
    private void createQueue()
    throws Exception
    {
        CreateQueueRequest createRequest = new CreateQueueRequest().withQueueName(resourceName);
        CreateQueueResult createResponse = localSQSclient.createQueue(createRequest);
        queueUrl = createResponse.getQueueUrl();
        queueArn = retrieveQueueAttribute("QueueArn");
    }


    /**
     *  Creates and configures the subscription, as well as giving the topic permission
     *  to publish to the queue.
     */
    private void subscribeQueueToTopic()
    throws Exception
    {
        String queueAccessPolicy
                = "{"
                + "  \"Version\": \"2012-10-17\","
                + "  \"Id\": \"" + resourceName + "-SubscriptionPolicy\","
                + "  \"Statement\": ["
                + "    {"
                + "      \"Effect\": \"Allow\","
                + "      \"Principal\": {"
                + "        \"AWS\": \"*\""
                + "      },"
                + "      \"Action\": \"SQS:SendMessage\","
                + "      \"Resource\": \"" + queueArn + "\","
                + "      \"Condition\": {"
                + "        \"ArnEquals\": {"
                + "          \"aws:SourceArn\": \"" + topicArn + "\""
                + "        }"
                + "      }"
                + "    }"
                + "  ]"
                + "}";

        Map<String,String> queueAttributes = new HashMap<String,String>();
        queueAttributes.put("Policy", queueAccessPolicy);
        SetQueueAttributesRequest setPolicyRequest = new SetQueueAttributesRequest()
                                                         .withQueueUrl(queueUrl)
                                                         .withAttributes(queueAttributes);
        localSQSclient.setQueueAttributes(setPolicyRequest);

        // according to docs, it can take up to 60 seconds for an attribute to propagate
        // we'll just wait until it's non-blank
        retrieveQueueAttribute("Policy");

        SubscribeRequest subscribeRequest = new SubscribeRequest()
                                            .withTopicArn(topicArn)
                                            .withProtocol("sqs")
                                            .withEndpoint(queueArn);
        localSNSclient.subscribe(subscribeRequest);
    }


    /**
     *  Loops through all topic names, looking for the one that matches the provided name.
     *  Returns the topic's ARN, null if unable to find it.
     */
    private String lookupTopic(String topicName)
    {
        ListTopicsRequest request = new ListTopicsRequest();
        ListTopicsResult response;
        do
        {
            response = localSNSclient.listTopics(request);
            for (Topic topic : response.getTopics())
            {
                if (topic.getTopicArn().endsWith(topicName))
                    return topic.getTopicArn();
            }
            request.setNextToken(response.getNextToken());
        }
        while (! StringUtil.isEmpty(response.getNextToken()));

        return null;
    }


    /**
     *  Loops until an attribute is set, throwing after a timeout.
     */
    private String retrieveQueueAttribute(String attributeName)
    throws Exception
    {

        for (int ii = 0 ; ii < 60 ; ii++)
        {
            try
            {
                GetQueueAttributesRequest attribsRequest = new GetQueueAttributesRequest()
                                                                .withQueueUrl(queueUrl)
                                                                .withAttributeNames(attributeName);
                GetQueueAttributesResult attribsResponse = localSQSclient.getQueueAttributes(attribsRequest);
                Map<String,String> attribs = attribsResponse.getAttributes();
                if (! StringUtil.isEmpty(attribs.get(attributeName)))
                    return attribs.get(attributeName);
            }
            catch (ResourceNotFoundException ex)
            {
                // ignored; queue isn't ready
            }
            Thread.sleep(1000);
        }

        throw new IllegalStateException("unable to retrieve attribute: " + attributeName);
    }


    /**
     *  Attempts to read the expected number of messages from the queue, extracting
     *  the message message body (which is a JSON blob).
     */
    private List<String> retrieveMessages(int expectedMessageCount)
    throws Exception
    {
        List<String> result = new ArrayList<String>();
        int emptyBatchCount = 0;
        while ((expectedMessageCount > 0) && (emptyBatchCount < 3))
        {
            ReceiveMessageRequest retrieveRequest = new ReceiveMessageRequest()
                                                        .withQueueUrl(queueUrl)
                                                        .withWaitTimeSeconds(5);
            ReceiveMessageResult retrieveResponse = localSQSclient.receiveMessage(retrieveRequest);
            if (retrieveResponse.getMessages().isEmpty())
            {
                emptyBatchCount++;
            }
            else
            {
                for (Message message : retrieveResponse.getMessages())
                {
                    result.add(message.getBody());
                    localSQSclient.deleteMessage(queueUrl, message.getReceiptHandle());
                }
            }
        }
        return result;
    }


    /**
     *  Performs assertions on the content of each message. In general, these should
     *  fail on the first message.
     *  <p>
     *  It may seem strange that I'm converting the message JSON to XML for assertions.
     *  The answer is that I have a library that makes XPath easy, and also transforms
     *  JSON into XML, so JSON looks like an angle-bracketed nail.
     */
    private void assertMessageContent(List<String> messages, String... expectedSubjects0)
    {
        XPathWrapper arnXPath = new XPathWrapper("//TopicArn");
        XPathWrapper subjectXPath = new XPathWrapper("//Subject");
        XPathWrapper messageXPath = new XPathWrapper("//Message");

        Set<String> expectedSubjects = new TreeSet<String>(Arrays.asList(expectedSubjects0));
        Set<String> actualSubjects = new TreeSet<String>();

        for (String message : messages)
        {
            Element root = new Json2XmlConverter(message).convert();

            assertEquals("topic ARN",       topicArn,               arnXPath.evaluateAsString(root));
            assertRegex("message text",     MessageWriter.REGEX,    messageXPath.evaluateAsString(root));

            String actualSubject = subjectXPath.evaluateAsString(root);
            if (! StringUtil.isEmpty(actualSubject))
                actualSubjects.add(actualSubject);
        }

        assertEquals("message subject(s)", expectedSubjects, actualSubjects);
    }
    
    
    private static class MessageWriter
    extends com.kdgregory.logging.testhelpers.MessageWriter
    {
        private Logger logger;
        
        public MessageWriter(Logger logger, int numMessages)
        {
            super(numMessages);
            this.logger = logger;
        }

        @Override
        protected void writeLogMessage(String message)
        {
            logger.debug(message);
        }
    }
}
