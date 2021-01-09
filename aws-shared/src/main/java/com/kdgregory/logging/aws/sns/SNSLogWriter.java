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
import java.util.List;

import com.kdgregory.logging.aws.internal.AbstractLogWriter;
import com.kdgregory.logging.aws.internal.facade.SNSFacade;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.util.InternalLogger;


public class SNSLogWriter
extends AbstractLogWriter<SNSWriterConfig,SNSWriterStatistics>
{
    // provided by constructor
    private SNSFacade facade;


    public SNSLogWriter(SNSWriterConfig config, SNSWriterStatistics stats, InternalLogger logger, SNSFacade facade)
    {
        super(config, stats, logger);
        this.facade = facade;
        stats.setActualTopicName(config.getTopicName());
        stats.setActualTopicArn(config.getTopicArn());
        stats.setActualSubject(config.getSubject());
    }

//----------------------------------------------------------------------------
//  LogWriter overrides
//----------------------------------------------------------------------------

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
        List<String> configErrors = config.validate();
        if (! configErrors.isEmpty())
        {
            for (String error : configErrors)
            {
                reportError("configuration error: " + error, null);
            }
            return false;
        }

        try
        {
            String topicArn = facade.lookupTopic();
            if (topicArn == null)
            {
                topicArn = optCreateTopic();
                if (topicArn == null)
                    return false;
            }

            // ARN is used by publish(), so ensure that it's set
            config.setTopicArn(topicArn);

            stats.setActualTopicArn(topicArn);
            stats.setActualTopicName(topicArn.replaceAll(".*:", ""));
            return true;
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
                facade.publish(message);
            }
            catch (Exception ex)
            {
                stats.setLastError("failed to publish: " + ex.getMessage(), ex);
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
        facade.shutdown();
    }

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    /**
     *  Called during initialization if the topic doesn't exist. Decides whether
     *  we should try to create it.
     */
    private String optCreateTopic()
    {
        if (config.getTopicArn() != null)
        {
            reportError("topic does not exist: " + config.getTopicArn(), null);
            return null;
        }

        if (! config.getAutoCreate())
        {
            reportError("topic does not exist and auto-create not enabled: " + config.getTopicName(), null);
            return null;
        }

        logger.debug("creating SNS topic: " + config.getTopicName());
        return facade.createTopic();
    }
}
