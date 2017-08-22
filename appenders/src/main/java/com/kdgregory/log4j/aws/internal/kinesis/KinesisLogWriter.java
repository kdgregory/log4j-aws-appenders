// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.kinesis;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.helpers.LogLog;

import com.amazonaws.services.kinesis.AmazonKinesisClient;
import com.amazonaws.services.kinesis.model.*;

import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;


public class KinesisLogWriter
implements LogWriter
{
    // this controls the number of tries that we wait for a stream to become active
    // each try takes 1 second
    private final static int STREAM_ACTIVE_TRIES = 60;

    // this controls the number of times that we retry a send
    private final static int RETRY_LIMIT = 3;

    private String streamName;
    private String partitionKey;
    private int shardCount = 1;
    private long batchDelay;

    private Thread dispatchThread;
    private AmazonKinesisClient client;

    private volatile Long shutdownTime;
    private volatile int batchCount;

    private LinkedBlockingDeque<LogMessage> messageQueue = new LinkedBlockingDeque<LogMessage>();


    public KinesisLogWriter(String streamName, String partitionKey, long batchDelay)
    {
        this.streamName = streamName;
        this.partitionKey = partitionKey;
        // TODO - add shard count
        this.batchDelay = batchDelay;
    }


//----------------------------------------------------------------------------
//  Implementation of LogWriter
//----------------------------------------------------------------------------

    @Override
    public void addMessage(LogMessage message)
    {
        messageQueue.add(message);
    }


    @Override
    public void setBatchDelay(long value)
    {
        this.batchDelay = value;
    }


    @Override
    public void stop()
    {
        shutdownTime = new Long(System.currentTimeMillis() + batchDelay);
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
        // unclear why this has to be initialized here, but it throws (failing class init!)
        // if assigned in constructor
        client = new AmazonKinesisClient();

        if (! ensureStreamAvailable()) return;

        // initialize the dispatch thread here so that an interrupt will only affect the code
        // that waits for messages; not likely to happen in real world, but does in smoketest

        dispatchThread = Thread.currentThread();

        // the do-while loop ensures that we attempt to process at least one batch, even if
        // the writer is started and immediately stopped; again, that's not likely to happen
        // in the real world, but was causing problems with the smoketest

        do
        {
            List<LogMessage> currentBatch = buildBatch();
            attemptToSend(currentBatch);
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
        return batchDelay;
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
     *  A check for whether we should keep running: either we haven't been shut
     *  down or there's still messages to process
     */
    private boolean keepRunning()
    {
        return (shutdownTime == null)
             ? true
             : shutdownTime.longValue() > System.currentTimeMillis()
               && messageQueue.peek() == null;
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

        long batchTimeout = System.currentTimeMillis() + batchDelay;
        int batchBytes = 0;
        int batchMsgs = 0;
        while (message != null)
        {
            // TODO - need to add size of partition key
            batchBytes += message.size();
            batchMsgs++;

            // if this message would exceed the batch limits, push it back onto the queue
            // the first message must never break this rule -- and shouldn't, as appender checks size
            if ((batchBytes >= KinesisConstants.MAX_BATCH_BYTES) || (batchMsgs == KinesisConstants.MAX_BATCH_COUNT))
            {
                messageQueue.addFirst(message);
                break;
            }

            batch.add(message);
            message = waitForMessage(batchTimeout);
        }

        return batch;
    }


    private LogMessage waitForMessage(long waitUntil)
    {
        try
        {
            long waitTime = waitUntil - System.currentTimeMillis();
            if (waitTime < 0) waitTime = 1;
            return messageQueue.poll(waitTime, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException ex)
        {
            return null;
        }
    }


    private void attemptToSend(List<LogMessage> batch)
    {
        if (batch.isEmpty())
            return;

        batchCount++;

        List<PutRecordsRequestEntry> requestRecords = new ArrayList<PutRecordsRequestEntry>(batch.size());
        for (LogMessage message : batch)
        {
            try
            {
                byte[] messageBytes = message.getMessage().getBytes("UTF-8");
                requestRecords.add(new PutRecordsRequestEntry()
                                       .withPartitionKey(partitionKey)
                                       .withData(ByteBuffer.wrap(messageBytes)));
            }
            catch (UnsupportedEncodingException ex)
            {
                LogLog.error("failed to convert message to UTF-8 bytes; possible JVM problem", ex);
            }
        }

        PutRecordsRequest request = new PutRecordsRequest()
                                        .withStreamName(streamName)
                                        .withRecords(requestRecords);

        Exception lastException = null;
        for (int attempt = 0 ; attempt < RETRY_LIMIT ; attempt++)
        {
            try
            {
                PutRecordsResult response = client.putRecords(request);
                // TODO - examine response to find any errors, re-process
                return;
            }
            catch (Exception ex)
            {
                lastException = ex;
                sleepQuietly(250 * (attempt + 1));
            }
        }

        LogLog.error("failed to send batch after " + RETRY_LIMIT + " retries", lastException);
    }


    private boolean ensureStreamAvailable()
    {
        try
        {
            try
            {
                // this will throw if the stream doesn't exist
                getStreamStatus();
            }
            catch (ResourceNotFoundException ex)
            {
                LogLog.debug("creating Kinesis stream: " + streamName + " with " + shardCount + " shards");
                CreateStreamRequest request = new CreateStreamRequest()
                                              .withStreamName(streamName)
                                              .withShardCount(shardCount);
                client.createStream(request);
                sleepQuietly(250);
            }

            for (int ii = 0 ; ii < STREAM_ACTIVE_TRIES ; ii++)
            {
                if (getStreamStatus().equals(StreamStatus.ACTIVE.toString()))
                {
                    return true;
                }
                sleepQuietly(1000);
            }

            LogLog.error("timed-out waiting for stream to become active: " + streamName);
            return false;
        }
        catch (Exception ex)
        {
            LogLog.error("unable to configure logging stream: " + streamName, ex);
            return false;
        }
    }


    private String getStreamStatus()
    {
        DescribeStreamRequest request = new DescribeStreamRequest().withStreamName(streamName);
        DescribeStreamResult response = client.describeStream(request);
        return response.getStreamDescription().getStreamStatus();
    }


    private void sleepQuietly(long time)
    {
        try
        {
            Thread.sleep(time);
        }
        catch (InterruptedException ignored)
        {
            // this will simply break to the enclosing loop
        }
    }
}