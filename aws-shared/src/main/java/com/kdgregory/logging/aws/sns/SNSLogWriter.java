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

package com.kdgregory.logging.aws.sns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.*;

import com.kdgregory.logging.aws.internal.AbstractLogWriter;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.factories.ClientFactory;
import com.kdgregory.logging.common.util.InternalLogger;


public class SNSLogWriter
extends AbstractLogWriter<SNSWriterConfig,SNSWriterStatistics,AmazonSNS>
{
    // this is set when configuring by name, exposed for testing
    protected String topicArn;


    public SNSLogWriter(SNSWriterConfig config, SNSWriterStatistics stats, InternalLogger logger, ClientFactory<AmazonSNS> clientFactory)
    {
        super(config, stats, logger, clientFactory);
        stats.setActualTopicName(config.topicName);
        stats.setActualTopicArn(config.topicArn);
        stats.setActualSubject(config.subject);
    }

//----------------------------------------------------------------------------
//  LogWriter overrides
//----------------------------------------------------------------------------

    /**
     *  Sets the subject for subsequent messages. Any substitutions must be
     *  applied before calling this method.
     */
    public void setSubject(String subject)
    {
        config.subject = subject;
        stats.setActualSubject(subject);
    }

//----------------------------------------------------------------------------
//  LogWriter overrides
//----------------------------------------------------------------------------

    @Override
    public boolean isMessageTooLarge(LogMessage message)
    {
        return message.size() > SNSConstants.MAX_MESSAGE_BYTES;
    }


    @Override
    public int maxMessageSize()
    {
        return SNSConstants.MAX_MESSAGE_BYTES;
    }

//----------------------------------------------------------------------------
//  Hooks for superclass
//----------------------------------------------------------------------------

    @Override
    protected boolean ensureDestinationAvailable()
    {
        if ((config.subject != null) && ! config.subject.isEmpty())
        {
            if (config.subject.length() >= 100)
            {
                reportError("invalid subject (too long): " + config.subject, null);
                return false;
            }
            if (config.subject.matches(".*[^\u0020-\u007d].*"))
            {
                reportError("invalid subject (disallowed characters): " + config.subject, null);
                return false;
            }
            if (config.subject.startsWith(" "))
            {
                reportError("invalid subject (starts with space): " + config.subject, null);
                return false;
            }
        }

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
            reportError("unable to configure", ex);
            return false;
        }
    }


    @Override
    protected List<LogMessage> sendBatch(List<LogMessage> currentBatch)
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


    @Override
    protected void stopAWSClient()
    {
        client.shutdown();
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
            reportError("topic does not exist: " + config.topicArn, null);
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
        String topicName = config.topicName;

        if (! Pattern.matches(SNSConstants.TOPIC_NAME_REGEX, config.topicName))
        {
            reportError("invalid topic name: " + topicName, null);
            return false;
        }

        topicArn = retrieveAllTopicsByName().get(config.topicName);
        if (topicArn != null)
        {
            return true;
        }
        else if (config.autoCreate)
        {
            logger.debug("creating SNS topic: " + topicName);
            CreateTopicResult response = client.createTopic(topicName);
            topicArn = response.getTopicArn();
            return true;
        }
        else
        {
            reportError("topic does not exist and auto-create not enabled: " + topicName, null);
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
