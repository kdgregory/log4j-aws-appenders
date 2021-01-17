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

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.*;

import com.kdgregory.logging.aws.facade.SNSFacade;
import com.kdgregory.logging.aws.facade.SNSFacadeException;
import com.kdgregory.logging.aws.facade.SNSFacadeException.ReasonCode;
import com.kdgregory.logging.aws.facade.v1.internal.ClientFactory;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;
import com.kdgregory.logging.common.LogMessage;


/**
 *  Provides a facade over the SNS API using the v1 SDK.
 */
public class SNSFacadeImpl
implements SNSFacade
{
    private SNSWriterConfig config;

    private AmazonSNS client;


    public SNSFacadeImpl(SNSWriterConfig config)
    {
        this.config = config;
    }

//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    @Override
    public String lookupTopic()
    {
        String match = (config.getTopicArn() != null)
                     ? config.getTopicArn()
                     : ":" + config.getTopicName();

        try
        {
            ListTopicsRequest request = new ListTopicsRequest();
            do
            {
                ListTopicsResult response = client().listTopics(request);
                for (Topic topic : response.getTopics())
                {
                    String arn = topic.getTopicArn();
                    if (arn.endsWith(match))
                        return arn;
                }
                request.setNextToken(response.getNextToken());
            } while ((request.getNextToken() != null) && ! request.getNextToken().isEmpty());

            return null;
        }
        catch (Exception ex)
        {
            throw transformException("lookupTopic", ex);
        }
    }


    @Override
    public String createTopic()
    {
        try
        {
            CreateTopicRequest request = new CreateTopicRequest(config.getTopicName());
            CreateTopicResult response = client().createTopic(request);
            return response.getTopicArn();
        }
        catch (Exception ex)
        {
            throw transformException("createTopic", ex);
        }
    }


    @Override
    public void publish(LogMessage message)
    {
        if ((config.getTopicArn() == null) || config.getTopicArn().isEmpty())
            throw new SNSFacadeException("ARN not configured", ReasonCode.INVALID_CONFIGURATION, false, "publish");

        try
        {
            PublishRequest request = new PublishRequest()
                                     .withTopicArn(config.getTopicArn())
                                     .withSubject(config.getSubject())
                                     .withMessage(message.getMessage());
            client().publish(request);
        }
        catch (Exception ex)
        {
            throw transformException("publish", ex);
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

    protected AmazonSNS client()
    {
        if (client == null)
        {
            client = new ClientFactory<>(AmazonSNS.class, config).create();
        }

        return client;
    }


    /**
     *  Creates a facade exception based on some other exception.
     */
    private SNSFacadeException transformException(String functionName, Exception cause)
    {
        String message;
        ReasonCode reason;
        boolean isRetryable;

        if (cause instanceof NotFoundException)
        {
            reason = ReasonCode.MISSING_TOPIC;
            message = "topic does not exist";
            isRetryable = false;
        }
        else if (cause instanceof AmazonSNSException)
        {
            AmazonSNSException ex = (AmazonSNSException)cause;
            if ("Throttling".equals(ex.getErrorCode()))
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
            message = "unexpected exception: " + cause.getMessage();
            reason = ReasonCode.UNEXPECTED_EXCEPTION;
            isRetryable = false;
        }

        return new SNSFacadeException(
                message, cause, reason, isRetryable,
                functionName, (config.getTopicArn() != null) ? config.getTopicArn() : config.getTopicName());
    }

}
