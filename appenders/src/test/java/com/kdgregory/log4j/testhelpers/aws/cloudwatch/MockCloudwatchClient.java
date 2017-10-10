// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.testhelpers.aws.cloudwatch;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.DescribeLogGroupsResult;
import com.amazonaws.services.logs.model.DescribeLogStreamsResult;
import com.amazonaws.services.logs.model.InputLogEvent;
import com.amazonaws.services.logs.model.LogGroup;
import com.amazonaws.services.logs.model.LogStream;
import com.amazonaws.services.logs.model.PutLogEventsRequest;
import com.amazonaws.services.logs.model.PutLogEventsResult;

import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;


/**
 *  A proxy-based mock for the CloudWatch client that allows deep testing of
 *  writer behavior. To use, override {@link #putLogEvents} to implement your
 *  own behavior, and call {@link #newWriterFactory} to create a factory that
 *  you attach to the appender. This class also provides semaphores that
 *  control sequencing of the test thread and writer thread; see the function
 *  {@link allowWriterThread}.
 */
public abstract class MockCloudwatchClient
implements InvocationHandler
{
    // these semaphores coordinate the calls to PutLogEvents with the assertions
    // that we make in the main thread; note that both start unacquired
    private Semaphore allowMainThread = new Semaphore(0);
    private Semaphore allowWriterThread = new Semaphore(0);

    /**
     *  The number of times that putLogEvents() was invoked
     */
    public int invocationCount;


    /**
     *  The log events passed to the most recent call
     */
    public List<InputLogEvent> mostRecentEvents = new ArrayList<InputLogEvent>();


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
     *  Creates a new WriterFactory, with the stock CloudWatch writer.
     */
    public WriterFactory<CloudWatchWriterConfig> newWriterFactory()
    {
        return new WriterFactory<CloudWatchWriterConfig>()
        {
            @Override
            public LogWriter newLogWriter(CloudWatchWriterConfig config)
            {
                return new CloudWatchLogWriter(config)
                {
                    @Override
                    protected void createAWSClient()
                    {
                        client = (AWSLogs)Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] { AWSLogs.class },
                                    MockCloudwatchClient.this);
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
        if (method.getName().equals("describeLogGroups"))
        {
            return new DescribeLogGroupsResult()
                   .withLogGroups(Arrays.asList(
                       new LogGroup().withLogGroupName("argle")));
        }
        else if (method.getName().equals("describeLogStreams"))
        {
            return new DescribeLogStreamsResult()
                   .withLogStreams(Arrays.asList(
                       new LogStream().withLogStreamName("bargle")
                                      .withUploadSequenceToken("anything")));
        }
        else if (method.getName().equals("putLogEvents"))
        {
            try
            {
                allowWriterThread.acquire();
                PutLogEventsRequest request = (PutLogEventsRequest)args[0];
                mostRecentEvents.clear();
                mostRecentEvents.addAll(request.getLogEvents());
                return putLogEvents(request);
            }
            finally
            {
                allowMainThread.release();
            }
        }
        else
        {
            System.err.println("invocation handler called unexpectedly: " + method.getName());
            return null;
        }
    }


    /**
     *  Override this to provide client-specific behavior.
     */
    protected abstract PutLogEventsResult putLogEvents(PutLogEventsRequest request);
}
