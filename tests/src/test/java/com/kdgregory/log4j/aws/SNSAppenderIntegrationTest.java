// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import net.sf.kdgcommons.lang.StringUtil;

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
    private Logger mainLogger;

    private AmazonSNS snsClient;
    private AmazonSQS sqsClient;

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
        mainLogger.info("smoketest: starting");

        createTopicAndQueue();

        final int numMessages = 11;

        Logger testLogger = Logger.getLogger("TestLogger");
        (new MessageWriter(testLogger, numMessages)).run();

        mainLogger.info("smoketest: reading messages");
        List<String> messages = retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
    }


    @Test
    public void smoketestByName() throws Exception
    {
        setUp("SNSAppenderIntegrationTest-smoketestByName.properties");
        mainLogger.info("smoketest: starting");

        createTopicAndQueue();

        final int numMessages = 11;

        Logger testLogger = Logger.getLogger("TestLogger");
        (new MessageWriter(testLogger, numMessages)).run();

        mainLogger.info("smoketest: reading messages");
        List<String> messages = retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
    }


//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    public void setUp(String propertiesName)
    throws Exception
    {
        URL config = ClassLoader.getSystemResource(propertiesName);
        PropertyConfigurator.configure(config);

        mainLogger = Logger.getLogger(getClass());

        runId = String.valueOf(System.currentTimeMillis());
        resourceName = "SNSAppenderIntegrationTest-" + runId;
        System.setProperty("SNSAppenderIntegrationTest.resourceName", resourceName);

        snsClient = AmazonSNSClientBuilder.defaultClient();
        sqsClient = AmazonSQSClientBuilder.defaultClient();
    }


    private void createTopicAndQueue()
    throws Exception
    {
        mainLogger.info("creating queue and topic with name: " + resourceName);
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
        SubscribeResult subscribeResponse = snsClient.subscribe(subscribeRequest);
        String subscriptionArn = subscribeResponse.getSubscriptionArn();

        SetSubscriptionAttributesRequest setRawMessaeRequest = new SetSubscriptionAttributesRequest()
                                                               .withSubscriptionArn(subscriptionArn)
                                                               .withAttributeName("RawMessageDelivery")
                                                               .withAttributeValue("true");
        snsClient.setSubscriptionAttributes(setRawMessaeRequest);
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


    /**
     *  Attempts to read the expected number of messages from the queue, extracting
     *  the message content.
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
}
