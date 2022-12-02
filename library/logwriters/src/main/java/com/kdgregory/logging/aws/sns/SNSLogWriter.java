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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.kdgregory.logging.aws.facade.SNSFacade;
import com.kdgregory.logging.aws.internal.AbstractLogWriter;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.util.InternalLogger;
import com.kdgregory.logging.common.util.RetryManager2;


/**
 *  Writes log messages to an SNS topic. Optionally creates the topic at
 *  startup, but will fail if the topic is deleted during operation.
 */
public class SNSLogWriter
extends AbstractLogWriter<SNSWriterConfig,SNSWriterStatistics>
{
    // provided by constructor
    private SNSFacade facade;

    // these control the retries for checking topic existence
    // we use a small number of retries in case of throttling; don't want a long timeout for a new topic
    protected Duration describeTimeout = Duration.ofMillis(2000);
    protected RetryManager2 describeRetry = new RetryManager2("describe", Duration.ofMillis(50), false, false);

    // this controls the retries for creating a topic
    protected RetryManager2 createRetry = new RetryManager2("create", Duration.ofMillis(200), true, true);


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

        Instant timeoutAt = Instant.now().plusMillis(config.getInitializationTimeout());

        try
        {
            logger.debug("checking for existance of SNS topic: " +
                         (config.getTopicArn() != null ? config.getTopicArn() : config.getTopicName()));
            String topicArn = facade.lookupTopic();
            if (topicArn == null)
            {
                topicArn = optCreateTopic(timeoutAt);
                if (topicArn == null)
                {
                    logger.error("failed to create SNS topic", null);
                    return false;
                }
            }

            // ARN is used by publish(), so ensure that it's set if we were configured by name
            config.setTopicArn(topicArn);

            stats.setActualTopicArn(topicArn);
            stats.setActualTopicName(topicArn.replaceAll(".*:", ""));
            return true;
        }
        catch (Throwable ex)
        {
            reportError("exception during initialization", ex);
            return false;
        }
    }


    @Override
    protected List<LogMessage> sendBatch(List<LogMessage> currentBatch)
    {
        // we process this as a list because we may be recovering from failures
        List<LogMessage> failures = new ArrayList<LogMessage>();
        for (LogMessage message : currentBatch)
        {
            try
            {
                if (config.getEnableBatchLogging())
                    logger.debug("about to publish 1 message");
                // don't retry; just let messages accumulate
                facade.publish(message);
                if (config.getEnableBatchLogging())
                    logger.debug("published 1 message");
            }
            catch (Exception ex)
            {
                reportError("failed to publish: " + ex.getMessage(), ex);
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
    private String optCreateTopic(Instant timeoutAt)
    {
        // we could have arrived here as a result of a throttled lookup call, so
        // we try again with retries and a short timeout
        String topicArn = describeRetry.invoke(describeTimeout, () -> facade.lookupTopic());
        if (topicArn != null)
            return topicArn;

        if (config.getTopicArn() != null)
        {
            reportError("topic " + config.getTopicArn() + " does not exist", null);
            return null;
        }

        if (! config.getAutoCreate())
        {
            reportError("topic " + config.getTopicName() + " does not exist and auto-create not enabled", null);
            return null;
        }

        logger.debug("creating topic: " + config.getTopicName());
        return createRetry.invoke(timeoutAt, () -> facade.createTopic());
    }
}
