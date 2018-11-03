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

package com.kdgregory.logging.testhelpers.cloudwatch;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.factories.ClientFactory;
import com.kdgregory.logging.common.factories.WriterFactory;
import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  A proxy-based mock for the CloudWatch client that allows deep testing of
 *  writer behavior. I don't particularly like using mock objects with this
 *  level of  complexity, but they're the only way to experiement with error
 *  conditions.
 *  <p>
 *  Construct with a list of loggroups and logstreams; there's no pairing of
 *  groups and streams. There's a default constructor that uses predefined lists,
 *  and a more complex constructor that breaks the list into fixed-sized chunks
 *  to verify multiple calls to the "describe" functions. Adding a group or stream
 *  will update the instance lists, which are available to the test code.
 *  <p>
 *  Each method invocation is counted, whether or not it succeeds. Only those
 *  methods that are used by the writer are implemented; everything else throws.
 *  Tests can override the method to change default behavior (for example, to
 *  throw or return an empty list).
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
    // the default list of names
    public static List<String> NAMES = Arrays.asList("foo", "bar", "barglet", "arglet",
                                                     "baz", "bargle", "argle", "fribble");

    // the actual list of names used by this instance
    public List<String> logGroupNames;
    public List<String> logStreamNames;

    // the maximum number of names that will be returned in a single describe call
    private int maxLogGroupNamesInBatch;
    private int maxLogStreamNamesInBatch;

    // the sequence token used for putLogEvents(); start with arbitrary value to
    // verify that we're actually retrieving it from describe
    protected int putLogEventsSequenceToken = (int)(System.currentTimeMillis() % 143);

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
    public volatile int shutdownInvocationCount;

    // the name passed to the last createLogGroup request
    public volatile String createLogGroupGroupName;

    // the names passed to the last createLogStream request
    public volatile String createLogStreamGroupName;
    public volatile String createLogStreamStreamName;

    // the list of events passed to the most recent putLogEvents call
    public volatile List<InputLogEvent> mostRecentEvents = new ArrayList<InputLogEvent>();


    /**
     *  Constructs an instance using the default name list.
     */
    public MockCloudWatchClient()
    {
        this(NAMES, NAMES);
    }


    /**
     *  Constructs an instance using the specified lists of names, with no batch limit.
     */
    public MockCloudWatchClient(List<String> groupNames, List<String> streamNames)
    {
        this(groupNames, Integer.MAX_VALUE, streamNames, Integer.MAX_VALUE);
    }


    /**
     *  Constructs an instance using the specified lists of names and limits to the number
     *  of names that will be returned in a batch.
     */
    public MockCloudWatchClient(List<String> groupNames, int groupBatchSize, List<String> streamNames, int streamBatchSize)
    {
        logGroupNames = new ArrayList<String>(groupNames);
        maxLogGroupNamesInBatch = groupBatchSize;
        logStreamNames = new ArrayList<String>(streamNames);
        maxLogStreamNamesInBatch = streamBatchSize;
    }

//----------------------------------------------------------------------------
//  Public API
//----------------------------------------------------------------------------

    /**
     *  Creates the client proxy. This is used internally, and also by the test
     *  for calling a static factory method.
     */
    public AWSLogs createClient()
    {
        return (AWSLogs)Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] { AWSLogs.class },
                            MockCloudWatchClient.this);
    }


    /**
     *  Creates a new WriterFactory, with the stock CloudWatch writer.
     */
    public WriterFactory<CloudWatchWriterConfig,CloudWatchWriterStatistics> newWriterFactory()
    {
        return new WriterFactory<CloudWatchWriterConfig,CloudWatchWriterStatistics>()
        {
            @Override
            public LogWriter newLogWriter(CloudWatchWriterConfig config, CloudWatchWriterStatistics stats, InternalLogger internalLogger)
            {
                return new CloudWatchLogWriter(config, stats, internalLogger, new ClientFactory<AWSLogs>()
                {
                    @Override
                    public AWSLogs createClient()
                    {
                        return MockCloudWatchClient.this.createClient();
                    }
                });
            }
        };
    }


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
//  Subclasses can override these
//----------------------------------------------------------------------------

    protected DescribeLogGroupsResult describeLogGroups(DescribeLogGroupsRequest request)
    {
        int offset = 0;
        if (request.getNextToken() != null)
            offset = Integer.parseInt(request.getNextToken());

        int max = Math.min(logGroupNames.size(), offset + maxLogGroupNamesInBatch);

        String namePrefix = request.getLogGroupNamePrefix();
        List<LogGroup> logGroups = new ArrayList<LogGroup>();
        for (String name : logGroupNames.subList(offset, max))
        {
            if ((namePrefix == null) || name.startsWith(namePrefix))
                logGroups.add(new LogGroup().withLogGroupName(name));
        }

        String nextToken = (max == logGroupNames.size()) ? null : String.valueOf(max);

        return new DescribeLogGroupsResult()
               .withLogGroups(logGroups)
               .withNextToken(nextToken);
    }


    protected DescribeLogStreamsResult describeLogStreams(DescribeLogStreamsRequest request)
    {
        int offset = 0;
        if (request.getNextToken() != null)
            offset = Integer.parseInt(request.getNextToken());

        int max = Math.min(logStreamNames.size(), offset + maxLogStreamNamesInBatch);

        String namePrefix = request.getLogStreamNamePrefix();
        List<LogStream> logStreams = new ArrayList<LogStream>();
        for (String name : logStreamNames.subList(offset, max))
        {
            if ((namePrefix == null) || name.startsWith(namePrefix))
                logStreams.add(new LogStream()
                               .withLogStreamName(name)
                               .withUploadSequenceToken(String.valueOf(putLogEventsSequenceToken)));
        }

        String nextToken = (max == logStreamNames.size()) ? null : String.valueOf(max);

        return new DescribeLogStreamsResult()
                   .withLogStreams(logStreams)
                   .withNextToken(nextToken);
    }


    // default implementation is successful, adds group name to those returned by describe
    protected CreateLogGroupResult createLogGroup(CreateLogGroupRequest request)
    {
        logGroupNames.add(request.getLogGroupName());
        return new CreateLogGroupResult();
    }


    // default implementation is successful, adds stream name to those returned by describe
    protected CreateLogStreamResult createLogStream(CreateLogStreamRequest request)
    {
        logStreamNames.add(request.getLogStreamName());
        return new CreateLogStreamResult();
    }


    protected PutLogEventsResult putLogEvents(PutLogEventsRequest request)
    {
        return new PutLogEventsResult()
               .withNextSequenceToken(String.valueOf(++putLogEventsSequenceToken));
    }

}
