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
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.internal.facade.CloudWatchFacade;
import com.kdgregory.logging.aws.internal.facade.CloudWatchFacadeException;
import com.kdgregory.logging.aws.internal.facade.CloudWatchFacadeException.ReasonCode;
import com.kdgregory.logging.common.LogMessage;


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
    throws CloudWatchFacadeException
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
            if (ex2.getReason() == ReasonCode.THROTTLING)
                return null;
            else
                throw ex2;
        }
    }


    @Override
    public void createLogGroup()
    throws CloudWatchFacadeException
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
    throws CloudWatchFacadeException
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
                constructExceptionMessage("setLogGroupRetention", "invalid retention period: " + config.getRetentionPeriod()),
                ReasonCode.INVALID_CONFIGURATION,
                ex);
        }
        catch (Exception ex)
        {
            throw transformException("setLogGroupRetention", ex);
        }
    }


    @Override
    public void createLogStream()
    throws CloudWatchFacadeException
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
                constructExceptionMessage("createLogStream", "log group missing"),
                ReasonCode.MISSING_LOG_GROUP,
                ex);
        }
        catch (Exception ex)
        {
            throw transformException("createLogStream", ex);
        }
    }


    @Override
    public String retrieveSequenceToken()
    throws CloudWatchFacadeException
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
                        return stream.getUploadSequenceToken();
                }
                request.setNextToken(result.getNextToken());
            } while (result.getNextToken() != null);
        }
        catch (ResourceNotFoundException ex)
        {
            return null;
        }
        catch (Exception ex)
        {
            CloudWatchFacadeException ex2 = transformException("retrieveSequenceToken()", ex);
            if (ex2.getReason() == ReasonCode.THROTTLING)
                return null;
            else
                throw ex2;
        }

        // hit end of the list without finding the stream
        return null;
    }


    @Override
    public String sendMessages(String sequenceToken, List<LogMessage> messages)
    throws CloudWatchFacadeException
    {
        if (messages.isEmpty())
            return sequenceToken;

        List<InputLogEvent> events
                = messages.stream()
                  .map(m -> new InputLogEvent().withTimestamp(m.getTimestamp()).withMessage(m.getMessage()))
                  .collect(Collectors.toList());

        PutLogEventsRequest request
                = new PutLogEventsRequest()
                  .withLogGroupName(config.getLogGroupName())
                  .withLogStreamName(config.getLogStreamName())
                  .withSequenceToken(sequenceToken)
                  .withLogEvents(events);


        try
        {
            PutLogEventsResult response = client().putLogEvents(request);
            return response.getNextSequenceToken();
        }
        catch (InvalidSequenceTokenException ex)
        {
            throw new CloudWatchFacadeException(
                    constructExceptionMessage("sendMessages", "invalid sequence token: " + sequenceToken),
                    ReasonCode.INVALID_SEQUENCE_TOKEN,
                    null);
        }
        catch (ResourceNotFoundException ex)
        {
            throw new CloudWatchFacadeException(
                    constructExceptionMessage("sendMessages", "missing log group"),
                    ReasonCode.MISSING_LOG_GROUP,
                    null);
        }
        catch (Exception ex)
        {
            throw transformException("sendMessages", ex);
        }
    }


    @Override
    public void shutdown() throws CloudWatchFacadeException
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
        if (client != null)
            return client;

        // TODO - this will be properly implemented in superclass
        return AWSLogsClientBuilder.defaultClient();
    }


    /**
     *  Constructs an exception message that identifies function, log group,
     *  and log stream.
     */
    private String constructExceptionMessage(String functionName, String message)
    {
        return functionName + "(" + config.getLogGroupName()
             + (config.getLogStreamName() != null ? "," + config.getLogStreamName() : "")
             + "): " + message;
    }


    /**
     *  Translates a source exception into an instance of CloudWatchFacadeException.
     */
    private CloudWatchFacadeException transformException(String functionName, Exception cause)
    {
        ReasonCode reason;
        String message;

        if (cause == null)
        {
            reason = ReasonCode.UNEXPECTED_EXCEPTION;
            message = "coding error; exception not provided";
        }
        else if (cause instanceof OperationAbortedException)
        {
            reason = ReasonCode.ABORTED;
            message = "request aborted";
        }
        else if (cause instanceof DataAlreadyAcceptedException)
        {
            reason = ReasonCode.ALREADY_PROCESSED;
            message = "already processed";
        }
        else if (cause instanceof AWSLogsException)
        {
            AWSLogsException ex = (AWSLogsException)cause;
            if ("ThrottlingException".equals(ex.getErrorCode()))
            {
                reason = ReasonCode.THROTTLING;
                message = "request throttled";
            }
            else
            {
                reason = ReasonCode.UNEXPECTED_EXCEPTION;
                message = "service exception: " + cause.getMessage();
            }
        }
        else
        {
            reason = ReasonCode.UNEXPECTED_EXCEPTION;
            message = "unexpected exception: " + cause.getMessage();
        }

        return new CloudWatchFacadeException(
                constructExceptionMessage(functionName, message), reason, cause);
    }
}
