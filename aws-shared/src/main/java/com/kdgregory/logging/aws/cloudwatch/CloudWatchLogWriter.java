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

package com.kdgregory.logging.aws.cloudwatch;

import java.util.Collections;
import java.util.List;

import com.kdgregory.logging.aws.internal.AbstractLogWriter;
import com.kdgregory.logging.aws.internal.Utils;
import com.kdgregory.logging.aws.internal.facade.CloudWatchFacade;
import com.kdgregory.logging.aws.internal.facade.CloudWatchFacadeException;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.util.InternalLogger;


public class CloudWatchLogWriter
extends AbstractLogWriter<CloudWatchWriterConfig,CloudWatchWriterStatistics,Object>
{
    // for creation, this is the number of retries that we'll attempt before giving up
    private final static int CREATION_RETRIES = 10;

    // for sending a batch, this is the number of retries that we'll attempt at one time
    private final static int SEND_RETRIES = 3;

    // for retries, this is the initial wait time (with exponential backoff)
    private final static int INITIAL_RETRY_DELAY = 100;

    // passed into constructor
    private CloudWatchFacade facade;

    // this is used when we are a dedicated writer
    private String sequenceToken;


    public CloudWatchLogWriter(CloudWatchWriterConfig config, CloudWatchWriterStatistics stats, InternalLogger logger, CloudWatchFacade facade)
    {
        super(config, stats, logger, null);

        this.facade = facade;

        this.stats.setActualLogGroupName(config.getLogGroupName());
        this.stats.setActualLogStreamName(config.getLogStreamName());
    }

//----------------------------------------------------------------------------
//  LogWriter overrides
//----------------------------------------------------------------------------

    @Override
    public int maxMessageSize()
    {
        return CloudWatchConstants.MAX_MESSAGE_SIZE;
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
                reportError(error, null);
            return false;
        }

        try
        {
            String logGroupArn = facade.findLogGroup();
            if (logGroupArn == null)
            {
                createLogGroup();
            }
            else
            {
                logger.debug("using existing CloudWatch log group: " + config.getLogGroupName());
            }


            sequenceToken = facade.retrieveSequenceToken();
            if (sequenceToken == null)
            {
                createLogStream();
            }
            else
            {
                logger.debug("using existing CloudWatch log stream: " + config.getLogStreamName());
            }

            return true;
        }
        catch (Throwable ex)
        {
            reportError("unable to configure log group/stream", ex);
            return false;
        }
    }


    @Override
    protected List<LogMessage> sendBatch(List<LogMessage> currentBatch)
    {
        Collections.sort(currentBatch);
        return attemptToSend(currentBatch);
    }


    @Override
    protected int effectiveSize(LogMessage message)
    {
        return message.size() + CloudWatchConstants.MESSAGE_OVERHEAD;
    }


    @Override
    protected boolean withinServiceLimits(int batchBytes, int numMessages)
    {
        return (batchBytes <= CloudWatchConstants.MAX_BATCH_BYTES)
            && (numMessages <= CloudWatchConstants.MAX_BATCH_COUNT);
    }


    @Override
    protected void stopAWSClient()
    {
        try
        {
            facade.shutdown();
        }
        catch (CloudWatchFacadeException ex)
        {
            reportError("exception shutting down CloudWatch client", ex);
        }
    }

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    private void createLogGroup()
    {
        logger.debug("creating CloudWatch log group: " + config.getLogGroupName());

        for (int retry = 0 ; retry < CREATION_RETRIES ; retry++)
        {
            try
            {
                facade.createLogGroup();
                break;
            }
            catch (CloudWatchFacadeException ex)
            {
                switch (ex.getReason())
                {
                    case THROTTLING:
                    case ABORTED:
                        Utils.sleepQuietly(250);
                        break;
                    default:
                        throw new RuntimeException(ex.getMessage(), ex.getCause());
                }
            }
        }

        try
        {
            for (int ii = 0 ; ii < 300 ; ii++)
            {
                if (facade.findLogGroup() != null)
                    break;
                Utils.sleepQuietly(100);
            }
        }
        catch(CloudWatchFacadeException ex)
        {
            throw new RuntimeException(ex.getMessage(), ex.getCause());
        }

        try
        {
            if (config.getRetentionPeriod() != null)
            {
                logger.debug("setting retention period to: " + config.getRetentionPeriod());
                facade.setLogGroupRetention();
            }
        }
        catch(CloudWatchFacadeException ex)
        {
            logger.error("exception setting retention policy", ex);
        }
    }


    private void createLogStream()
    {
        logger.debug("creating CloudWatch log stream: " + config.getLogStreamName());
        for (int retry = 0 ; retry < CREATION_RETRIES ; retry++)
        {
            try
            {
                facade.createLogStream();
                break;
            }
            catch(CloudWatchFacadeException ex)
            {
                if (! ex.isRetryable())
                {
                    throw new RuntimeException(ex.getMessage(), ex.getCause());
                }
                logger.warn("retryable exception when creating stream: " + ex.getMessage());
                Utils.sleepQuietly(INITIAL_RETRY_DELAY);
            }
        }

        // this will wait for the stream to become available
        // TODO - is it really necessary? can defer until first send
        sequenceToken();
    }


    // note: protected so that it can be overridden for testing
    protected List<LogMessage> attemptToSend(List<LogMessage> batch)
    {
        if (batch.isEmpty())
            return batch;

        long retryDelay = INITIAL_RETRY_DELAY;
        for (int retry = 0 ; retry < SEND_RETRIES ; retry++)
        {
            try
            {
                sequenceToken = facade.putEvents(sequenceToken(), batch);
                return Collections.emptyList();
            }
            catch (CloudWatchFacadeException ex)
            {
                switch (ex.getReason())
                {
                    case THROTTLING:
                        break;
                    case INVALID_SEQUENCE_TOKEN:
                        // force the token to be fetched next time through
                        sequenceToken = null;
                        stats.updateWriterRaceRetries();
                        break;
                    case ALREADY_PROCESSED:
                        logger.warn("received DataAlreadyAcceptedException, dropping batch");
                        return Collections.emptyList();
                    case MISSING_LOG_GROUP:
                    case MISSING_LOG_STREAM:
                        reportError(ex.getMessage(), ex);
                        ensureDestinationAvailable();
                        return batch;
                    default:
                        reportError("failed to send: " + ex.getMessage(), ex.getCause());
                        return batch;
                }
            }

            // retryable errors come here
            Utils.sleepQuietly(retryDelay);
            retryDelay *= 2;
        }

        if (sequenceToken == null)
        {
            logger.warn("batch failed: unrecovered sequence token race");
            stats.updateUnrecoveredWriterRaceRetries();
        }
        else
        {
            logger.warn("batch failed: repeated throttling");
        }
        return batch;
    }


    private String sequenceToken()
    {
        if ((! config.getDedicatedWriter()) || (sequenceToken == null))
        {
            // TODO - use a waiter object with exponential backoff
            for (int ii = 0 ; ii < 100 ; ii++)
            {
                try
                {
                    sequenceToken = facade.retrieveSequenceToken();
                    if (sequenceToken != null)
                        return sequenceToken;
                    Utils.sleepQuietly(100);
                }
                catch (CloudWatchFacadeException ex)
                {
                    throw new RuntimeException("failed to retrieve sequence token", ex.getCause());
                }
            }
        }

        return sequenceToken;
    }

}
