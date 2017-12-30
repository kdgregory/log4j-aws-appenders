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

package com.kdgregory.log4j.testhelpers.aws.cloudwatch;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;

import net.sf.kdgcommons.lang.StringUtil;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;


/**
 *  A proxy-based mock for the CloudWatch client that allows deep testing of
 *  writer behavior. I don't particularly like using mock objects with this
 *  level of  complexity, but they're the only way to experiement with error
 *  conditions.
 *  <p>
 *  The basic implementation knows a list of names that are used for both
 *  logstreams and loggroups; both of the "describe" operations return this
 *  list, split into two parts. It also supports creating groups and streams,
 *  and the new names will be added to the lists returned by describe. Lastly,
 *  it supports putLogEvents, always returning success unless it's passed an
 *  invalid sequence token. If you need additional functionality (such as
 *  throwing from within any call), override the appropriate protected client
 *  method(s).
 *  <p>
 *  The tests that use this writer will have a background thread running, so will
 *  to coordinate behaviors between the main thread and writer thread. There are
 *  semaphores to control interaction with message publication: call {@link
 *  #allowWriterThread} after logging a message to wait for that message to be
 *  passed to putRecords.
 *  <p>
 *  To use this writer you'll also need to install a factory into the appender;
 *  {@link #newWriterFactory} will create it for you..
 */
public class MockCloudWatchClient
implements InvocationHandler
{
    // this token is used for describeLogGroups and describeLogStreams to
    // indicate the presence of or request for a second batch
    private final static String DESCRIBE_2ND_REQUEST_TOKEN = "qwertyuiop";

    // default lists of groups for describeLogGroups and describeLogStreams
    // note that the names that we use for testing are in the second batch;
    // this lets us verify that we retrieve all names
    protected List<String> describe1 = new ArrayList<String>(Arrays.asList("foo", "bar", "barglet", "arglet"));
    protected List<String> describe2 = new ArrayList<String>(Arrays.asList("baz", "bargle", "argle", "fribble"));

    // the sequence token used for putLogEvents(); start with non-default value
    protected int putLogEventsSequenceToken = 17;

    // these semaphores coordinate the calls to PutLogEvents with the assertions
    // that we make in the main thread; note that both start unacquired
    private Semaphore allowMainThread = new Semaphore(0);
    private Semaphore allowWriterThread = new Semaphore(0);

    // invocation counts for each function that we support
    public volatile int describeLogGroupsInvocationCount;
    public volatile int describeLogStreamsInvocationCount;
    public volatile int createLogGroupInvocationCount;
    public volatile int createLogStreamInvocationCount;
    public volatile int putLogEventsInvocationCount;

    // the name passed to the last createLogGroup request
    public volatile String createLogGroupGroupName;

    // the names passed to the last createLogStream request
    public volatile String createLogStreamGroupName;
    public volatile String createLogStreamStreamName;

    // the list of events passed to the most recent putLogEvents call
    public volatile List<InputLogEvent> mostRecentEvents = new ArrayList<InputLogEvent>();


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
                                    MockCloudWatchClient.this);
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
        String methodName = method.getName();
        if (methodName.equals("describeLogGroups"))
        {
            describeLogGroupsInvocationCount++;
            return describeLogGroups((DescribeLogGroupsRequest)args[0]);
        }
        else if (methodName.equals("describeLogStreams"))
        {
            describeLogStreamsInvocationCount++;
            return describeLogStreams((DescribeLogStreamsRequest)args[0]);
        }
        else if (methodName.equals("createLogGroup"))
        {
            createLogGroupInvocationCount++;
            CreateLogGroupRequest request = (CreateLogGroupRequest)args[0];
            createLogGroupGroupName = request.getLogGroupName();
            return createLogGroup(request);
        }
        else if (methodName.equals("createLogStream"))
        {
            createLogStreamInvocationCount++;
            CreateLogStreamRequest request = (CreateLogStreamRequest)args[0];
            createLogStreamGroupName = request.getLogGroupName();
            createLogStreamStreamName = request.getLogStreamName();
            return createLogStream(request);
        }
        else if (methodName.equals("putLogEvents"))
        {
            putLogEventsInvocationCount++;
            try
            {
                allowWriterThread.acquire();
                PutLogEventsRequest request = (PutLogEventsRequest)args[0];
                if (Integer.parseInt(request.getSequenceToken()) != putLogEventsSequenceToken)
                {
                    System.err.println("putLogEvents called with invalid sequence token: " + request.getSequenceToken());
                    throw new IllegalArgumentException("putLogEvents called with invalid sequence token: " + request.getSequenceToken());
                }
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
            System.err.println("invocation handler called unexpectedly: " + methodName);
            throw new IllegalArgumentException("unexpected client call: " + methodName);
        }
    }

//----------------------------------------------------------------------------
//  Subclasses can override these
//----------------------------------------------------------------------------

     // Default implementation returns predefined groups in two batches.
    protected DescribeLogGroupsResult describeLogGroups(DescribeLogGroupsRequest request)
    {
        List<String> names = (request.getNextToken() == null)
                           ? filterNames(describe1, request.getLogGroupNamePrefix())
                           : filterNames(describe2, request.getLogGroupNamePrefix());

        List<LogGroup> logGroups = new ArrayList<LogGroup>();
        for (String name : names)
        {
            logGroups.add(new LogGroup().withLogGroupName(name));
        }

        // for testing we always return two batches
        String nextToken = (request.getNextToken() == null)
                         ? DESCRIBE_2ND_REQUEST_TOKEN
                         : null;

        return new DescribeLogGroupsResult()
               .withLogGroups(logGroups)
               .withNextToken(nextToken);
    }


    // Default implementation returns predefined streams in two batches.
    protected DescribeLogStreamsResult describeLogStreams(DescribeLogStreamsRequest request)
    {
        List<String> names = (request.getNextToken() == null)
                           ? filterNames(describe1, request.getLogStreamNamePrefix())
                           : filterNames(describe2, request.getLogStreamNamePrefix());

        List<LogStream> logStreams = new ArrayList<LogStream>();
        for (String name : names)
        {
            LogStream stream = new LogStream()
                               .withLogStreamName(name)
                               .withUploadSequenceToken(String.valueOf(putLogEventsSequenceToken));
            logStreams.add(stream);
        }

        // for testing we always return two batches
        String nextToken = (request.getNextToken() == null)
                         ? DESCRIBE_2ND_REQUEST_TOKEN
                         : null;

        return new DescribeLogStreamsResult()
                   .withLogStreams(logStreams)
                   .withNextToken(nextToken);
    }


    // default implementation is successful, adds group name to those returned by describe
    protected CreateLogGroupResult createLogGroup(CreateLogGroupRequest request)
    {
        describe2.add(request.getLogGroupName());
        return new CreateLogGroupResult();
    }


    // default implementation is successful, adds stream name to those returned by describe
    protected CreateLogStreamResult createLogStream(CreateLogStreamRequest request)
    {
        describe2.add(request.getLogStreamName());
        return new CreateLogStreamResult();
    }


    protected PutLogEventsResult putLogEvents(PutLogEventsRequest request)
    {
        return new PutLogEventsResult()
               .withNextSequenceToken(String.valueOf(++putLogEventsSequenceToken));
    }


//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    /**
     *  Filters a list of names to find those that start with a given value.
     */
    private List<String> filterNames(List<String> source, String startsWith)
    {
        if (StringUtil.isEmpty(startsWith))
            return source;

        List<String> result = new ArrayList<String>();
        for (String value : source)
        {
            if (value.startsWith(startsWith))
                result.add(value);
        }
        return result;
    }
}
