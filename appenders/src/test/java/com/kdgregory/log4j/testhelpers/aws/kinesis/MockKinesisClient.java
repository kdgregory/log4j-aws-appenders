// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.testhelpers.aws.kinesis;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.DescribeStreamResult;
import com.amazonaws.services.kinesis.model.PutRecordsRequest;
import com.amazonaws.services.kinesis.model.PutRecordsRequestEntry;
import com.amazonaws.services.kinesis.model.PutRecordsResult;
import com.amazonaws.services.kinesis.model.StreamDescription;
import com.amazonaws.services.kinesis.model.StreamStatus;

import com.kdgregory.log4j.aws.internal.kinesis.KinesisLogWriter;
import com.kdgregory.log4j.aws.internal.kinesis.KinesisWriterConfig;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;

/**
 *  A proxy-based mock for the Kinesis client that allows deep testing of
 *  writer behavior. To use, override {@link #putRecords} to implement your
 *  own behavior, and call {@link #newWriterFactory} to create a factory that
 *  you attach to the appender. This class also provides semaphores that
 *  control sequencing of the test thread and writer thread; see the function
 *  {@link allowWriterThread}.
 */
public abstract class MockKinesisClient
implements InvocationHandler
{
    // these semaphores coordinate the calls to PutLogEvents with the assertions
    // that we make in the main thread; note that both start unacquired
    private Semaphore allowMainThread = new Semaphore(0);
    private Semaphore allowWriterThread = new Semaphore(0);


    /**
     *  Number of times that putRecords() was invoked.
     */
    public int invocationCount;


    /**
     *  The complete list of records passed to the last putRecords call
     */
    public List<PutRecordsRequestEntry> providedRecords = new ArrayList<PutRecordsRequestEntry>();


    /**
     *  The list of records that were considered successes
     */
    public List<PutRecordsRequestEntry> successRecords = new ArrayList<PutRecordsRequestEntry>();

    /**
     *  The list of records that were considered failures
     */
    public List<PutRecordsRequestEntry> failedRecords = new ArrayList<PutRecordsRequestEntry>();


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
     *  Returns a Kinesis WriterFactory that includes our mock client.
     */
    public WriterFactory<KinesisWriterConfig> newWriterFactory()
    {
        return new WriterFactory<KinesisWriterConfig>()
        {
            @Override
            public LogWriter newLogWriter(KinesisWriterConfig config)
            {
                return new KinesisLogWriter(config)
                {
                    @Override
                    protected void createAWSClient()
                    {
                        client = (AmazonKinesis)Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] { AmazonKinesis.class },
                                    MockKinesisClient.this);
                    }
                };
            }
        };
    }


    /**
     *  The invocation handler; test code should not care about this.
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        if (method.getName().equals("describeStream"))
        {
            return new DescribeStreamResult()
                   .withStreamDescription(
                       new StreamDescription()
                       .withStreamStatus(StreamStatus.ACTIVE));
        }
        else if (method.getName().equals("putRecords"))
        {
            allowWriterThread.acquire();
            PutRecordsRequest request = (PutRecordsRequest)args[0];
            providedRecords = request.getRecords();
            try
            {
                successRecords.clear();
                failedRecords.clear();

                PutRecordsResult result = putRecords(request);
                for (int ii = 0 ; ii < result.getRecords().size() ; ii++)
                {
                    if (result.getRecords().get(ii).getErrorCode() != null)
                        failedRecords.add(providedRecords.get(ii));
                    else
                        successRecords.add(providedRecords.get(ii));
                }
                return result;
            }
            finally
            {
                allowMainThread.release();
            }
        }

        System.err.println("invocation handler called unexpectedly: " + method.getName());
        return null;
    }


    /**
     *  Subclasses override this to provide behavior.
     */
    public abstract PutRecordsResult putRecords(PutRecordsRequest request);

}
