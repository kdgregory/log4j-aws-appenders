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

package com.kdgregory.logging.aws.testhelpers.kinesis;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.*;

import com.kdgregory.logging.aws.kinesis.KinesisWriterStatistics;
import com.kdgregory.logging.aws.kinesis.KinesisLogWriter;
import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.factories.ClientFactory;
import com.kdgregory.logging.common.factories.WriterFactory;
import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  A proxy-based mock for the Kinesis client that allows deep testing of writer
 *  behavior. I don't particularly like using mock objects with this level of
 *  complexity, but they're the only way to experiement with error conditions.
 *  <p>
 *  The basic implementation knows about a stream named "argle", will let you
 *  create a new stream with whatever name you choose, and will let you put
 *  records without error. If you need different behavior, override whichever
 *  of the protected "client" methods you need.
 *  <p>
 *  The tests that use this writer will have a background thread running, so will
 *  to coordinate behaviors between the main thread and writer thread. There are
 *  semaphores to control interaction with message publication: call {@link
 *  #allowWriterThread} after logging a message to wait for that message to be
 *  passed to putRecords.
 *  <p>
 *  To use this writer you'll also need to install a factory into the appender;
 *  {@link #newWriterFactory} will create it for you.
 */
public class MockKinesisClient
implements InvocationHandler
{
    // these semaphores coordinate the calls to PutLogEvents with the assertions
    // that we make in the main thread; note that both start unacquired
    private Semaphore allowMainThread = new Semaphore(0);
    private Semaphore allowWriterThread = new Semaphore(0);

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

    // the number of records that were consiidered successes and failures
    public volatile List<PutRecordsRequestEntry> putRecordsSuccesses = new ArrayList<PutRecordsRequestEntry>();
    public volatile List<PutRecordsRequestEntry> putRecordsFailures = new ArrayList<PutRecordsRequestEntry>();




    /**
     *  Constructs an instance that has a list of streams and will return them
     *  all in a single describe call.
     */
    public MockKinesisClient(String... streamNames)
    {
        knownStreams.addAll(Arrays.asList(streamNames));
    }


    /**
     *  Constructs an instance that does not know about any streams. The passed
     *  count controls how many times describeStream will return CREATING before
     *  it returns ACTIVE.
     */
    public MockKinesisClient(int creatingStatusCount)
    {
        this.creatingStatusCount = creatingStatusCount;
    }


//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    /**
     *  Pauses the main thread and allows the writer thread to proceed.
     */
    public void allowWriterThread() throws Exception
    {
        allowWriterThread.release();
        Thread.sleep(100);
        allowMainThread.acquire();
    }


    /**
     *  Creates a client proxy outside of the writer factory.
     */
    public AmazonKinesis createClient()
    {
        return (AmazonKinesis)Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] { AmazonKinesis.class },
                                    MockKinesisClient.this);
    }


    /**
     *  Returns a Kinesis WriterFactory that includes our mock client.
     */
    public WriterFactory<KinesisWriterConfig,KinesisWriterStatistics> newWriterFactory()
    {
        return new WriterFactory<KinesisWriterConfig,KinesisWriterStatistics>()
        {
            @Override
            public LogWriter newLogWriter(KinesisWriterConfig config, KinesisWriterStatistics stats, InternalLogger logger)
            {
                return new KinesisLogWriter(config, stats, logger, new ClientFactory<AmazonKinesis>()
                {
                    @Override
                    public AmazonKinesis createClient()
                    {
                        return MockKinesisClient.this.createClient();
                    }
                });
            }
        };
    }


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
            allowWriterThread.acquire();
            PutRecordsRequest request = (PutRecordsRequest)args[0];
            putRecordsStreamName    = request.getStreamName();
            putRecordsSourceRecords = request.getRecords();
            try
            {
                putRecordsSuccesses.clear();
                putRecordsFailures.clear();

                PutRecordsResult result = putRecords(request);
                for (int ii = 0 ; ii < result.getRecords().size() ; ii++)
                {
                    if (result.getRecords().get(ii).getErrorCode() != null)
                        putRecordsFailures.add(putRecordsSourceRecords.get(ii));
                    else
                        putRecordsSuccesses.add(putRecordsSourceRecords.get(ii));
                }
                return result;
            }
            finally
            {
                allowMainThread.release();
            }
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
//  Methods for subclasses to override
//----------------------------------------------------------------------------

    protected DescribeStreamResult describeStream(DescribeStreamRequest request)
    {
        StreamDescription streamDesc = new StreamDescription();

        if (! knownStreams.contains(request.getStreamName()))
        {
            throw new ResourceNotFoundException("");
        }
        else if (creatingStatusCount-- > 0)
        {
            streamDesc.setStreamStatus(StreamStatus.CREATING);
        }
        else
        {
            streamDesc.setStreamStatus(StreamStatus.ACTIVE);
        }

        return new DescribeStreamResult().withStreamDescription(streamDesc);
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
        for (int ii = 0 ; ii < request.getRecords().size() ; ii++)
        {
            PutRecordsResultEntry resultRecord = new PutRecordsResultEntry();
            // TODO - at the present time we're not verifying shard ID or sequence number
            resultRecords.add(resultRecord);
        }
        return new PutRecordsResult()
               .withFailedRecordCount(Integer.valueOf(0))
               .withRecords(resultRecords);
    }

}
