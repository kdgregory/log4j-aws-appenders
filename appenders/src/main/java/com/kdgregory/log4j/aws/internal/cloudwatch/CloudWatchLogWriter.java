// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.cloudwatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.helpers.LogLog;

import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.MessageQueue;
import com.kdgregory.log4j.aws.internal.shared.MessageQueue.DiscardAction;
import com.kdgregory.log4j.aws.internal.shared.Utils;


public class CloudWatchLogWriter
implements LogWriter
{
    // this controls the number of times that we retry a send
    private final static int RETRY_LIMIT = 3;

    private String groupName;
    private String streamName;
    private long batchDelay;

    private Thread dispatchThread;
    private AWSLogsClient client;

    private volatile Long shutdownTime;
    private volatile int batchCount;

    private MessageQueue messageQueue = new MessageQueue(10000, DiscardAction.none);


    public CloudWatchLogWriter(CloudWatchWriterConfig config)
    {
        this.groupName = config.logGroup;
        this.streamName = config.logStream;
        this.batchDelay = config.batchDelay;
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
        client = new AWSLogsClient();

        if (! ensureGroupAndStreamAvailable()) return;

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

        long batchTimeout = System.currentTimeMillis() + batchDelay;
        int batchBytes = 0;
        int batchMsgs = 0;
        while (message != null)
        {
            batchBytes += message.size() + CloudWatchConstants.MESSAGE_OVERHEAD;
            batchMsgs++;

            // if this message would exceed the batch limits, push it back onto the queue
            // the first message must never break this rule -- and shouldn't, as appender checks size
            if ((batchBytes >= CloudWatchConstants.MAX_BATCH_BYTES) || (batchMsgs == CloudWatchConstants.MAX_BATCH_COUNT))
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


    private void attemptToSend(List<LogMessage> batch)
    {
        if (batch.isEmpty())
            return;

        batchCount++;
        PutLogEventsRequest request = new PutLogEventsRequest()
                                      .withLogGroupName(groupName)
                                      .withLogStreamName(streamName)
                                      .withLogEvents(constructLogEvents(batch));

        Exception lastException = null;
        for (int attempt = 0 ; attempt < RETRY_LIMIT ; attempt++)
        {
            try
            {
                LogStream stream = findLogStream();
                request.setSequenceToken(stream.getUploadSequenceToken());
                client.putLogEvents(request);
                return;
            }
            catch (Exception ex)
            {
                lastException = ex;
                Utils.sleepQuietly(250 * (attempt + 1));
            }
        }

        LogLog.error("failed to send batch after " + RETRY_LIMIT + " retries", lastException);
    }


    private List<InputLogEvent> constructLogEvents(List<LogMessage> batch)
    {
        Collections.sort(batch);

        List<InputLogEvent> result = new ArrayList<InputLogEvent>(batch.size());
        for (LogMessage msg : batch)
        {
            InputLogEvent event = new InputLogEvent()
                                      .withTimestamp(msg.getTimestamp())
                                      .withMessage(msg.getMessage());
            result.add(event);
        }
        return result;
    }


    private boolean ensureGroupAndStreamAvailable()
    {
        try
        {
            LogGroup logGroup = findLogGroup();
            if (logGroup == null)
            {
                LogLog.debug("creating log group: " + groupName);
                createLogGroup();
            }


            LogStream logStream = findLogStream();
            if (logStream == null)
            {
                createLogStream();
            }

            return true;
        }
        catch (Exception ex)
        {
            LogLog.error("unable to configure log group/stream", ex);
            return false;
        }
    }


    private LogGroup findLogGroup()
    {
        DescribeLogGroupsRequest request = new DescribeLogGroupsRequest().withLogGroupNamePrefix(groupName);
        DescribeLogGroupsResult result = client.describeLogGroups(request);
        for (LogGroup group : result.getLogGroups())
        {
            if (group.getLogGroupName().equals(groupName))
                return group;
        }
        return null;
    }


    private void createLogGroup()
    {
        while (true)
        {
            try
            {
                CreateLogGroupRequest request = new CreateLogGroupRequest().withLogGroupName(groupName);
                client.createLogGroup(request);
                for (int ii = 0 ; ii < 300 ; ii++)
                {
                    if (findLogGroup() != null)
                        return;
                    else
                        Utils.sleepQuietly(100);
                }
                throw new RuntimeException("unable to create log group after 30 seconds; aborting");
            }
            catch (ResourceAlreadyExistsException ex)
            {
                // somebody else created it
                return;
            }
            catch (OperationAbortedException ex)
            {
                // someone else is trying to create it, wait and try again
                Utils.sleepQuietly(250);
            }
        }
    }


    private LogStream findLogStream()
    {
        DescribeLogStreamsRequest request = new DescribeLogStreamsRequest()
                                            .withLogGroupName(groupName)
                                            .withLogStreamNamePrefix(streamName);
        DescribeLogStreamsResult result = client.describeLogStreams(request);
        for (LogStream stream : result.getLogStreams())
        {
            if (stream.getLogStreamName().equals(streamName))
                return stream;
        }
        return null;
    }


    private void createLogStream()
    {
        try
        {
            CreateLogStreamRequest request = new CreateLogStreamRequest()
                                             .withLogGroupName(groupName)
                                             .withLogStreamName(streamName);
            client.createLogStream(request);

            for (int ii = 0 ; ii < 300 ; ii++)
            {
                if (findLogStream() != null)
                    return;
                else
                    Utils.sleepQuietly(100);
            }
            throw new RuntimeException("unable to create log strean after 30 seconds; aborting");
        }
        catch (ResourceAlreadyExistsException ex)
        {
            // somebody else created it
            return;
        }
    }
}
