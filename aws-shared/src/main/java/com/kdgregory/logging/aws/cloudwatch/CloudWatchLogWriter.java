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

package com.kdgregory.logging.aws.cloudwatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.logging.aws.internal.AbstractLogWriter;
import com.kdgregory.logging.aws.internal.Utils;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.factories.ClientFactory;
import com.kdgregory.logging.common.util.InternalLogger;


public class CloudWatchLogWriter
extends AbstractLogWriter<CloudWatchWriterConfig,CloudWatchWriterStatistics,AWSLogs>
{
    // for retries, this is the initial wait time (with exponential backoff)
    private final static int INITIAL_RETRY_DELAY = 100;
    
    // this is how long we'll wait to overcome throttled DescribeLogStreams
    private final static int DESCRIBE_RETRY_TIMEOUT = 30 * 1000;

    // this is how long we'll wait to overcome colliding sequence numbers
    // it's short to facilitate testing; in practice the batch would be
    // immediately resubmitted
    private final static int SEQNUM_RETRY_TIMEOUT = 3 * 1000;

    // this is used when we are a dedicated writer
    private String sequenceToken;

    public CloudWatchLogWriter(CloudWatchWriterConfig config, CloudWatchWriterStatistics stats, InternalLogger logger, ClientFactory<AWSLogs> clientFactory)
    {
        super(config, stats, logger, clientFactory);

        this.stats.setActualLogGroupName(config.logGroupName);
        this.stats.setActualLogStreamName(config.logStreamName);
    }

//----------------------------------------------------------------------------
//  LogWriter overrides
//----------------------------------------------------------------------------

    @Override
    public boolean isMessageTooLarge(LogMessage message)
    {
        return (message.size() + CloudWatchConstants.MESSAGE_OVERHEAD)  > CloudWatchConstants.MAX_BATCH_BYTES;
    }

//----------------------------------------------------------------------------
//  Hooks for superclass
//----------------------------------------------------------------------------

    @Override
    protected boolean ensureDestinationAvailable()
    {
        if (! Pattern.matches(CloudWatchConstants.ALLOWED_GROUP_NAME_REGEX, config.logGroupName))
        {
            reportError("invalid log group name: " + config.logGroupName, null);
            return false;
        }

        if (! Pattern.matches(CloudWatchConstants.ALLOWED_STREAM_NAME_REGEX, config.logStreamName))
        {
            reportError("invalid log stream name: " + config.logStreamName, null);
            return false;
        }

        try
        {
            LogGroup logGroup = findLogGroup();
            if (logGroup == null)
            {
                logger.debug("creating CloudWatch log group: " + config.logGroupName);
                createLogGroup();
                optSetLogGroupRetentionPolicy();
            }
            else
            {
                logger.debug("using existing CloudWatch log group: " + config.logGroupName);
            }


            LogStream logStream = findLogStream();
            if (logStream == null)
            {
                logger.debug("creating CloudWatch log stream: " + config.logStreamName);
                createLogStream();
            }
            else
            {
                logger.debug("using existing CloudWatch log stream: " + config.logStreamName);
            }

            return true;
        }
        catch (Exception ex)
        {
            reportError("unable to configure log group/stream", ex);
            return false;
        }
    }


    @Override
    protected List<LogMessage> sendBatch(List<LogMessage> currentBatch)
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
        return (batchBytes <= CloudWatchConstants.MAX_BATCH_BYTES)
            && (numMessages <= CloudWatchConstants.MAX_BATCH_COUNT);
    }


    @Override
    protected void stopAWSClient()
    {
        client.shutdown();
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

        // sending is all-or-nothing with CloudWatch: if we receive any error
        // we'll return the entire batch -- the one exception being a writer race,
        // which we'll retry a few times before giving up because it should resolve
        // itself

        long retryDelay = INITIAL_RETRY_DELAY;
        long timeoutAt = System.currentTimeMillis() + SEQNUM_RETRY_TIMEOUT;
        while (System.currentTimeMillis() < timeoutAt)
        {
            try
            {
                request.setSequenceToken(getSequenceToken());
                PutLogEventsResult response = client.putLogEvents(request);
                sequenceToken = response.getNextSequenceToken();
                return Collections.emptyList();
            }
            catch (InvalidSequenceTokenException ex)
            {
                // force the token to be fetched next time through
                sequenceToken = null;
                stats.updateWriterRaceRetries();
                // fall through
            }
            catch (DataAlreadyAcceptedException ex)
            {
                reportError("received DataAlreadyAcceptedException, dropping batch", ex);
                return Collections.emptyList();
            }
            catch (ResourceNotFoundException ex)
            {
                reportError("log stream missing: " + config.logStreamName, null);
                ensureDestinationAvailable();
                return batch;
            }
            catch (Exception ex)
            {
                reportError("failed to send batch", ex);
                return batch;
            }

            // retryable errors come here
            Utils.sleepQuietly(retryDelay);
            retryDelay *= 2;
        }

        reportError("failed to send due to repeated InvalidSequenceTokenExceptions", null);
        stats.updateUnrecoveredWriterRaceRetries();
        return batch;
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


    private String getSequenceToken()
    {
        if (config.dedicatedWriter && (sequenceToken != null))
        {
            return sequenceToken;
        }

        LogStream stream = findLogStream();
        if (stream == null)
            throw new ResourceNotFoundException("stream appears to have been deleted");

        return stream.getUploadSequenceToken();
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


    private void optSetLogGroupRetentionPolicy()
    {
        if (config.retentionPeriod == null)
            return;

        logger.debug("setting retention policy on " + config.logGroupName
                     + " to " + config.retentionPeriod + " days");

        try
        {
            client.putRetentionPolicy(
                new PutRetentionPolicyRequest(config.logGroupName, config.retentionPeriod));
        }
        catch (Exception ex)
        {
            reportError("failed to set retention policy on log group " + config.logGroupName, ex);
        }
    }


    private LogStream findLogStream()
    {
        DescribeLogStreamsRequest request = new DescribeLogStreamsRequest()
                                            .withLogGroupName(config.logGroupName)
                                            .withLogStreamNamePrefix(config.logStreamName);
        DescribeLogStreamsResult result;
        try
        {
            do
            {
                result = describeStreamsWithRetry(request);
                for (LogStream stream : result.getLogStreams())
                {
                    if (stream.getLogStreamName().equals(config.logStreamName))
                        return stream;
                }
                request.setNextToken(result.getNextToken());
            } while (result.getNextToken() != null);
        }
        catch (Exception ex)
        {
            reportError("unable to describe log stream", ex);
        }
        
        return null;
    }


    // DescribeLogStreams is limited to 5 operations per second, which causes problems
    // for clusters that are starting up, so we'll retry with exponential backoff
    private DescribeLogStreamsResult describeStreamsWithRetry(DescribeLogStreamsRequest request)
    {
        long retryDelay = INITIAL_RETRY_DELAY;
        long timeoutAt = System.currentTimeMillis() + DESCRIBE_RETRY_TIMEOUT;
        while (System.currentTimeMillis() < timeoutAt)
        {
            try
            {
                return client.describeLogStreams(request);
            }
            catch (AmazonServiceException ex)
            {
                if (! ex.getMessage().contains("ThrottlingException"))
                    throw ex;
                // otherwise fall through to retry
            }
            Utils.sleepQuietly(retryDelay);
            retryDelay *= 2;
        }
        throw new RuntimeException("DescribeLogStreams has been throttled for " + DESCRIBE_RETRY_TIMEOUT + " ms");
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
