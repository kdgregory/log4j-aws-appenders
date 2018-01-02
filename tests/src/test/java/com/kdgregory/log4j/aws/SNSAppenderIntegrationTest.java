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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Element;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import net.sf.practicalxml.converter.json.Json2XmlConverter;
import net.sf.practicalxml.xpath.XPathWrapper;
import net.sf.kdgcommons.lang.StringUtil;
import static net.sf.kdgcommons.test.StringAsserts.*;

import com.amazonaws.services.logs.model.ResourceNotFoundException;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sns.model.*;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.*;

import com.kdgregory.log4j.aws.testhelpers.MessageWriter;


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


//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void smoketestByArn() throws Exception
    {
        setUp("SNSAppenderIntegrationTest-smoketestByArn.properties");
        localLogger.info("smoketest: starting");

        createTopicAndQueue();

        final int numMessages = 11;

        Logger testLogger = Logger.getLogger("TestLogger");
        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("smoketest: reading messages");
        List<String> messages = retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        assertMessageContent(messages, "");
    }


    @Test
    public void smoketestByName() throws Exception
    {
        setUp("SNSAppenderIntegrationTest-smoketestByName.properties");
        localLogger.info("smoketest: starting");

        createTopicAndQueue();

        final int numMessages = 11;

        Logger testLogger = Logger.getLogger("TestLogger");
        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("smoketest: reading messages");
        List<String> messages = retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        assertMessageContent(messages, "Example");
    }


//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Loads the test-specific Log4J configuration and resets the environment.
     */
    public void setUp(String propertiesName)
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
    private void assertMessageContent(List<String> messages, String expectedSubject)
    {
        XPathWrapper arnXPath = new XPathWrapper("//TopicArn");
        XPathWrapper subjectXPath = new XPathWrapper("//Subject");
        XPathWrapper messageXPath = new XPathWrapper("//Message");

        for (String message : messages)
        {
            Element root = new Json2XmlConverter(message).convert();
            assertEquals("topic ARN",       topicArn,               arnXPath.evaluateAsString(root));
            assertEquals("message subject", expectedSubject,        subjectXPath.evaluateAsString(root));
            assertRegex("message text",     MessageWriter.REGEX,    messageXPath.evaluateAsString(root));
        }
    }
}
