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

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import com.kdgregory.logging.aws.facade.CloudWatchFacade;
import com.kdgregory.logging.aws.facade.CloudWatchFacadeException;
import com.kdgregory.logging.aws.internal.AbstractLogWriter;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.util.InternalLogger;
import com.kdgregory.logging.common.util.RetryManager2;


/**
 *  Writes messages to a CloudWatch log stream. Auto-create both group and stream
 *  if they do not exist, and re-creates them if they're deleted during operation.
 *  <p>
 *  Implementation note: the various retry managers are exposed so that tests can
 *  replace them with shorter delays.
 */
public class CloudWatchLogWriter
extends AbstractLogWriter<CloudWatchWriterConfig,CloudWatchWriterStatistics>
{
    // setting the sequence token to this value triggers retrieval
    private final static String SEQUENCE_TOKEN_FLAG_VALUE = "";

    // passed into constructor
    private CloudWatchFacade facade;

    // this controls the retries for DescribeLogGroup and DescribeLogStream
    protected RetryManager2 describeRetry = new RetryManager2("describe", Duration.ofMillis(50), true, true);

    // this controls the retries for creating and configuring groups and streams
    protected RetryManager2 createRetry = new RetryManager2("create", Duration.ofMillis(200), true, true);

    // these control the retries for PutEvents; note that sends use a duration-based timeout
    protected Duration sendTimeout = Duration.ofMillis(2000);
    protected RetryManager2 sendRetry = new RetryManager2("send", Duration.ofMillis(200), true, false);

    // cache for sequence tokens when using a dedicated writer; also used as a flag to trigger retrieve
    private String sequenceToken = SEQUENCE_TOKEN_FLAG_VALUE;


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

        Instant timeoutAt = Instant.now().plusMillis(config.getInitializationTimeout());

        try
        {
            logger.debug("checking for existence of CloudWatch log group: " + config.getLogGroupName());
            if (facade.findLogGroup() == null)  // will throw if no network connection
                createLogGroup(timeoutAt);
            else
                logger.debug("using existing CloudWatch log group: " + config.getLogGroupName());

            logger.debug("checking for existence of CloudWatch log stream: " + config.getLogStreamName());
            if (facade.findLogStream() == null)
                createLogStream(timeoutAt);
            else
                logger.debug("using existing CloudWatch log stream: " + config.getLogStreamName());

            return true;
        }
        catch (Throwable ex)
        {
            reportError("exception during initialization", ex);
            return false;
        }
    }


    @Override
    protected List<LogMessage> sendBatch(List<LogMessage> batch)
    {
        stats.setLastBatchSize(batch.size());
        if (config.getEnableBatchLogging())
            logger.debug("about to write batch of " + batch.size() + " message(s)");

        // this should never happen (we wait for at least one message in queue)
        if (batch.isEmpty())
            return batch;

        // CloudWatch wants all messages to be sorted by timestamp
        Collections.sort(batch);

        Instant timeoutAt = Instant.now().plus(sendTimeout);
        List<LogMessage> result = sendRetry.invoke(timeoutAt, () ->
        {
            try
            {
                sequenceToken = facade.putEvents(retrieveSequenceToken(timeoutAt), batch);
                if (config.getEnableBatchLogging())
                    logger.debug("wrote batch of " + batch.size() + " message(s)");
                return Collections.emptyList();
            }
            catch (CloudWatchFacadeException ex)
            {
                switch (ex.getReason())
                {
                    case THROTTLING:
                        stats.incrementThrottledWrites();
                        return null;
                    case INVALID_SEQUENCE_TOKEN:
                        // force the token to be fetched next time through
                        sequenceToken = SEQUENCE_TOKEN_FLAG_VALUE;
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
            catch (Exception ex)
            {
                logger.error("unexpected exception in sendBatch()", ex);
                return batch;
            }
        });

        // either success or an exception
        if (result != null)
            return result;

        // if we get here we timed-out, need to figure out cause
        if (sequenceToken.equals(SEQUENCE_TOKEN_FLAG_VALUE))
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

    private void createLogGroup(Instant timeoutAt)
    {
        logger.debug("creating CloudWatch log group: " + config.getLogGroupName());

        createRetry.invoke(timeoutAt,
                           () -> { facade.createLogGroup(); return Boolean.TRUE; },
                           new DefaultExceptionHandler());

        // wait for the log group to be created, throw if it never is
        String logGroupArn = describeRetry.invoke(timeoutAt, () -> facade.findLogGroup());

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


    private void createLogStream(Instant timeoutAt)
    {
        logger.debug("creating CloudWatch log stream: " + config.getLogStreamName());

        createRetry.invoke(timeoutAt,
                           () -> { facade.createLogStream(); return Boolean.TRUE; },
                           new DefaultExceptionHandler());

        // wait for the log stream to be created, throw if it never happens
        describeRetry.invoke(timeoutAt, () -> facade.findLogStream());

        // new log streams use a null sequence token
        sequenceToken = null;
    }


    private String retrieveSequenceToken(Instant timeoutAt)
    {
        if ((! config.getDedicatedWriter()) || (SEQUENCE_TOKEN_FLAG_VALUE.equals(sequenceToken)))
        {
            // this might throw a facade exception, which will be caught by sendBatch()
            sequenceToken = facade.retrieveSequenceToken();
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
