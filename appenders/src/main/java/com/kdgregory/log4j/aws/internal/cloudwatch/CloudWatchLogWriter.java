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

package com.kdgregory.log4j.aws.internal.cloudwatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.log4j.helpers.LogLog;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.log4j.aws.internal.shared.AbstractLogWriter;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.Utils;


public class CloudWatchLogWriter
extends AbstractLogWriter
{
    private String groupName;
    private String streamName;
    private String clientFactoryMethod;
    private String clientEndpoint;

    protected AWSLogs client;

    private CloudWatchAppenderStatistics stats;


    public CloudWatchLogWriter(CloudWatchWriterConfig config, CloudWatchAppenderStatistics stats)
    {
        super(stats, config.batchDelay, config.discardThreshold, config.discardAction);
        this.groupName = config.logGroup;
        this.streamName = config.logStream;
        this.clientFactoryMethod = config.clientFactoryMethod;
        this.clientEndpoint = config.clientEndpoint;

        this.stats = stats;
        this.stats.setActualLogGroupName(this.groupName);
        this.stats.setActualLogStreamName(this.streamName);
    }

//----------------------------------------------------------------------------
//  Hooks for superclass
//----------------------------------------------------------------------------

    @Override
    protected void createAWSClient()
    {
        client = tryClientFactory(clientFactoryMethod, AWSLogs.class, true);
        if ((client == null) && (clientEndpoint == null))
        {
            client = tryClientFactory("com.amazonaws.services.logs.AWSLogsClientBuilder.defaultClient", AWSLogs.class, false);
        }
        if (client == null)
        {
            LogLog.debug(getClass().getSimpleName() + ": creating service client via constructor");
            client = tryConfigureEndpointOrRegion(new AWSLogsClient(), clientEndpoint);
        }
    }


    @Override
    protected boolean ensureDestinationAvailable()
    {
        if (! Pattern.matches(CloudWatchConstants.ALLOWED_GROUP_NAME_REGEX, groupName))
        {
            return initializationFailure("invalid log group name: " + groupName, null);
        }

        if (! Pattern.matches(CloudWatchConstants.ALLOWED_STREAM_NAME_REGEX, streamName))
        {
            return initializationFailure("invalid log stream name: " + streamName, null);
        }

        try
        {
            LogGroup logGroup = findLogGroup();
            if (logGroup == null)
            {
                LogLog.debug("creating CloudWatch log group: " + groupName);
                createLogGroup();
            }


            LogStream logStream = findLogStream();
            if (logStream == null)
            {
                LogLog.debug("creating CloudWatch log stream: " + streamName);
                createLogStream();
            }

            return true;
        }
        catch (Exception ex)
        {
            return initializationFailure("unable to configure log group/stream", ex);
        }
    }


    @Override
    protected List<LogMessage> processBatch(List<LogMessage> currentBatch)
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
        return (batchBytes < CloudWatchConstants.MAX_BATCH_BYTES)
            && (numMessages < CloudWatchConstants.MAX_BATCH_COUNT);
    }

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    private List<LogMessage> attemptToSend(List<LogMessage> batch)
    {
        if (batch.isEmpty())
            return batch;

        PutLogEventsRequest request = new PutLogEventsRequest()
                                      .withLogGroupName(groupName)
                                      .withLogStreamName(streamName)
                                      .withLogEvents(constructLogEvents(batch));

        // if we can't find the stream we'll try to re-create it
        LogStream stream = findLogStream();
        if (stream == null)
        {
            stats.setLastError("log stream missing: " + streamName, null);
            ensureDestinationAvailable();
            return batch;
        }

        // sending is all-or-nothing with CloudWatch; we'll return the entire batch
        // if there's an exception

        try
        {
            request.setSequenceToken(stream.getUploadSequenceToken());
            client.putLogEvents(request);
            stats.updateMessagesSent(batch.size());
            return Collections.emptyList();
        }
        catch (Exception ex)
        {
            LogLog.error("failed to send batch", ex);
            stats.setLastError(null, ex);
            return batch;
        }
    }


    private List<InputLogEvent> constructLogEvents(List<LogMessage> batch)
    {
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


    private LogGroup findLogGroup()
    {
        DescribeLogGroupsRequest request = new DescribeLogGroupsRequest().withLogGroupNamePrefix(groupName);
        DescribeLogGroupsResult result;
        do
        {
            result = client.describeLogGroups(request);
            for (LogGroup group : result.getLogGroups())
            {
                if (group.getLogGroupName().equals(groupName))
                    return group;
            }
            request.setNextToken(result.getNextToken());
        } while (result.getNextToken() != null);

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
        DescribeLogStreamsResult result;
        do
        {
            result = client.describeLogStreams(request);
            for (LogStream stream : result.getLogStreams())
            {
                if (stream.getLogStreamName().equals(streamName))
                    return stream;
            }
            request.setNextToken(result.getNextToken());
        } while (result.getNextToken() != null);
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
