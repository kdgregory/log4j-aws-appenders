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

package com.kdgregory.logging.testhelpers;

import static net.sf.kdgcommons.test.StringAsserts.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.w3c.dom.Element;

import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.StringUtil;
import net.sf.practicalxml.converter.json.Json2XmlConverter;
import net.sf.practicalxml.xpath.XPathWrapper;

import com.amazonaws.services.logs.model.ResourceNotFoundException;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.*;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.*;


/**
 *  A collection of utility methods to support integration tests. This is an
 *  instantiable class, internally tracking the AWS clients and test resources.
 *  <p>
 *  Not intended for production use outside of this library.
 */
public class SNSTestHelper
{
    private AmazonSNS snsClient;
    private AmazonSQS sqsClient;

    private String runId;           // set in ctor, used to generate unique names
    private String resourceName;    // base name for topic and queue

    private String topicArn;        // these are set by createTopicAndQueue()
    private String queueArn;
    private String queueUrl;


    public SNSTestHelper(AmazonSNS snsClient, AmazonSQS sqsClient)
    {
        this.snsClient = snsClient;
        this.sqsClient = sqsClient;

        runId = String.valueOf(System.currentTimeMillis());
        resourceName = "SNSAppenderIntegrationTest-" + runId;

        System.setProperty("SNSAppenderIntegrationTest.resourceName", resourceName);
    }


    /**
     *  Returns the generated topic name.
     */
    public String getTopicName()
    {
        return resourceName;
    }


    /**
     *  Returns the topic's ARN. This will be set by {@link #createTopicAndQueue} or
     *  {@link #lookupTopic}.
     */
    public String getTopicARN()
    {
        return topicArn;
    }


    /**
     *  Creates the topic and queue, and subscribes the queue to the topic. Returns
     *  the topic ARN.
     */
    public String createTopicAndQueue()
    throws Exception
    {
//        localLogger.info("creating queue and topic with name: " + resourceName);
        createTopic();
        createQueue();
        subscribeQueueToTopic();
        return topicArn;
    }


    /**
     *  Loops through all topic names, looking for the one that matches the provided name.
     *  Returns the topic's ARN, null if unable to find it.
     */
    public String lookupTopic()
    {
        ListTopicsRequest request = new ListTopicsRequest();
        ListTopicsResult response;
        do
        {
            response = snsClient.listTopics(request);
            for (Topic topic : response.getTopics())
            {
                if (topic.getTopicArn().endsWith(resourceName))
                {
                    topicArn = topic.getTopicArn();
                    return topicArn;
                }
            }
            request.setNextToken(response.getNextToken());
        }
        while (! StringUtil.isEmpty(response.getNextToken()));

        return null;
    }


    /**
     *  Attempts to read the expected number of messages from the queue, extracting
     *  the message message body (which is a JSON blob).
     */
    public List<String> retrieveMessages(int expectedMessageCount)
    throws Exception
    {
        List<String> result = new ArrayList<String>();
        int emptyBatchCount = 0;
        while ((expectedMessageCount > 0) && (emptyBatchCount < 3))
        {
            ReceiveMessageRequest retrieveRequest = new ReceiveMessageRequest()
                                                        .withQueueUrl(queueUrl)
                                                        .withWaitTimeSeconds(5);
            ReceiveMessageResult retrieveResponse = sqsClient.receiveMessage(retrieveRequest);
            if (retrieveResponse.getMessages().isEmpty())
            {
                emptyBatchCount++;
            }
            else
            {
                for (Message message : retrieveResponse.getMessages())
                {
                    result.add(message.getBody());
                    sqsClient.deleteMessage(queueUrl, message.getReceiptHandle());
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
    public void assertMessageContent(List<String> messages, String... expectedSubjects0)
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

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    /**
     *  Creates the topic and waits for it to become available.
     */
    private void createTopic()
    throws Exception
    {
        CreateTopicRequest createTopicRequest = new CreateTopicRequest().withName(resourceName);
        CreateTopicResult createTopicResponse = snsClient.createTopic(createTopicRequest);
        topicArn = createTopicResponse.getTopicArn();

        System.setProperty("SNSAppenderIntegrationTest.topicArn", topicArn);

        for (int ii = 0 ; ii <  30 ; ii++)
        {
            try
            {
                GetTopicAttributesRequest attribsRequest = new GetTopicAttributesRequest().withTopicArn(topicArn);
                snsClient.getTopicAttributes(attribsRequest);
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
        CreateQueueResult createResponse = sqsClient.createQueue(createRequest);
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
        sqsClient.setQueueAttributes(setPolicyRequest);

        // according to docs, it can take up to 60 seconds for an attribute to propagate
        // we'll just wait until it's non-blank
        retrieveQueueAttribute("Policy");

        SubscribeRequest subscribeRequest = new SubscribeRequest()
                                            .withTopicArn(topicArn)
                                            .withProtocol("sqs")
                                            .withEndpoint(queueArn);
        snsClient.subscribe(subscribeRequest);
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
                GetQueueAttributesResult attribsResponse = sqsClient.getQueueAttributes(attribsRequest);
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
}
