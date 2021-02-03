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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.*;

import com.kdgregory.logging.aws.facade.KinesisFacade;
import com.kdgregory.logging.aws.facade.KinesisFacadeException;
import com.kdgregory.logging.aws.facade.KinesisFacadeException.ReasonCode;
import com.kdgregory.logging.aws.facade.v2.internal.ClientFactory;
import com.kdgregory.logging.aws.kinesis.KinesisConstants.StreamStatus;
import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;
import com.kdgregory.logging.common.LogMessage;


/**
 *  Provides a facade over the Kinesis API using the v1 SDK.
 */
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

    private KinesisClient client;

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
        try
        {
            DescribeStreamSummaryRequest request = DescribeStreamSummaryRequest.builder()
                                                   .streamName(config.getStreamName())
                                                   .build();
            DescribeStreamSummaryResponse response = client().describeStreamSummary(request);
            return STATUS_LOOKUP.get(response.streamDescriptionSummary().streamStatusAsString());
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
            CreateStreamRequest request = CreateStreamRequest.builder()
                                          .streamName(config.getStreamName())
                                          .shardCount(config.getShardCount())
                                          .build();
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
            IncreaseStreamRetentionPeriodRequest request = IncreaseStreamRetentionPeriodRequest.builder()
                                                           .streamName(config.getStreamName())
                                                           .retentionPeriodHours(config.getRetentionPeriod())
                                                           .build();
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
            PutRecordsResponse response = client().putRecords(request);
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
        client().close();
    }

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    /**
     *  Returns the Kinesis client, lazily constructing it if needed.
     *  <p>
     *  This method is not threadsafe; it should be called only from the writer thread.
     */
    protected KinesisClient client()
    {
        if (client == null)
        {
            client = new ClientFactory<>(KinesisClient.class, config).create();
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
        List<PutRecordsRequestEntry> requestRecords = new ArrayList<>(batch.size());
        for (LogMessage message : batch)
        {
            PutRecordsRequestEntry entry = PutRecordsRequestEntry.builder()
                                           .partitionKey(config.getPartitionKeyHelper().getValue())
                                           .data(SdkBytes.fromByteArray(message.getBytes()))
                                           .build();
            requestRecords.add(entry);
        }

        return PutRecordsRequest.builder()
               .streamName(config.getStreamName())
               .records(requestRecords)
               .build();
    }


    private List<LogMessage> extractPutRecordsFailures(List<LogMessage> batch, PutRecordsResponse response)
    {
        List<LogMessage> result = new ArrayList<>(batch.size());
        if ((response.failedRecordCount() == null) || (response.failedRecordCount().intValue() == 0))
            return result;

        Iterator<LogMessage> lmItx = batch.iterator();
        Iterator<PutRecordsResultEntry> rspItx = response.records().iterator();
        while (lmItx.hasNext() && rspItx.hasNext())
        {
            LogMessage logMessage = lmItx.next();
            PutRecordsResultEntry entry = rspItx.next();
            if ((entry.errorCode() != null) && !entry.errorCode().isEmpty())
            {
                result.add(logMessage);
            }
        }

        return result;
    }
}
