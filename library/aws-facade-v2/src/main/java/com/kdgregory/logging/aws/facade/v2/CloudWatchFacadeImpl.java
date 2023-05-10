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

package com.kdgregory.logging.aws.facade.v2;

import java.util.List;
import java.util.stream.Collectors;

import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;
import software.amazon.awssdk.services.cloudwatchlogs.paginators.*;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.facade.CloudWatchFacade;
import com.kdgregory.logging.aws.facade.CloudWatchFacadeException;
import com.kdgregory.logging.aws.facade.CloudWatchFacadeException.ReasonCode;
import com.kdgregory.logging.aws.facade.v2.internal.ClientFactory;
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
    protected CloudWatchLogsClient client;


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
            DescribeLogGroupsRequest request = DescribeLogGroupsRequest.builder()
                                               .logGroupNamePrefix(logGroupName)
                                               .build();
            DescribeLogGroupsIterable itx = client().describeLogGroupsPaginator(request);
            for (LogGroup logGroup : itx.logGroups())
            {
                if (logGroup.logGroupName().equals(logGroupName))
                    return logGroup.arn();
            }

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
            CreateLogGroupRequest request = CreateLogGroupRequest.builder()
                                            .logGroupName(logGroupName)
                                            .build();
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
        PutRetentionPolicyRequest request = PutRetentionPolicyRequest.builder()
                                        .logGroupName(config.getLogGroupName())
                                        .retentionInDays(config.getRetentionPeriod())
                                        .build();
        client().putRetentionPolicy(request);
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

        DescribeLogStreamsRequest request = DescribeLogStreamsRequest.builder()
                                            .logGroupName(logGroupName)
                                            .logStreamNamePrefix(logStreamName)
                                            .build();

        try
        {
            DescribeLogStreamsIterable itx = client().describeLogStreamsPaginator(request);
            for (LogStream stream : itx.logStreams())
            {
                if (stream.logStreamName().equals(config.getLogStreamName()))
                    return stream.arn();
            }
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
            CreateLogStreamRequest request = CreateLogStreamRequest.builder()
                                            .logGroupName(logGroupName)
                                            .logStreamName(logStreamName)
                                            .build();
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
                  .map(m -> InputLogEvent.builder().timestamp(m.getTimestamp()).message(m.getMessage()).build())
                  .collect(Collectors.toList());

        PutLogEventsRequest request
                = PutLogEventsRequest.builder()
                  .logGroupName(config.getLogGroupName())
                  .logStreamName(config.getLogStreamName())
                  .logEvents(events)
                  .build();

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
        client().close();
    }

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    /**
     *  Returns the CloudWatch Logs client, lazily constructing it if needed.
     *  <p>
     *  This method is not threadsafe; it should be called only from the writer thread.
     */
    protected CloudWatchLogsClient client()
    {
        if (client == null)
        {
            client = new ClientFactory<>(CloudWatchLogsClient.class, config).create();
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
        else if (cause instanceof CloudWatchLogsException)
        {
            CloudWatchLogsException ex = (CloudWatchLogsException)cause;
            String errorCode = (ex.awsErrorDetails() != null) ? ex.awsErrorDetails().errorCode() : null;
            if ("ThrottlingException".equals(errorCode))
            {
                reason = ReasonCode.THROTTLING;
                message = "request throttled";
                isRetryable = true;
            }
            else
            {
                reason = ReasonCode.UNEXPECTED_EXCEPTION;
                message = "service exception: " + cause.getMessage();
                isRetryable = false;  // SDKException considers some things retryable that we don't
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
