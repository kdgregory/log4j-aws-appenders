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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.*;

import com.kdgregory.logging.aws.facade.v1.internal.ClientFactory;
import com.kdgregory.logging.aws.internal.facade.KinesisFacade;
import com.kdgregory.logging.aws.internal.facade.KinesisFacadeException;
import com.kdgregory.logging.aws.internal.facade.KinesisFacadeException.ReasonCode;
import com.kdgregory.logging.aws.kinesis.KinesisConstants.StreamStatus;
import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;
import com.kdgregory.logging.common.LogMessage;


public class KinesisFacadeImpl
implements KinesisFacade
{
    private final static Map<String,StreamStatus> STATUS_LOOKUP = new HashMap<>();
    static
    {
        STATUS_LOOKUP.put("ACTIVE",   StreamStatus.ACTIVE);
        STATUS_LOOKUP.put("CREATING", StreamStatus.CREATING);
        STATUS_LOOKUP.put("DELETING", StreamStatus.DELETING);
        STATUS_LOOKUP.put("UPDATING", StreamStatus.UPDATING);
    }

    private KinesisWriterConfig config;

    private AmazonKinesis client;

    public KinesisFacadeImpl(KinesisWriterConfig config)
    {
        this.config = config;
    }

//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    @Override
    public StreamStatus retrieveStreamStatus()
    {
        // TODO - if DescribeStreamSummary is available, use it
        try
        {
            DescribeStreamRequest request = new DescribeStreamRequest().withStreamName(config.getStreamName());
            DescribeStreamResult response = client().describeStream(request);
            return STATUS_LOOKUP.get(response.getStreamDescription().getStreamStatus());
        }
        catch (ResourceNotFoundException ex)
        {
            return StreamStatus.DOES_NOT_EXIST;
        }
        catch (LimitExceededException ex)
        {
            // the caller will retry on null, so no need to make them catch
            return null;
        }
        catch (Exception ex)
        {
            throw transformException("retrieveStreamStatus", ex);
        }
    }


    @Override
    public void createStream()
    {
        try
        {
            CreateStreamRequest request = new CreateStreamRequest()
                                          .withStreamName(config.getStreamName())
                                          .withShardCount(config.getShardCount());
            client().createStream(request);
        }
        catch (ResourceInUseException ex)
        {
            // someone has already created it
            return;
        }
        catch (Exception ex)
        {
            throw transformException("createStream", ex);
        }
    }


    @Override
    public void setRetentionPeriod()
    {
        if (config.getRetentionPeriod() == null)
            return;

        try
        {
            IncreaseStreamRetentionPeriodRequest request = new IncreaseStreamRetentionPeriodRequest()
                                                           .withStreamName(config.getStreamName())
                                                           .withRetentionPeriodHours(config.getRetentionPeriod());
            client().increaseStreamRetentionPeriod(request);
        }
        catch (Exception ex)
        {
            throw transformException("setRetentionPeriod", ex);
        }
    }


    @Override
    public List<LogMessage> putRecords(List<LogMessage> batch)
    {
        if (batch.isEmpty())
            return batch;

        try
        {
            PutRecordsRequest request = createPutRecordsRequest(batch);
            PutRecordsResult response = client().putRecords(request);
            return extractPutRecordsFailures(batch, response);
        }
        catch (Exception ex)
        {
            throw transformException("putRecords", ex);
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
     *  Returns the Kinesis client, lazily constructing it if needed.
     *  <p>
     *  This method is not threadsafe; it should be called only from the writer thread.
     */
    protected AmazonKinesis client()
    {
        if (client == null)
        {
            client = new ClientFactory<>(AmazonKinesis.class, config).create();
        }

        return client;
    }


    /**
     *  Creates a facade exception based on some other exception.
     */
    private KinesisFacadeException transformException(String functionName, Exception ex)
    {
        String message;
        ReasonCode reason;
        boolean isRetryable;

        if (ex instanceof ProvisionedThroughputExceededException)
        {
            message = "throttled";
            reason = ReasonCode.THROTTLING;
            isRetryable = true;
        }
        else if (ex instanceof LimitExceededException)
        {
            message = "limit exceeded";
            reason = ReasonCode.LIMIT_EXCEEDED;
            isRetryable = true;
        }
        else if (ex instanceof ResourceInUseException)
        {
            message = "stream not active";
            reason = ReasonCode.INVALID_STATE;
            isRetryable = true;
        }
        else
        {
            message = "unexpected exception: " + ex.getMessage();
            reason = ReasonCode.UNEXPECTED_EXCEPTION;
            isRetryable = false;
        }

        return new KinesisFacadeException(
                message, ex, reason, isRetryable,
                functionName, config.getStreamName());
    }


    private PutRecordsRequest createPutRecordsRequest(List<LogMessage> batch)
    {
        List<PutRecordsRequestEntry> requestRecords = new ArrayList<PutRecordsRequestEntry>(batch.size());
        for (LogMessage message : batch)
        {
            requestRecords.add(new PutRecordsRequestEntry()
                       .withPartitionKey(config.getPartitionKeyHelper().getValue())
                       .withData(ByteBuffer.wrap(message.getBytes())));
        }

        return new PutRecordsRequest()
                   .withStreamName(config.getStreamName())
                   .withRecords(requestRecords);
    }


    private List<LogMessage> extractPutRecordsFailures(List<LogMessage> batch, PutRecordsResult response)
    {
        List<LogMessage> result = new ArrayList<>(batch.size());

        Iterator<LogMessage> lmItx = batch.iterator();
        Iterator<PutRecordsResultEntry> rspItx = response.getRecords().iterator();
        while (lmItx.hasNext() && rspItx.hasNext())
        {
            LogMessage logMessage = lmItx.next();
            PutRecordsResultEntry entry = rspItx.next();
            if ((entry.getErrorCode() != null) && !entry.getErrorCode().isEmpty())
            {
                result.add(logMessage);
            }
        }

        // these two should match exactly, so not testing that there are any records remaining
        return result;
    }
}
