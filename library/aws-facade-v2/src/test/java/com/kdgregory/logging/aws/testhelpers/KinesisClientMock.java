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

package com.kdgregory.logging.aws.testhelpers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import net.sf.kdgcommons.lang.StringUtil;

import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.*;


/**
 *  Supports mock-object testing of the Kinesis facade.
 *  <p>
 *  This is a proxy-based mock: you create an instance of the mock, and from it
 *  create an instance of a proxy that implements the client interface. Each of
 *  the supported client methods is implemented in the mock, and called from the
 *  invocation handler. To test specific behaviors, subclasses should override
 *  the method implementation.
 *  <p>
 *  Each method has an associated invocation counter, along with variables that
 *  hold the last set of arguments passed to this method. These variables are
 *  public, to minimize boilerplate code; if testcases modify the variables, they
 *  only hurt themselves.
 *  <p>
 *  The mock is assumed to be invoked from a single thread, so no effort has been
 *  taken to make it threadsafe.
 *  <p>
 *  This mock can be configured with a single "known" stream, and the "describe"
 *  operation will respond appropriately. Also, the "create" operation updates the
 *  known streams, so a create followed by a describe will behave appropriately.
 */
public class KinesisClientMock
implements InvocationHandler
{
    // the list of known streams, initialized by the constructor
    public List<String> knownStreams = new ArrayList<String>();

    // this provides a countdown for the CREATING status from describe;
    // by default all describes return ACTIVE if the stream exists
    public int creatingStatusCount;

    // the number of times each method was invoked
    public volatile int describeStreamSummaryInvocationCount;
    public volatile int createStreamInvocationCount;
    public volatile int putRecordsInvocationCount;
    public volatile int increaseRetentionPeriodInvocationCount;
    public volatile int closeInvocationCount;

    // arguments passed to the last describeStream call
    public volatile String describeStreamStreamName;

    // arguments passed to the last createStream call
    public volatile String createStreamStreamName;
    public volatile Integer createStreamShardCount;

    // arguments passed to the last increaseStreamRetentionPeriod call
    public String increaseRetentionPeriodStreamName;
    public Integer increaseRetentionPeriodHours;

    // arguments passed to the last putRecords call
    public volatile String putRecordsStreamName;
    public volatile List<PutRecordsRequestEntry> putRecordsSourceRecords = new ArrayList<PutRecordsRequestEntry>();


    /**
     *  Base constructor.
     */
    public KinesisClientMock()
    {
        // nothing here
    }


    /**
     *  Constructs an instance with a known stream.
     */
    public KinesisClientMock(String streamName)
    {
        knownStreams.add(streamName);
    }

//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    public KinesisClient createClient()
    {
        return (KinesisClient)Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] { KinesisClient.class },
                                    KinesisClientMock.this);
    }

//----------------------------------------------------------------------------
//  Invocation Handler
//----------------------------------------------------------------------------

    /**
     *  The invocation handler; test code should not care about this.
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        String methodName = method.getName();
        if (methodName.equals("describeStreamSummary"))
        {
            describeStreamSummaryInvocationCount++;
            DescribeStreamSummaryRequest request = (DescribeStreamSummaryRequest)args[0];
            describeStreamStreamName = request.streamName();
            return describeStreamSummary(request);
        }
        else if (methodName.equals("createStream"))
        {
            createStreamInvocationCount++;
            CreateStreamRequest request = (CreateStreamRequest)args[0];
            createStreamStreamName = request.streamName();
            createStreamShardCount = request.shardCount();
            return createStream(request);
        }
        else if (methodName.equals("increaseStreamRetentionPeriod"))
        {
            increaseRetentionPeriodInvocationCount++;
            IncreaseStreamRetentionPeriodRequest request = (IncreaseStreamRetentionPeriodRequest)args[0];
            increaseRetentionPeriodStreamName = request.streamName();
            increaseRetentionPeriodHours = request.retentionPeriodHours();
            return increaseStreamRetentionPeriod(request);
        }
        else if (methodName.equals("putRecords"))
        {
            putRecordsInvocationCount++;
            PutRecordsRequest request = (PutRecordsRequest)args[0];
            putRecordsStreamName    = request.streamName();
            putRecordsSourceRecords = request.records();
            return putRecords(request);
        }
        else if (methodName.equals("close"))
        {
            closeInvocationCount++;
            return null;
        }

        // if nothing matches, fall through to here
        System.err.println("invocation handler called unexpectedly: " + methodName);
        throw new IllegalArgumentException("unexpected client call: " + methodName);
    }

//----------------------------------------------------------------------------
//  Default mock implementations -- override for specific tests
//----------------------------------------------------------------------------

    protected DescribeStreamSummaryResponse describeStreamSummary(DescribeStreamSummaryRequest request)
    {
        String streamName = request.streamName();
        if (knownStreams.contains(streamName))
        {
            StreamDescriptionSummary desc = StreamDescriptionSummary.builder()
                                           .streamStatus(StreamStatus.ACTIVE)
                                           .build();
            return DescribeStreamSummaryResponse.builder().streamDescriptionSummary(desc).build();
        }
        else
        {
            throw ResourceNotFoundException.builder().message("stream not found: " + streamName).build();
        }
    }


    protected CreateStreamResponse createStream(CreateStreamRequest request)
    {
        knownStreams.add(request.streamName());
        return CreateStreamResponse.builder().build();
    }


    protected IncreaseStreamRetentionPeriodResponse increaseStreamRetentionPeriod(IncreaseStreamRetentionPeriodRequest request)
    {
        return IncreaseStreamRetentionPeriodResponse.builder().build();
    }


    protected PutRecordsResponse putRecords(PutRecordsRequest request)
    {
        List<PutRecordsResultEntry> resultRecords = new ArrayList<>(request.records().size());
        int errorCount = 0;

        // we use index rather than iterator so that we can take different actions based on it
        for (int ii = 0 ; ii < request.records().size() ; ii++)
        {
            PutRecordsResultEntry rec = processRequestEntry(ii, request.records().get(ii));
            errorCount += StringUtil.isEmpty(rec.errorCode()) ? 0 : 1;
            resultRecords.add(rec);
        }

        return PutRecordsResponse.builder()
               .failedRecordCount(Integer.valueOf(errorCount))
               .records(resultRecords)
               .build();
    }

//----------------------------------------------------------------------------
//  Supporting methods that can also be overridden
//----------------------------------------------------------------------------

    protected PutRecordsResultEntry processRequestEntry(int index, PutRecordsRequestEntry entry)
    {
        return PutRecordsResultEntry.builder().sequenceNumber(String.valueOf(index)).build();
    }
}
