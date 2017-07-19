// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.cloudwatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.helpers.LogLog;

import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;


public class CloudWatchLogWriter
implements LogWriter
{
    // this controls the number of times that we retry a send
    private final static int RETRY_LIMIT = 3;

    // to avoid race conditions when shutting down, we don't use a simple boolean
    // "is-running" flag; instead, we wait for this amount of millis for any last
    // messages to be added to the queue

    private final static long SHUTDOWN_WAIT = 500;

    private String groupName;
    private String streamName;

    private AWSLogsClient client;

    private volatile Long shutdownTime;
    private Thread dispatchThread;

    private LinkedBlockingDeque<LogMessage> messageQueue = new LinkedBlockingDeque<LogMessage>();


    public CloudWatchLogWriter(String logGroup, String logStream)
    {
        this.groupName = logGroup;
        this.streamName = logStream;
    }


//----------------------------------------------------------------------------
//  Implementation of LogWriter
//----------------------------------------------------------------------------

    @Override
    public void addBatch(List<LogMessage> batch)
    {
        // for now I'm going to assume that batches will be relatively small
        // (definitely less than 10k rows), so this won't cause excessive
        // contention; the alternative is to maintain a separate (concurrent)
        // batch queue, and then copy messages from that queue in the run loop
        // ... right now, that seems like another point of failure

        messageQueue.addAll(batch);
    }


    @Override
    public void stop()
    {
        shutdownTime = new Long(System.currentTimeMillis() + SHUTDOWN_WAIT);
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

        dispatchThread = Thread.currentThread();

        // the do-while loop here ensures that we attempt to process at least one batch,
        // even if the writer is started and immediately stopped; that's not likely to
        // happen in the real world, but does cause problems for our smoketest

        do
        {
            List<LogMessage> currentBatch = buildBatch();
            attemptToSend(currentBatch);
        } while (keepRunning());
    }


//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    /**
     *  Checks the "running" flag, but overrides if there are messages in queue.
     */
    private boolean keepRunning()
    {
        return shutdownTime != null
            && shutdownTime.longValue() > System.currentTimeMillis()
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

        LogMessage message = waitForFirstMessage();
        if (message == null)
            return batch;

        int batchBytes = 0;
        int batchCount = 0;
        while (message != null)
        {
            batchBytes += message.size() + CloudWatchConstants.MESSAGE_OVERHEAD;
            batchCount++;

            // the first message must never break this rule -- and shouldn't, as appender checks size
            if ((batchBytes >= CloudWatchConstants.MAX_BATCH_BYTES) || (batchCount == CloudWatchConstants.MAX_BATCH_COUNT))
            {
                messageQueue.addFirst(message);
                break;
            }

            batch.add(message);
            message = messageQueue.poll();
        }

        return batch;
    }


    private LogMessage waitForFirstMessage()
    {
        try
        {
            if (shutdownTime == null)
                return messageQueue.take();
            else
                return messageQueue.poll(SHUTDOWN_WAIT, TimeUnit.MILLISECONDS);
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
                sleepQuietly(250 * (attempt + 1));
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
        LogLog.debug("making log group and stream (this is first connection to AWS)");
        try
        {
            LogGroup logGroup = findLogGroup();
            if (logGroup == null)
            {
                CreateLogGroupRequest request = new CreateLogGroupRequest().withLogGroupName(groupName);
                client.createLogGroup(request);
            }

            LogStream logStream = findLogStream();
            if (logStream == null)
            {
                CreateLogStreamRequest request = new CreateLogStreamRequest()
                                                 .withLogGroupName(groupName)
                                                 .withLogStreamName(streamName);
                client.createLogStream(request);
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