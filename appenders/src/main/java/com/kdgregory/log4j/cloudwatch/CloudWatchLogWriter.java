// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.cloudwatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.log4j.helpers.LogLog;

import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.log4j.shared.LogWriter;
import com.kdgregory.log4j.shared.LogMessage;


/**
 *  This is where all the magic happens.
 */
class CloudWatchLogWriter
implements LogWriter
{
    private String groupName;
    private String streamName;

    private AWSLogsClient client;

    private volatile boolean running = true;
    private Thread dispatchThread;

    private ConcurrentLinkedQueue<List<LogMessage>> batchQueue = new ConcurrentLinkedQueue<List<LogMessage>>();


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
        batchQueue.add(batch);
    }


    @Override
    public void stop()
    {
        running = false;
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

        while (running)
        {
            List<LogMessage> currentBatch = batchQueue.poll();
            if (currentBatch != null)
            {
                attemptToSend(currentBatch);
            }
            else
            {
                sleepQuietly(100);
            }
        }
    }


//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

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


    private void attemptToSend(List<LogMessage> batch)
    {
        PutLogEventsRequest request = new PutLogEventsRequest()
                                      .withLogGroupName(groupName)
                                      .withLogStreamName(streamName)
                                      .withLogEvents(constructLogEvents(batch));

        // TODO - wrap this in a retry loop

        LogStream stream = findLogStream();
        request.setSequenceToken(stream.getUploadSequenceToken());
        client.putLogEvents(request);
    }


    private List<InputLogEvent> constructLogEvents(List<LogMessage> batch)
    {
        Collections.sort(batch);
        // TODO - verify that batch satisfies requirements in http://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_PutLogEvents.html

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
}