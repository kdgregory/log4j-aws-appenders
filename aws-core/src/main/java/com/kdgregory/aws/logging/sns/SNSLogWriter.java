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

package com.kdgregory.aws.logging.sns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.*;

import com.kdgregory.aws.logging.common.LogMessage;
import com.kdgregory.aws.logging.internal.AbstractLogWriter;


public class SNSLogWriter
extends AbstractLogWriter
{
    protected SNSWriterConfig config;
    private SNSAppenderStatistics stats;

    protected AmazonSNS client;
    protected String topicArn;


    public SNSLogWriter(SNSWriterConfig config, SNSAppenderStatistics stats)
    {
        super(stats, 1, config.discardThreshold, config.discardAction);
        this.config = config;
        this.stats = stats;
    }

//----------------------------------------------------------------------------
//  AbstractLogWriter implementation
//----------------------------------------------------------------------------

    @Override
    protected void createAWSClient()
    {
        client = tryClientFactory(config.clientFactoryMethod, AmazonSNS.class, true);
        if ((client == null) && (config.clientEndpoint == null))
        {
            client = tryClientFactory("com.amazonaws.services.sns.AmazonSNSClientBuilder.defaultClient", AmazonSNS.class, false);
        }
        if (client == null)
        {
            // TODO - need a new way to report status
//            LogLog.debug(getClass().getSimpleName() + ": creating service client via constructor");
            client = tryConfigureEndpointOrRegion(new AmazonSNSClient(), config.clientEndpoint);
        }
    }


    @Override
    protected boolean ensureDestinationAvailable()
    {
        try
        {
            boolean topicAvailable = (config.topicArn != null)
                                   ? configureByArn()
                                   : configureByName();

            if (topicAvailable)
            {
                stats.setActualTopicArn(topicArn);
                stats.setActualTopicName(topicArn.replaceAll(".*:", ""));
            }

            return topicAvailable;

        }
        catch (Exception ex)
        {
            return initializationFailure("exception in initializer", ex);
        }
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
                PublishRequest request = new PublishRequest()
                                         .withTopicArn(topicArn)
                                         .withMessage(message.getMessage());
                if (config.subject != null)
                {
                    request.setSubject(config.subject);
                }
                client.publish(request);
            }
            catch (Exception ex)
            {
                stats.setLastError(null, ex);
                // TODO - need a new way to report status
//                LogLog.error("failed to send message", ex);
                failures.add(message);
            }
        }
        stats.updateMessagesSent(currentBatch.size() - failures.size());
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
        // note: we don't validate topic ARN because an invalid ARN won't be
        //       found in the list and therefore will fail initialization

        if (retrieveAllTopics().contains(config.topicArn))
        {
            topicArn = config.topicArn;
            return true;
        }
        else
        {
            return initializationFailure("unable to find specified topicArn: " + config.topicArn, null);
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
        String topicName = config.topicName;

        if (! Pattern.matches(SNSConstants.TOPIC_NAME_REGEX, config.topicName))
        {
            return initializationFailure("invalid topic name: " + topicName, null);
        }

        topicArn = retrieveAllTopicsByName().get(config.topicName);
        if (topicArn != null)
        {
            // TODO - need a new way to report status
//            LogLog.debug("using existing SNS topic: " + topicName);
            return true;
        }
        else if (config.autoCreate)
        {
            // TODO - need a new way to report status
//            LogLog.debug("creating SNS topic: " + topicName);
            CreateTopicResult response = client.createTopic(topicName);
            topicArn = response.getTopicArn();
            return true;
        }
        else
        {
            // TODO - need a new way to report status
//            LogLog.error("topic does not exist and auto-create not enabled: " + topicName);
            stats.setLastError("topic does not exist: " + topicName, null);
            return false;
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
        } while (response.getNextToken() != null);

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