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

package com.kdgregory.logging.aws.facade.v1;

import java.util.List;
import java.util.stream.Collectors;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.facade.CloudWatchFacade;
import com.kdgregory.logging.aws.facade.CloudWatchFacadeException;
import com.kdgregory.logging.aws.facade.CloudWatchFacadeException.ReasonCode;
import com.kdgregory.logging.aws.facade.v1.internal.ClientFactory;
import com.kdgregory.logging.common.LogMessage;


/**
 *  Provides a facade over the CloudWatch API using the v1 SDK.
 */
public class CloudWatchFacadeImpl
implements CloudWatchFacade
{
    // passed to constructor
    private CloudWatchWriterConfig config;

    // lazily constructed; protected so that it can be set for testing
    protected AWSLogs client;


    public CloudWatchFacadeImpl(CloudWatchWriterConfig config)
    {
        this.config = config;
    }

//----------------------------------------------------------------------------
//  CloudWatchFacade
//----------------------------------------------------------------------------

    @Override
    public String findLogGroup()
    {
        String logGroupName = config.getLogGroupName();
        try
        {
            DescribeLogGroupsRequest request = new DescribeLogGroupsRequest().withLogGroupNamePrefix(logGroupName);
            DescribeLogGroupsResult result;
            do
            {
                result = client().describeLogGroups(request);
                for (LogGroup group : result.getLogGroups())
                {
                    if (group.getLogGroupName().equals(logGroupName))
                        return group.getArn();
                }
                request.setNextToken(result.getNextToken());
            } while (result.getNextToken() != null);

            return null;
        }
        catch (Exception ex)
        {
            CloudWatchFacadeException ex2 = transformException("findLogGroup", ex);
            if (ex2.isRetryable())
                return null;
            else
                throw ex2;
        }
    }


    @Override
    public void createLogGroup()
    {
        String logGroupName = config.getLogGroupName();
        try
        {
            CreateLogGroupRequest request = new CreateLogGroupRequest().withLogGroupName(logGroupName);
            client().createLogGroup(request);
            return;
        }
        catch (ResourceAlreadyExistsException ex)
        {
            // somebody else created it, nothing to do here
            return;
        }
        catch (Exception ex)
        {
            throw transformException("createLogGroup", ex);
        }
    }


    @Override
    public void setLogGroupRetention()
    {
        if (config.getRetentionPeriod() == null)
            return;

        try
        {
            client().putRetentionPolicy(
                new PutRetentionPolicyRequest(
                    config.getLogGroupName(), config.getRetentionPeriod()));
        }
        catch (InvalidParameterException ex)
        {
            throw new CloudWatchFacadeException(
                "invalid retention period: " + config.getRetentionPeriod(),
                ReasonCode.INVALID_CONFIGURATION,
                false,
                "setLogGroupRetention", config.getLogGroupName());
        }
        catch (Exception ex)
        {
            throw transformException("setLogGroupRetention", ex);
        }
    }


    @Override
    public String findLogStream()
    {
        String logGroupName = config.getLogGroupName();
        String logStreamName = config.getLogStreamName();

        DescribeLogStreamsRequest request = new DescribeLogStreamsRequest(logGroupName).withLogStreamNamePrefix(logStreamName);
        DescribeLogStreamsResult result;
        try
        {
            do
            {
                result = client().describeLogStreams(request);
                for (LogStream stream : result.getLogStreams())
                {
                    if (stream.getLogStreamName().equals(config.getLogStreamName()))
                        return stream.getArn();
                }
                request.setNextToken(result.getNextToken());
            } while (result.getNextToken() != null);
            return null;
        }
        catch (ResourceNotFoundException ex)
        {
            return null;
        }
        catch (Exception ex)
        {
            CloudWatchFacadeException ex2 = transformException("findLogStream", ex);
            if (ex2.isRetryable())
                return null;
            else
                throw ex2;
        }
    }


    @Override
    public void createLogStream()
    {
        String logGroupName = config.getLogGroupName();
        String logStreamName = config.getLogStreamName();

        try
        {
            CreateLogStreamRequest request = new CreateLogStreamRequest()
                                             .withLogGroupName(logGroupName)
                                             .withLogStreamName(logStreamName);
            client().createLogStream(request);
            return;
        }
        catch (ResourceAlreadyExistsException ex)
        {
            // somebody else created it, nothing to do here
            return;
        }
        catch (ResourceNotFoundException ex)
        {
            throw new CloudWatchFacadeException(
                "log group missing",
                ReasonCode.MISSING_LOG_GROUP,
                false,
                "createLogStream",
                config.getLogGroupName());
        }
        catch (Exception ex)
        {
            throw transformException("createLogStream", ex);
        }
    }


    @Override
    public void putEvents(List<LogMessage> messages)
    {
        if (messages.isEmpty())
            return;

        List<InputLogEvent> events
                = messages.stream()
                  .map(m -> new InputLogEvent().withTimestamp(m.getTimestamp()).withMessage(m.getMessage()))
                  .collect(Collectors.toList());

        PutLogEventsRequest request
                = new PutLogEventsRequest()
                  .withLogGroupName(config.getLogGroupName())
                  .withLogStreamName(config.getLogStreamName())
                  .withLogEvents(events);

        try
        {
            // the log-writer ensures that all events meet acceptance criteria, so we don't check
            // for failures (there's nothing we could do about it anyway)
            client().putLogEvents(request);
        }
        catch (ResourceNotFoundException ex)
        {
            throw new CloudWatchFacadeException(
                    "missing log group",
                    ReasonCode.MISSING_LOG_GROUP,
                    false,
                    "putEvents", config.getLogGroupName(), config.getLogStreamName());
        }
        catch (DataAlreadyAcceptedException ex)
        {
            throw new CloudWatchFacadeException(
                    "already processed",
                    ReasonCode.ALREADY_PROCESSED,
                    false,
                    "putEvents", config.getLogGroupName(), config.getLogStreamName());
        }
        catch (Exception ex)
        {
            throw transformException("putEvents", ex);
        }
    }


    @Override
    public void shutdown()
    {
        client().shutdown();
    }

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    /**
     *  Returns the CloudWatch Logs client, lazily constructing it if needed.
     *  <p>
     *  This method is not threadsafe; it should be called only from the writer thread.
     */
    protected AWSLogs client()
    {
        if (client == null)
        {
            client = new ClientFactory<>(AWSLogs.class, config).create();
        }

        return client;
    }


    /**
     *  Translates a source exception into an instance of CloudWatchFacadeException.
     */
    private CloudWatchFacadeException transformException(String functionName, Exception cause)
    {
        ReasonCode reason;
        String message;
        boolean isRetryable;

        if (cause == null)
        {
            reason = ReasonCode.UNEXPECTED_EXCEPTION;
            message = "coding error; exception not provided";
            isRetryable = false;
        }
        else if (cause instanceof OperationAbortedException)
        {
            reason = ReasonCode.ABORTED;
            message = "request aborted";
            isRetryable = true;
        }
        else if (cause instanceof AWSLogsException)
        {
            AWSLogsException ex = (AWSLogsException)cause;
            if ("ThrottlingException".equals(ex.getErrorCode()))
            {
                reason = ReasonCode.THROTTLING;
                message = "request throttled";
                isRetryable = true;
            }
            else
            {
                reason = ReasonCode.UNEXPECTED_EXCEPTION;
                message = "service exception: " + cause.getMessage();
                isRetryable = false;  // AWSLogsException considers some things retryable that we don't
            }
        }
        else
        {
            reason = ReasonCode.UNEXPECTED_EXCEPTION;
            message = "unexpected exception: " + cause.getMessage();
            isRetryable = false;
        }

        return new CloudWatchFacadeException(
                message, cause, reason, isRetryable,
                functionName, config.getLogGroupName(), config.getLogStreamName());
    }
}
