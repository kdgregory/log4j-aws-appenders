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
import java.util.Collections;
import java.util.List;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;


/**
 *  Supports mock-object testing of the CloudWatch facade.
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
 *  This specific mock can be constructed with a list of "known" log groups and
 *  streams, and simulates enough of the "describe" API to return appropriate
 *  responses. It also provides the ability to set a batch size for these methods,
 *  to test pagination in the consumer.
 */
public class CloudWatchClientMock
implements InvocationHandler
{
    // the actual list of names used by this instance
    public List<String> logGroupNames;
    public List<String> logStreamNames;

    // the maximum number of names that will be returned in a single describe call
    private int maxLogGroupNamesInBatch;
    private int maxLogStreamNamesInBatch;

    // invocation counts for each function that we support
    public int describeLogGroupsInvocationCount;
    public int describeLogStreamsInvocationCount;
    public int createLogGroupInvocationCount;
    public int createLogStreamInvocationCount;
    public int putLogEventsInvocationCount;
    public int putRetentionPolicyInvocationCount;
    public int shutdownInvocationCount;

    // the name passed to the last describeLogGroups request
    public String describeLogGroupsGroupNamePrefix;

    // the name passed to the last createLogGroup request
    public String createLogGroupGroupName;

    // the names passed to the last describeLogStreams request
    public String describeLogStreamsGroupName;
    public String describeLogStreamsStreamPrefix;

    // the names passed to the last createLogStream request
    public String createLogStreamGroupName;
    public String createLogStreamStreamName;

    // the names passed to the last putLogEvents request
    public String putLogEventsGroupName;
    public String putLogEventsStreamName;
    public List<InputLogEvent> putLogEventsEvents;

    // the last arguments passed to putRetentionPolicy
    public String putRetentionPolicyGroupName;
    public Integer putRetentionPolicyValue;


    /**
     *  Constructs an instance with no known groups/streams.
     */
    public CloudWatchClientMock()
    {
        this(Collections.emptyList(), Collections.emptyList());
    }


    /**
     *  Constructs an instance using the specified lists of names, with no batch limit.
     */
    public CloudWatchClientMock(List<String> groupNames, List<String> streamNames)
    {
        this(groupNames, Integer.MAX_VALUE, streamNames, Integer.MAX_VALUE);
    }


    /**
     *  Constructs an instance using the specified lists of names and limits to the number
     *  of names that will be returned in a batch.
     */
    public CloudWatchClientMock(List<String> groupNames, int groupBatchSize, List<String> streamNames, int streamBatchSize)
    {
        logGroupNames = new ArrayList<String>(groupNames);
        maxLogGroupNamesInBatch = groupBatchSize;
        logStreamNames = new ArrayList<String>(streamNames);
        maxLogStreamNamesInBatch = streamBatchSize;
    }

//----------------------------------------------------------------------------
//  Public API
//----------------------------------------------------------------------------

    public AWSLogs createClient()
    {
        return (AWSLogs)Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] { AWSLogs.class },
                            CloudWatchClientMock.this);
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
        if (methodName.equals("describeLogGroups"))
        {
            describeLogGroupsInvocationCount++;
            DescribeLogGroupsRequest request = (DescribeLogGroupsRequest)args[0];
            describeLogGroupsGroupNamePrefix = request.getLogGroupNamePrefix();
            return describeLogGroups(request);
        }
        else if (methodName.equals("describeLogStreams"))
        {
            describeLogStreamsInvocationCount++;
            DescribeLogStreamsRequest request = (DescribeLogStreamsRequest)args[0];
            describeLogStreamsGroupName = request.getLogGroupName();
            describeLogStreamsStreamPrefix = request.getLogStreamNamePrefix();
            return describeLogStreams(request);
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
        else if (methodName.equals("putRetentionPolicy"))
        {
            putRetentionPolicyInvocationCount++;
            PutRetentionPolicyRequest request = (PutRetentionPolicyRequest)args[0];
            putRetentionPolicyGroupName = request.getLogGroupName();
            putRetentionPolicyValue = request.getRetentionInDays();
            return putRetentionPolicy(request);

        }
        else if (methodName.equals("putLogEvents"))
        {
            putLogEventsInvocationCount++;
            PutLogEventsRequest request = (PutLogEventsRequest)args[0];
            putLogEventsGroupName = request.getLogGroupName();
            putLogEventsStreamName = request.getLogStreamName();
            putLogEventsEvents = request.getLogEvents();
            return putLogEvents(request);
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
            {
                logGroups.add(new LogGroup()
                              .withLogGroupName(name)
                              .withArn("arn:aws:logs:us-east-1:123456789012:log-group:" + name));
            }
        }

        String nextToken = (max == logGroupNames.size()) ? null : String.valueOf(max);

        return new DescribeLogGroupsResult()
               .withLogGroups(logGroups)
               .withNextToken(nextToken);
    }


    protected DescribeLogStreamsResult describeLogStreams(DescribeLogStreamsRequest request)
    {
        if (! logGroupNames.contains(request.getLogGroupName()))
            throw new ResourceNotFoundException("no such log group: " + request.getLogGroupName());

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
                               .withArn("arn:aws:logs:us-east-1:123456789012:log-group:" + request.getLogGroupName() + ":log-stream:" + name)
                               );
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


    // default implementation is successful, does nothing (invocation handler has recorded args)
    protected PutRetentionPolicyResult putRetentionPolicy(PutRetentionPolicyRequest request)
    {
        switch (request.getRetentionInDays().intValue())
        {
            // copied separately from API docs
            case 1:
            case 3:
            case 5:
            case 7:
            case 14:
            case 30:
            case 60:
            case 90:
            case 120:
            case 150:
            case 180:
            case 365:
            case 400:
            case 545:
            case 731:
            case 1827:
            case 3653:
                return new PutRetentionPolicyResult();
            default:
                throw new InvalidParameterException("invalid retention period: " + request.getRetentionInDays());
        }
    }


    protected PutLogEventsResult putLogEvents(PutLogEventsRequest request)
    {
        if (! logGroupNames.contains(request.getLogGroupName()))
            throw new ResourceNotFoundException("no such log group: " + request.getLogGroupName());
        if (! logStreamNames.contains(request.getLogStreamName()))
            throw new ResourceNotFoundException("no such log stream: " + request.getLogStreamName());

        return new PutLogEventsResult();
    }
}
