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

import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.MessageQueue;
import com.kdgregory.log4j.aws.internal.shared.MessageQueue.DiscardAction;
import com.kdgregory.log4j.aws.internal.shared.Utils;


public class KinesisLogWriter
implements LogWriter
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

    private Thread dispatchThread;
    private AmazonKinesis client;

    private volatile Long shutdownTime;     // set on another thread
    private volatile int batchCount;        // can be read via accessor method by other threads

    private MessageQueue messageQueue = new MessageQueue(10000, DiscardAction.none);


    public KinesisLogWriter(KinesisWriterConfig config)
    {
        this.config = config;
    }


//----------------------------------------------------------------------------
//  Implementation of LogWriter
//----------------------------------------------------------------------------

    @Override
    public void addMessage(LogMessage message)
    {
        messageQueue.enqueue(message);
    }


    @Override
    public void setBatchDelay(long value)
    {
        config.batchDelay = value;
    }


    @Override
    public void stop()
    {
        shutdownTime = new Long(System.currentTimeMillis() + config.batchDelay);
        if (dispatchThread != null)
        {
            dispatchThread.interrupt();
        }
    }


//----------------------------------------------------------------------------
//  Implementation of Runnable
//----------------------------------------------------------------------------

    @Override
    public void run()
    {
        client = createClient();
        ensureStreamAvailable();

        // initialize the dispatch thread here so that an interrupt will only affect the code
        // that waits for messages; not likely to happen in real world, but does in smoketest

        dispatchThread = Thread.currentThread();

        // the do-while loop ensures that we attempt to process at least one batch, even if
        // the writer is started and immediately stopped; again, that's not likely to happen
        // in the real world, but was causing problems with the smoketest

        do
        {
            List<LogMessage> currentBatch = buildBatch();
            PutRecordsRequest request = convertBatchToRequest(currentBatch);
            if (request != null)
            {
                List<Integer> failures = attemptToSend(request);
                requeueFailures(currentBatch, failures);
            }
        } while (keepRunning());
    }


//----------------------------------------------------------------------------
//  Other public methods
//----------------------------------------------------------------------------

    /**
     *  Returns the current batch delay. This is intended for testing.
     */
    public long getBatchDelay()
    {
        return config.batchDelay;
    }


    /**
     *  Returns the number of batches processed. This is intended for testing
     */
    public int getBatchCount()
    {
        return batchCount;
    }


//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    /**
     *  This method is exposed so that we can create a mock client for tests.
     */
    protected AmazonKinesis createClient()
    {
        return new AmazonKinesisClient();
    }


    /**
     *  If the stream is not available, attempts to create it and waits until
     *  it's active.
     */
    private void ensureStreamAvailable()
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
        }
        catch (Exception ex)
        {
            throw new IllegalStateException("unable to configure logging stream: " + config.streamName, ex);
        }
    }


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


    /**
     *  A check for whether we should keep running: either we haven't been shut
     *  down or there's still messages to process
     */
    private boolean keepRunning()
    {
        return (shutdownTime == null)
             ? true
             : shutdownTime.longValue() > System.currentTimeMillis()
               && messageQueue.isEmpty();
    }


    /**
     *  Blocks until at least one message is available on the queue, then
     *  retrieves as many messages as will fit in one request.
     */
    private List<LogMessage> buildBatch()
    {
        // presizing to a small-but-possible size to avoid repeated resizes
        List<LogMessage> batch = new ArrayList<LogMessage>(512);

        // normally we wait "forever" for the first message, unless we're shutting down
        long firstMessageTimeout = (shutdownTime != null) ? shutdownTime.longValue() : Long.MAX_VALUE;

        LogMessage message = waitForMessage(firstMessageTimeout);
        if (message == null)
            return batch;

        long batchTimeout = System.currentTimeMillis() + config.batchDelay;
        int batchBytes = 0;
        int batchMsgs = 0;
        while (message != null)
        {
            batchBytes += message.size() + config.partitionKeyLength;
            batchMsgs++;

            // if this message would exceed the batch limits, push it back onto the queue
            // the first message must never break this rule -- and shouldn't, as appender checks size
            if ((batchBytes >= KinesisConstants.MAX_BATCH_BYTES) || (batchMsgs == KinesisConstants.MAX_BATCH_COUNT))
            {
                messageQueue.requeue(message);
                break;
            }

            batch.add(message);
            message = waitForMessage(batchTimeout);
        }

        return batch;
    }


    private LogMessage waitForMessage(long waitUntil)
    {
        long waitTime = waitUntil - System.currentTimeMillis();
        return messageQueue.dequeue(waitTime);
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
        batchCount++;
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
    private void requeueFailures(List<LogMessage> currentBatch, List<Integer> failures)
    {
        Collections.reverse(failures);
        for (Integer idx : failures)
        {
            messageQueue.requeue(currentBatch.get(idx.intValue()));
        }
    }
}