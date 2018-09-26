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

package com.kdgregory.aws.logging.cloudwatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.aws.logging.common.LogMessage;
import com.kdgregory.aws.logging.internal.AbstractLogWriter;
import com.kdgregory.aws.logging.internal.InternalLogger;
import com.kdgregory.aws.logging.internal.Utils;


public class CloudWatchLogWriter
extends AbstractLogWriter
{
    private CloudWatchWriterConfig config;
    private CloudWatchAppenderStatistics stats;
    private InternalLogger logger;

    protected AWSLogs client;


    public CloudWatchLogWriter(CloudWatchWriterConfig config, CloudWatchAppenderStatistics stats, InternalLogger logger)
    {
        super(stats, logger, config.batchDelay, config.discardThreshold, config.discardAction);

        this.config = config;

        this.stats = stats;
        this.stats.setActualLogGroupName(config.logGroupName);
        this.stats.setActualLogStreamName(config.logStreamName);

        this.logger = logger;
    }

//----------------------------------------------------------------------------
//  Hooks for superclass
//----------------------------------------------------------------------------

    @Override
    protected void createAWSClient()
    {
        client = tryClientFactory(config.clientFactoryMethod, AWSLogs.class, true);
        if ((client == null) && (config.clientEndpoint == null))
        {
            client = tryClientFactory("com.amazonaws.services.logs.AWSLogsClientBuilder.defaultClient", AWSLogs.class, false);
        }
        if (client == null)
        {
            logger.debug(getClass().getSimpleName() + ": creating service client via constructor");
            client = tryConfigureEndpointOrRegion(new AWSLogsClient(), config.clientEndpoint);
        }
    }


    @Override
    protected boolean ensureDestinationAvailable()
    {
        if (! Pattern.matches(CloudWatchConstants.ALLOWED_GROUP_NAME_REGEX, config.logGroupName))
        {
            return initializationFailure("invalid log group name: " + config.logGroupName, null);
        }

        if (! Pattern.matches(CloudWatchConstants.ALLOWED_STREAM_NAME_REGEX, config.logStreamName))
        {
            return initializationFailure("invalid log stream name: " + config.logStreamName, null);
        }

        try
        {
            LogGroup logGroup = findLogGroup();
            if (logGroup == null)
            {
                logger.debug("creating CloudWatch log group: " + config.logGroupName);
                createLogGroup();
            }


            LogStream logStream = findLogStream();
            if (logStream == null)
            {
                logger.debug("creating CloudWatch log stream: " + config.logStreamName);
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
                                      .withLogGroupName(config.logGroupName)
                                      .withLogStreamName(config.logStreamName)
                                      .withLogEvents(constructLogEvents(batch));

        // if we can't find the stream we'll try to re-create it
        LogStream stream = findLogStream();
        if (stream == null)
        {
            logger.error("log stream missing: " + config.logStreamName, null);
            stats.setLastError("log stream missing: " + config.logStreamName, null);
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
            logger.error("failed to send batch", ex);
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
        DescribeLogGroupsRequest request = new DescribeLogGroupsRequest().withLogGroupNamePrefix(config.logGroupName);
        DescribeLogGroupsResult result;
        do
        {
            result = client.describeLogGroups(request);
            for (LogGroup group : result.getLogGroups())
            {
                if (group.getLogGroupName().equals(config.logGroupName))
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
                CreateLogGroupRequest request = new CreateLogGroupRequest().withLogGroupName(config.logGroupName);
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
                                            .withLogGroupName(config.logGroupName)
                                            .withLogStreamNamePrefix(config.logStreamName);
        DescribeLogStreamsResult result;
        do
        {
            result = client.describeLogStreams(request);
            for (LogStream stream : result.getLogStreams())
            {
                if (stream.getLogStreamName().equals(config.logStreamName))
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
                                             .withLogGroupName(config.logGroupName)
                                             .withLogStreamName(config.logStreamName);
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
