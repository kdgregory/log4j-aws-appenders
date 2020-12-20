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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.sf.kdgcommons.lang.StringUtil;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.*;
import com.amazonaws.util.BinaryUtils;


/**
 *  A proxy-based mock for the Kinesis client that allows deep testing of writer
 *  behavior. I don't particularly like using mock objects with this level of
 *  complexity, but they're the only way to experiment with error conditions.
 */
public class MockKinesisClient
implements InvocationHandler
{
    // the list of known streams, initialized by the constructor
    public List<String> knownStreams = new ArrayList<String>();

    // this provides a countdown for the CREATING status from describe;
    // by default all describes return ACTIVE if the stream exists
    public int creatingStatusCount;

    // the number of times each method was invoked
    public volatile int describeStreamInvocationCount;
    public volatile int createStreamInvocationCount;
    public volatile int putRecordsInvocationCount;
    public volatile int increaseRetentionPeriodInvocationCount;
    public volatile int shutdownInvocationCount;

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
    public MockKinesisClient()
    {
        // nothing here
    }


    /**
     *  Constructs an instance with a known stream.
     */
    public MockKinesisClient(String streamName)
    {
        knownStreams.add(streamName);
    }

//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    public AmazonKinesis createClient()
    {
        return (AmazonKinesis)Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] { AmazonKinesis.class },
                                    MockKinesisClient.this);
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
        if (methodName.equals("describeStream"))
        {
            describeStreamInvocationCount++;
            DescribeStreamRequest request = (DescribeStreamRequest)args[0];
            describeStreamStreamName = request.getStreamName();
            return describeStream(request);
        }
        else if (methodName.equals("createStream"))
        {
            createStreamInvocationCount++;
            CreateStreamRequest request = (CreateStreamRequest)args[0];
            createStreamStreamName = request.getStreamName();
            createStreamShardCount = request.getShardCount();
            return createStream(request);
        }
        else if (methodName.equals("increaseStreamRetentionPeriod"))
        {
            increaseRetentionPeriodInvocationCount++;
            IncreaseStreamRetentionPeriodRequest request = (IncreaseStreamRetentionPeriodRequest)args[0];
            increaseRetentionPeriodStreamName = request.getStreamName();
            increaseRetentionPeriodHours = request.getRetentionPeriodHours();
            return increaseStreamRetentionPeriod(request);
        }
        else if (methodName.equals("putRecords"))
        {
            putRecordsInvocationCount++;
            PutRecordsRequest request = (PutRecordsRequest)args[0];
            putRecordsStreamName    = request.getStreamName();
            putRecordsSourceRecords = request.getRecords();
            return putRecords(request);
        }
        else if (methodName.equals("shutdown"))
        {
            shutdownInvocationCount++;
            return null;
        }

        // if nothing matches, fall through to here
        System.err.println("invocation handler called unexpectedly: " + methodName);
        throw new IllegalArgumentException("unexpected client call: " + methodName);
    }

//----------------------------------------------------------------------------
//  Default mock implementations -- override for specific tests
//----------------------------------------------------------------------------

    protected DescribeStreamResult describeStream(DescribeStreamRequest request)
    {
        if (knownStreams.contains(request.getStreamName()))
        {
            StreamDescription streamDesc = new StreamDescription()
                                           .withStreamStatus(StreamStatus.ACTIVE);
            return new DescribeStreamResult().withStreamDescription(streamDesc);
        }
        else
        {
            throw new ResourceNotFoundException("stream not found: " + request.getStreamName());
        }
    }


    protected CreateStreamResult createStream(CreateStreamRequest request)
    {
        knownStreams.add(request.getStreamName());
        return new CreateStreamResult();
    }


    protected IncreaseStreamRetentionPeriodResult increaseStreamRetentionPeriod(IncreaseStreamRetentionPeriodRequest request)
    {
        return new IncreaseStreamRetentionPeriodResult();
    }


    protected PutRecordsResult putRecords(PutRecordsRequest request)
    {
        List<PutRecordsResultEntry> resultRecords = new ArrayList<PutRecordsResultEntry>(request.getRecords().size());
        int errorCount = 0;
        for (int ii = 0 ; ii < request.getRecords().size() ; ii++)
        {
            PutRecordsResultEntry rec = processRequestEntry(ii, request.getRecords().get(ii));
            errorCount += StringUtil.isEmpty(rec.getErrorCode()) ? 0 : 1;
            resultRecords.add(rec);
        }
        return new PutRecordsResult()
               .withFailedRecordCount(Integer.valueOf(errorCount))
               .withRecords(resultRecords);
    }


    protected PutRecordsResultEntry processRequestEntry(int index, PutRecordsRequestEntry entry)
    {
        return new PutRecordsResultEntry().withSequenceNumber(String.valueOf(index));
    }
}
