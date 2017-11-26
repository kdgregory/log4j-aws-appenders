// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.sns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.helpers.LogLog;

import net.sf.kdgcommons.lang.StringUtil;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.*;

import com.kdgregory.log4j.aws.internal.shared.AbstractLogWriter;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.MessageQueue.DiscardAction;


public class SNSLogWriter
extends AbstractLogWriter
{
    protected SNSWriterConfig config;
    protected AmazonSNS client;

    // this is the ARN used to publish messages
    protected String topicArn;


    public SNSLogWriter(SNSWriterConfig config)
    {
        super(1, 1000, DiscardAction.oldest);
        this.config = config;
    }

//----------------------------------------------------------------------------
//  AbstractLogWriter implementation
//----------------------------------------------------------------------------

    @Override
    protected void createAWSClient()
    {
        client = new AmazonSNSClient();
    }


    @Override
    protected boolean ensureDestinationAvailable()
    {
        return (config.topicArn != null)
             ? configureByArn()
             : configureByName();
    }


    @Override
    protected List<LogMessage> processBatch(List<LogMessage> currentBatch)
    {
        // although we should only ever get a single message we'll process as a list
        List<LogMessage> failures = new ArrayList<LogMessage>();
        for (LogMessage message : currentBatch)
        {
            try
            {
                client.publish(topicArn, message.getMessage());
            }
            catch (Exception ex)
            {
                LogLog.error("failed to send message", ex);
                failures.add(message);
            }
        }
        return failures;
    }


    @Override
    protected int effectiveSize(LogMessage message)
    {
        return message.size();
    }


    @Override
    protected boolean withinServiceLimits(int batchBytes, int numMessages)
    {
        return (batchBytes <= SNSConstants.MAX_MESSAGE_BYTES) && (numMessages <= 1);
    }


//----------------------------------------------------------------------------
//  Internal
//----------------------------------------------------------------------------

    /**
     *  Attempts to find the configured topicArn in the list of topics for
     *  the current account. Returns true (and configures the writer) if
     *  successful false (with a LogLog message) if not.
     */
    private boolean configureByArn()
    {
        if (retrieveAllTopics().contains(config.topicArn))
        {
            topicArn = config.topicArn;
            return true;
        }
        else
        {
            LogLog.warn("unable to find specified topicArn: " + config.topicArn);
            return false;
        }
    }


    /**
     *  Attempts to find the configured topicName in the list of topics for
     *  the current account. If successful, configures the writer and returns
     *  true. If unsucessful, attempts to create the topic and configure as
     *  above.
     */
    private boolean configureByName()
    {
        topicArn = retrieveAllTopicsByName().get(config.topicName);
        if (topicArn == null)
        {
            try
            {
                CreateTopicResult response = client.createTopic(config.topicName);
                topicArn = response.getTopicArn();
                return true;
            }
            catch (Exception ex)
            {
                LogLog.warn("unable to create topic: " + config.topicName, ex);
                return false;
            }
        }
        else
        {
            return true;
        }
    }


    /**
     *  Returns all of the account's topics.
     */
    private Set<String> retrieveAllTopics()
    {
        Set<String> result = new HashSet<String>();
        ListTopicsRequest request = new ListTopicsRequest();
        ListTopicsResult response = null;

        do
        {
            response = client.listTopics(request);
            for (Topic topic : response.getTopics())
            {
                result.add(topic.getTopicArn());
            }
            request.setNextToken(response.getNextToken());
        } while (! StringUtil.isEmpty(response.getNextToken()));

        return result;
    }


    /**
     *  Returns all of the account's topics, as a mapping from name to ARN.
     */
    private Map<String,String> retrieveAllTopicsByName()
    {
        Map<String,String> result = new HashMap<String,String>();
        for (String arn : retrieveAllTopics())
        {
            String topicName = arn.replaceFirst(".*:", "");
            result.put(topicName, arn);
        }
        return result;
    }
}
