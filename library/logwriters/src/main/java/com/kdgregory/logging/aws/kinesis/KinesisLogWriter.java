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

package com.kdgregory.logging.aws.kinesis;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import com.kdgregory.logging.aws.facade.KinesisFacade;
import com.kdgregory.logging.aws.facade.KinesisFacadeException;
import com.kdgregory.logging.aws.facade.KinesisFacadeException.ReasonCode;
import com.kdgregory.logging.aws.internal.AbstractLogWriter;
import com.kdgregory.logging.aws.kinesis.KinesisConstants.StreamStatus;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.util.InternalLogger;
import com.kdgregory.logging.common.util.RetryManager2;


/**
 *  Writes log messages to a Kinesis stream. Optionally creates the stream at
 *  startup, but will fail if the stream is deleted during operation.
 *  <p>
 *  Implementation note: protected instance variables are replaced by tests.
 */
public class KinesisLogWriter
extends AbstractLogWriter<KinesisWriterConfig,KinesisWriterStatistics>
{
    /**
     *  This string is used in configuration to specify random partition keys.
     */
    public final static String RANDOM_PARTITION_KEY_CONFIG = "{random}";

    // passed into constructor
    private KinesisFacade facade;

    // this controls retries for an initial DescribeStream, which should be fast
    protected RetryManager2 describeRetry = new RetryManager2("describe", Duration.ofMillis(50));

    // this controls retries for CreateStream and SetRetentionPeriod, which return quickly but are eventually consistent
    protected RetryManager2 createRetry = new RetryManager2("create", Duration.ofMillis(200));

    // this controls retries for a DescribeStream after creation
    // this can take a long time, so we use a relatively long sleep duration with no backoff
    protected RetryManager2 postCreateRetry = new RetryManager2("describe", Duration.ofMillis(200), false, true);

    // these control retries for PutRecords; note that we use a duration-based timeout
    protected Duration sendTimeout = Duration.ofMillis(2000);
    protected RetryManager2 sendRetry = new RetryManager2("send", Duration.ofMillis(200));


    public KinesisLogWriter(KinesisWriterConfig config, KinesisWriterStatistics stats, InternalLogger logger, KinesisFacade facade)
    {
        super(config, stats, logger);

        this.facade = facade;

        stats.setActualStreamName(config.getStreamName());
    }

//----------------------------------------------------------------------------
//  LogWriter overrides
//----------------------------------------------------------------------------

    @Override
    public int maxMessageSize()
    {
        return KinesisConstants.MAX_MESSAGE_BYTES - config.getPartitionKeyHelper().getLength();
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
                reportError("configuration error: " + error, null);
            return false;
        }

        Instant timeoutAt = Instant.now().plusMillis(config.getInitializationTimeout());

        try
        {
            // see if the stream already exists and short-circuit if yes
            logger.debug("checking status of stream: " + config.getStreamName());
            StreamStatus status = describeRetry.invoke(timeoutAt, () -> facade.retrieveStreamStatus());
            if (status == StreamStatus.ACTIVE)
                return true;

            if (status == StreamStatus.DOES_NOT_EXIST)
            {
                if (config.getAutoCreate())
                {
                    return createStream(timeoutAt) && setRetentionPeriod(timeoutAt);
                }
                else
                {
                    reportError("stream \"" + config.getStreamName() + "\" does not exist and auto-create not enabled", null);
                    return false;
                }
            }

            // this is here to catch the case where somebody else created the stream
            return waitForStreamToBeActive(timeoutAt);
        }
        catch (Exception ex)
        {
            reportError("exception during initialization", ex);
            return false;
        }
    }


    @Override
    protected List<LogMessage> sendBatch(List<LogMessage> currentBatch)
    {
        stats.setLastBatchSize(currentBatch.size());
        if (config.getEnableBatchLogging())
            logger.debug("about to write batch of " + currentBatch.size() + " message(s)");

        // this should never happen (we wait for at least one message in queue)
        if (currentBatch.isEmpty())
            return currentBatch;

        try
        {
            List<LogMessage> result = sendRetry.invoke(sendTimeout, () ->
            {
                try
                {
                    List<LogMessage> unsent = facade.putRecords(currentBatch);
                    if (config.getEnableBatchLogging())
                        logger.debug("wrote batch of " + currentBatch.size() + " message(s); " + unsent.size() + " rejected");
                    return unsent;
                }
                catch (KinesisFacadeException ex)
                {
                    if (ex.getReason() == ReasonCode.THROTTLING)
                    {
                        stats.incrementThrottledWrites();
                        return null;
                    }
                    if (! ex.isRetryable())
                    {
                        throw ex;
                    }
                    return null;
                }
            });

            if (result == null)
            {
                logger.warn("timeout while sending batch");
                return currentBatch;
            }
            return result;  // either empty or partial list of source messages
        }
        catch (Exception ex)
        {
            logger.warn("exception while sending batch", ex);
            return currentBatch;
        }
    }


    @Override
    protected int effectiveSize(LogMessage message)
    {
        return message.size() + config.getPartitionKeyHelper().getLength();
    }


    @Override
    protected boolean withinServiceLimits(int batchBytes, int numMessages)
    {
        return (batchBytes < KinesisConstants.MAX_BATCH_BYTES)
            && (numMessages <= KinesisConstants.MAX_BATCH_COUNT);
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
     *  Attempts to create the stream and waits for it to become ready, returning
     *  <code>true</code> if successful, <code>false</code> on timeout.
     */
    private boolean createStream(Instant timeoutAt)
    {
        logger.debug("creating kinesis stream: " + config.getStreamName());

        createRetry.invoke(timeoutAt,
                           () -> { facade.createStream() ; return Boolean.TRUE; },
                           new DefaultExceptionHandler());

        return waitForStreamToBeActive(timeoutAt);
    }


    /**
     *  Attempts to set the retention period on a stream (if configured) and waits
     *  for it to become ready. Returns <code>true</code> if successful or no need
     *  to act, <code>false</code> on timeout.
     */
    private boolean setRetentionPeriod(Instant timeoutAt)
    {
        if (config.getRetentionPeriod() == null)
            return true;

        logger.debug("setting retention period on stream \"" + config.getStreamName()
                     + "\" to " + config.getRetentionPeriod() + " hours");

        createRetry.invoke(timeoutAt,
                           () -> { facade.setRetentionPeriod() ; return Boolean.TRUE; },
                           new DefaultExceptionHandler());

        return waitForStreamToBeActive(timeoutAt);
    }


    /**
     *  Waits for stream to become active, logging a message if it doesn't.
     */
    private boolean waitForStreamToBeActive(Instant timeoutAt)
    {
        logger.debug("waiting for stream " + config.getStreamName() + " to become active");
        StreamStatus status = postCreateRetry.invoke(timeoutAt, () ->
        {
            StreamStatus check = facade.retrieveStreamStatus();
            return (check == StreamStatus.ACTIVE) ? check : null;
        });

        if (status != StreamStatus.ACTIVE)
        {
            logger.error("timeout waiting for stream " + config.getStreamName() + " to become active", null);
            return false;
        }

        return true;
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
            if ((ex instanceof KinesisFacadeException) && ((KinesisFacadeException)ex).isRetryable())
                return;
            else
                throw ex;
        }
    }
}
