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
import java.util.function.Consumer;

import com.kdgregory.logging.aws.internal.AbstractLogWriter;
import com.kdgregory.logging.aws.internal.RetryManager;
import com.kdgregory.logging.aws.internal.facade.CloudWatchFacade;
import com.kdgregory.logging.aws.internal.facade.CloudWatchFacadeException;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.util.InternalLogger;


public class CloudWatchLogWriter
extends AbstractLogWriter<CloudWatchWriterConfig,CloudWatchWriterStatistics>
{
    // passed into constructor
    private CloudWatchFacade facade;

    // controls retries for describing stream and group
    protected RetryManager describeRetry = new RetryManager(50, 5000, true);

    // controls retries for group and stream creation
    protected RetryManager createRetry = new RetryManager(200, 5000, true);

    // controls retries for sending messages to CloudWatch
    protected RetryManager sendRetry = new RetryManager(200, 2000, true);

    // cache for sequence tokens when using a dedicated writer
    private String sequenceToken;

    public CloudWatchLogWriter(CloudWatchWriterConfig config, CloudWatchWriterStatistics stats, InternalLogger logger, CloudWatchFacade facade)
    {
        super(config, stats, logger);

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
            if (facade.findLogGroup() == null)
                createLogGroup();
            else
                logger.debug("using existing CloudWatch log group: " + config.getLogGroupName());

            if (facade.retrieveSequenceToken() == null)
                createLogStream();
            else
                logger.debug("using existing CloudWatch log stream: " + config.getLogStreamName());

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

        createRetry.invoke(() -> { facade.createLogGroup(); return Boolean.TRUE; },
                           new DefaultExceptionHandler());

        // the group isn't immediately ready for use after creation, so wait until it is
        String logGroupArn = describeRetry.invoke(() -> facade.findLogGroup());
        if (logGroupArn == null)
        {
            throw new RuntimeException("timed out while waiting for CloudWatch log group");
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
            // while there may be a condition that prevents the logger from operating,
            // settng retention period isn't critical so we'll just log and continue
            logger.error("exception setting retention policy", ex);
        }
    }


    private void createLogStream()
    {
        logger.debug("creating CloudWatch log stream: " + config.getLogStreamName());

        createRetry.invoke(() -> { facade.createLogStream(); return Boolean.TRUE; },
                           new DefaultExceptionHandler());

        // force a retrieve, and wait until it returns something
        sequenceToken = null;
        nextSequenceToken();
    }


    // note: protected so that it can be overridden for testing
    protected List<LogMessage> attemptToSend(List<LogMessage> batch)
    {
        if (batch.isEmpty())
            return batch;

        List<LogMessage> result = sendRetry.invoke(() ->
        {
            try
            {
                sequenceToken = facade.putEvents(nextSequenceToken(), batch);
                return Collections.emptyList();
            }
            catch (CloudWatchFacadeException ex)
            {
                switch (ex.getReason())
                {
                    case THROTTLING:
                        return null;
                    case INVALID_SEQUENCE_TOKEN:
                        // force the token to be fetched next time through
                        sequenceToken = null;
                        stats.updateWriterRaceRetries();
                        return null;
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
        });

        // either success or an exception
        if (result != null)
            return result;

        // if we get here we timed-out, need to figure out cause
        if (sequenceToken == null)
        {
            logger.warn("batch failed: unrecovered sequence token race");
            stats.updateUnrecoveredWriterRaceRetries();
        }
        else
        {
            logger.warn("batch failed: repeated throttling");
        }

        // in either case, return the original batch for reprocessing
        return batch;
    }


    private String nextSequenceToken()
    {
        if ((! config.getDedicatedWriter()) || (sequenceToken == null))
        {
            sequenceToken = describeRetry.invoke(() -> facade.retrieveSequenceToken());
        }

        return sequenceToken;
    }


    /**
     *  A common exception handler that will decide whether or not to retry.
     */
    private static class DefaultExceptionHandler
    implements Consumer<RuntimeException>
    {
        @Override
        public void accept(RuntimeException ex)
        {
            if ((ex instanceof CloudWatchFacadeException) && ((CloudWatchFacadeException)ex).isRetryable())
                return;
            else
                throw ex;
        }
    }
}
