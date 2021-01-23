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

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;

import com.kdgregory.logging.aws.facade.SNSFacade;
import com.kdgregory.logging.aws.facade.SNSFacadeException;
import com.kdgregory.logging.aws.facade.SNSFacadeException.ReasonCode;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;
import com.kdgregory.logging.common.LogMessage;


/**
 *  Provides a facade over the SNS API using the v1 SDK.
 */
public class SNSFacadeImpl
implements SNSFacade
{
    private SNSWriterConfig config;

    private SnsClient client;


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
            for (Topic topic : client().listTopicsPaginator().topics())
            {
                String arn = topic.topicArn();
                if (arn.endsWith(match))
                    return arn;
            }
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
            CreateTopicRequest request = CreateTopicRequest.builder()
                                         .name(config.getTopicName())
                                         .build();
            CreateTopicResponse response = client().createTopic(request);
            return response.topicArn();
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
            PublishRequest request = PublishRequest.builder()
                                     .topicArn(config.getTopicArn())
                                     .subject(config.getSubject())
                                     .message(message.getMessage())
                                     .build();
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
        client().close();
    }

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    protected SnsClient client()
    {
        if (client == null)
        {
            // TODO - implement client factory
//            client = new ClientFactory<>(AmazonSNS.class, config).create();
            client = SnsClient.builder().build();
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
        else if (cause instanceof SnsException)
        {
            SnsException ex = (SnsException)cause;
            String errorCode = (ex.awsErrorDetails() != null) ? ex.awsErrorDetails().errorCode() : null;
            if ("Throttling".equals(errorCode))
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
            message = "unexpected exception: " + cause.getMessage();
            reason = ReasonCode.UNEXPECTED_EXCEPTION;
            isRetryable = false;
        }

        return new SNSFacadeException(
                message, cause, reason, isRetryable,
                functionName, (config.getTopicArn() != null) ? config.getTopicArn() : config.getTopicName());
    }

}
