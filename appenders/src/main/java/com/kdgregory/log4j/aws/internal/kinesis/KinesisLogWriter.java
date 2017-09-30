// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.kinesis;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.helpers.LogLog;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClient;
import com.amazonaws.services.kinesis.model.*;

import com.kdgregory.log4j.aws.internal.shared.AbstractLogWriter;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.MessageQueue.DiscardAction;
import com.kdgregory.log4j.aws.internal.shared.Utils;


public class KinesisLogWriter
extends AbstractLogWriter
{
    // this controls the number of tries that we wait for a stream to become active
    // each try takes 1 second
    private final static int STREAM_ACTIVE_TRIES = 60;

    // this controls the number of times that we retry a send
    private final static int RETRY_LIMIT = 3;

    // this controls the number of times that we attempt to create a stream
    private final static int CREATE_RETRY_LIMIT = 12;

    // and how long we'll sleep between attempts
    private final static int CREATE_RETRY_SLEEP = 5000;


    private KinesisWriterConfig config;
    protected AmazonKinesis client;


    public KinesisLogWriter(KinesisWriterConfig config)
    {
        super(config.batchDelay, 10000, DiscardAction.none);
        this.config = config;
    }


//----------------------------------------------------------------------------
//  Hooks for superclass
//----------------------------------------------------------------------------

    @Override
    protected void createAWSClient()
    {
        client = new AmazonKinesisClient();
    }


    @Override
    protected boolean ensureDestinationAvailable()
    {
        try
        {
            if (getStreamStatus() == null)
            {
                createStream();
                waitForStreamToBeActive();
                setRetentionPeriodIfNeeded();
            }
            else
            {
                // already created but might just be created
                waitForStreamToBeActive();
            }
            return true;
        }
        catch (Exception ex)
        {
            LogLog.error("unable to configure logging stream: " + config.streamName, ex);
            return false;
        }
    }


    @Override
    protected List<LogMessage> processBatch(List<LogMessage> currentBatch)
    {
        PutRecordsRequest request = convertBatchToRequest(currentBatch);
        if (request != null)
        {
            List<Integer> failures = attemptToSend(request);
            return extractFailures(currentBatch, failures);
        }
        else
        {
            return Collections.emptyList();
        }
    }


    @Override
    protected int effectiveSize(LogMessage message)
    {
        return message.size() + config.partitionKeyLength;
    }



    @Override
    protected boolean withinServiceLimits(int batchBytes, int numMessages)
    {
        return (batchBytes < KinesisConstants.MAX_BATCH_BYTES)
            && (numMessages < KinesisConstants.MAX_BATCH_COUNT);
    }


//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    /**
     *  Attempts to create the stream, silently succeeding if it already exists.
     */
    private void createStream()
    {
        for (int retry = 0 ; retry < CREATE_RETRY_LIMIT ; retry++)
        {
            try
            {
                LogLog.debug("creating Kinesis stream: " + config.streamName + " with " + config.shardCount + " shards");
                CreateStreamRequest request = new CreateStreamRequest()
                                              .withStreamName(config.streamName)
                                              .withShardCount(config.shardCount);
                client.createStream(request);
                return;
            }
            catch (ResourceInUseException ignored)
            {
                // someone else created stream while we were trying; that's OK
                return;
            }
            catch (LimitExceededException ex)
            {
                // AWS limits number of streams that can be created; and also total
                // number of shards, with no way to distinguish; sleep a long time
                // but eventually time-out
                Utils.sleepQuietly(CREATE_RETRY_SLEEP);
            }
        }
        throw new IllegalStateException("unable to create stream after " + CREATE_RETRY_LIMIT + " tries");
    }


    /**
     *  Waits for stream to become active, logging a message and throwing if it doesn't
     *  within a set time.
     */
    private void waitForStreamToBeActive()
    {
        for (int ii = 0 ; ii < STREAM_ACTIVE_TRIES ; ii++)
        {
            if (StreamStatus.ACTIVE.toString().equals(getStreamStatus()))
            {
                return;
            }
            Utils.sleepQuietly(1000);
        }
        throw new IllegalStateException("stream did not become active within " + STREAM_ACTIVE_TRIES + " seconds");
    }


    /**
     *  Returns current stream status, null if the stream doesn't exist.
     */
    private String getStreamStatus()
    {
        try
        {
            DescribeStreamRequest request = new DescribeStreamRequest().withStreamName(config.streamName);
            DescribeStreamResult response = client.describeStream(request);
            return response.getStreamDescription().getStreamStatus();
        }
        catch (ResourceNotFoundException ex)
        {
            return null;
        }
    }


    /**
     *  If the caller has configured a retention period, set it.
     */
    private void setRetentionPeriodIfNeeded()
    {
        if (config.retentionPeriod != null)
        {
            try
            {
                client.increaseStreamRetentionPeriod(new IncreaseStreamRetentionPeriodRequest()
                                                     .withStreamName(config.streamName)
                                                     .withRetentionPeriodHours(config.retentionPeriod));
                waitForStreamToBeActive();
            }
            catch (InvalidArgumentException ignored)
            {
                // it's possible that someone else created the stream and set the retention period
            }
        }
    }


    private PutRecordsRequest convertBatchToRequest(List<LogMessage> batch)
    {
        if (batch.isEmpty())
            return null;

        List<PutRecordsRequestEntry> requestRecords = new ArrayList<PutRecordsRequestEntry>(batch.size());
        for (LogMessage message : batch)
        {
            requestRecords.add(new PutRecordsRequestEntry()
                       .withPartitionKey(config.partitionKey)
                       .withData(ByteBuffer.wrap(message.getBytes())));
        }

        return new PutRecordsRequest()
                   .withStreamName(config.streamName)
                   .withRecords(requestRecords);
    }


    /**
     *  Attempts to send current request, retrying on any request-level failure.
     *  Returns a list of the indexes for records that failed.
     */
    private List<Integer> attemptToSend(PutRecordsRequest request)
    {
        List<Integer> failures = new ArrayList<Integer>(request.getRecords().size());

        Exception lastException = null;
        for (int attempt = 0 ; attempt < RETRY_LIMIT ; attempt++)
        {
            try
            {
                PutRecordsResult response = client.putRecords(request);
                int ii = 0;
                for (PutRecordsResultEntry entry : response.getRecords())
                {
                    if (entry.getErrorCode() != null)
                    {
                        failures.add(Integer.valueOf(ii));
                    }
                    ii++;
                }
                return failures;
            }
            catch (Exception ex)
            {
                lastException = ex;
                Utils.sleepQuietly(250 * (attempt + 1));
            }
        }

        LogLog.error("failed to send batch after " + RETRY_LIMIT + " retries", lastException);
        for (int ii = 0 ; ii < request.getRecords().size() ; ii++)
        {
            failures.add(Integer.valueOf(ii));
        }
        return failures;
    }


    /**
     *  Re-inserts any failed records into the head of the message queue, so that they'll
     *  be picked up by the next batch.
     */
    private List<LogMessage> extractFailures(List<LogMessage> currentBatch, List<Integer> failureIndexes)
    {
        if (! failureIndexes.isEmpty())
        {
            List<LogMessage> messages = new ArrayList<LogMessage>(failureIndexes.size());
            for (Integer idx : failureIndexes)
            {
                messages.add(currentBatch.get(idx.intValue()));
            }
            return messages;
        }
        else
        {
            return Collections.emptyList();
        }
    }
}