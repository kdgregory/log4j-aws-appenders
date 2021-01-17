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

import java.util.List;
import java.util.function.Consumer;

import com.kdgregory.logging.aws.facade.KinesisFacade;
import com.kdgregory.logging.aws.facade.KinesisFacadeException;
import com.kdgregory.logging.aws.internal.AbstractLogWriter;
import com.kdgregory.logging.aws.kinesis.KinesisConstants.StreamStatus;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.util.InternalLogger;
import com.kdgregory.logging.common.util.RetryManager;


/**
 *  Writes log messages to a Kinesis stream. Optionally creates the stream at
 *  startup, but will fail if the stream is deleted during operation.
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

    // controls retries for initial stream describe; may be throttled
    protected RetryManager describeRetry = new RetryManager(50, 1000, true);

    // controls retries for stream creation; may be throttled
    protected RetryManager createRetry = new RetryManager(200, 5000, true);

    // controls retries for describing stream after; may take a long time
    protected RetryManager postCreateRetry = new RetryManager(200, 120000, true);

    // controls retries for sending messages; may be thottled
    protected RetryManager sendRetry = new RetryManager(200, 2000, true);


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

        try
        {
            // short-circuit if stream exists and is active; retry in case of throttling
            StreamStatus status = describeRetry.invoke(() -> facade.retrieveStreamStatus());
            if (status == StreamStatus.ACTIVE)
                return true;

            if (status == StreamStatus.DOES_NOT_EXIST)
            {
                if (config.getAutoCreate())
                {
                    return createStream() && setRetentionPeriod();
                }
                else
                {
                    reportError("stream \"" + config.getStreamName() + "\" does not exist and auto-create not enabled", null);
                    return false;
                }
            }

            // if we got here, then the stream was being created/updated by someone
            // else; wait until that's done
            return waitForStreamToBeActive();
        }
        catch (Exception ex)
        {
            reportError("unable to configure stream: " + config.getStreamName(), ex);
            return false;
        }
    }


    @Override
    protected List<LogMessage> sendBatch(List<LogMessage> currentBatch)
    {
        try
        {
            List<LogMessage> result = sendRetry.invoke(
                                            () -> facade.putRecords(currentBatch),
                                            new DefaultExceptionHandler());
            if (result == null)
            {
                logger.warn("timeout while sending batch");
                return currentBatch;
            }
            return result;  // either empty or partial list of source messages
        }
        catch (Exception ex)
        {
            logger.error("exception while sending batch", ex);
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
    private boolean createStream()
    {
        logger.debug("creating kinesis stream: " + config.getStreamName());

        createRetry.invoke(() -> { facade.createStream() ; return Boolean.TRUE; },
                           new DefaultExceptionHandler());

        return waitForStreamToBeActive();
    }


    /**
     *  Attempts to set the retention period on a stream (if configured) and waits
     *  for it to become ready. Returns <code>true</code> if successful or no need
     *  to act, <code>false</code> on timeout.
     */
    private boolean setRetentionPeriod()
    {
        if (config.getRetentionPeriod() == null)
            return true;

        logger.debug("setting retention period on stream \"" + config.getStreamName()
                     + "\" to " + config.getRetentionPeriod() + " hours");

        createRetry.invoke(() -> { facade.setRetentionPeriod() ; return Boolean.TRUE; },
                           new DefaultExceptionHandler());

        return waitForStreamToBeActive();
    }


    /**
     *  Waits for stream to become active, logging a message if it doesn't.
     */
    private boolean waitForStreamToBeActive()
    {
        StreamStatus status = postCreateRetry.invoke( () ->
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
