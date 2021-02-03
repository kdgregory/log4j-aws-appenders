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

import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;
import software.amazon.awssdk.services.cloudwatchlogs.paginators.*;


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

    // this is retained, because paginator requests will use it
    private CloudWatchLogsClient client;

    // the sequence token used for putLogEvents(); start with arbitrary value to
    // verify that we're actually retrieving it from describe
    public int nextSequenceToken = (int)(System.currentTimeMillis() % 143);

    // invocation counts for each function that we support
    public int describeLogGroupsInvocationCount;
    public int describeLogStreamsInvocationCount;
    public int createLogGroupInvocationCount;
    public int createLogStreamInvocationCount;
    public int putLogEventsInvocationCount;
    public int putRetentionPolicyInvocationCount;
    public int closeInvocationCount;

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
     *  Constructs an instance that doesn't know of any groups or streams.
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

    public CloudWatchLogsClient createClient()
    {
        if (client == null)
        {
            client = (CloudWatchLogsClient)Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] { CloudWatchLogsClient.class },
                            CloudWatchClientMock.this);
        }
        return client;
    }


    /**
     *  Retrieves the current sequence token, for testing PutLogEvents.
     */
    public String getCurrentSequenceToken()
    {
        return String.valueOf(nextSequenceToken);
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
        if (methodName.equals("describeLogGroupsPaginator"))
        {
            // this is a default method on the actual interface, but needs to be handled
            // explicitly by the proxy; I'm not going to track its calls, since it doesn't
            // actually hit the service

            return new DescribeLogGroupsIterable(client, (DescribeLogGroupsRequest)args[0]);
        }
        else if (methodName.equals("describeLogStreamsPaginator"))
        {
            // this is a default method on the actual interface, but needs to be handled
            // explicitly by the proxy; I'm not going to track its calls, since it doesn't
            // actually hit the service

            return new DescribeLogStreamsIterable(client, (DescribeLogStreamsRequest)args[0]);
        }
        else if (methodName.equals("describeLogGroups"))
        {
            describeLogGroupsInvocationCount++;
            DescribeLogGroupsRequest request = (DescribeLogGroupsRequest)args[0];
            describeLogGroupsGroupNamePrefix = request.logGroupNamePrefix();
            return describeLogGroups(request);
        }
        else if (methodName.equals("describeLogStreams"))
        {
            describeLogStreamsInvocationCount++;
            DescribeLogStreamsRequest request = (DescribeLogStreamsRequest)args[0];
            describeLogStreamsGroupName = request.logGroupName();
            describeLogStreamsStreamPrefix = request.logStreamNamePrefix();
            return describeLogStreams(request);
        }
        else if (methodName.equals("createLogGroup"))
        {
            createLogGroupInvocationCount++;
            CreateLogGroupRequest request = (CreateLogGroupRequest)args[0];
            createLogGroupGroupName = request.logGroupName();
            return createLogGroup(request);
        }
        else if (methodName.equals("createLogStream"))
        {
            createLogStreamInvocationCount++;
            CreateLogStreamRequest request = (CreateLogStreamRequest)args[0];
            createLogStreamGroupName = request.logGroupName();
            createLogStreamStreamName = request.logStreamName();
            return createLogStream(request);
        }
        else if (methodName.equals("putRetentionPolicy"))
        {
            putRetentionPolicyInvocationCount++;
            PutRetentionPolicyRequest request = (PutRetentionPolicyRequest)args[0];
            putRetentionPolicyGroupName = request.logGroupName();
            putRetentionPolicyValue = request.retentionInDays();
            return putRetentionPolicy(request);

        }
        else if (methodName.equals("putLogEvents"))
        {
            putLogEventsInvocationCount++;
            PutLogEventsRequest request = (PutLogEventsRequest)args[0];
            putLogEventsGroupName = request.logGroupName();
            putLogEventsStreamName = request.logStreamName();
            putLogEventsEvents = request.logEvents();
            return putLogEvents(request);
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

    protected DescribeLogGroupsResponse describeLogGroups(DescribeLogGroupsRequest request)
    {
        int offset = 0;
        if (request.nextToken() != null)
            offset = Integer.parseInt(request.nextToken());

        int max = Math.min(logGroupNames.size(), offset + maxLogGroupNamesInBatch);

        String namePrefix = request.logGroupNamePrefix();
        List<LogGroup> logGroups = new ArrayList<LogGroup>();
        for (String name : logGroupNames.subList(offset, max))
        {
            if ((namePrefix == null) || name.startsWith(namePrefix))
            {
                LogGroup group = LogGroup.builder()
                                 .logGroupName(name)
                                 .arn("arn:aws:logs:us-east-1:123456789012:log-group:" + name)
                                 .build();
                logGroups.add(group);
            }
        }

        String nextToken = (max == logGroupNames.size()) ? null : String.valueOf(max);

        return DescribeLogGroupsResponse.builder()
               .logGroups(logGroups)
               .nextToken(nextToken)
               .build();
    }


    protected DescribeLogStreamsResponse describeLogStreams(DescribeLogStreamsRequest request)
    {
        // no need to override for missing groups
        if (! logGroupNames.contains(request.logGroupName()))
            throw ResourceNotFoundException.builder().message("no such log group: " + request.logGroupName()).build();

        int offset = 0;
        if (request.nextToken() != null)
            offset = Integer.parseInt(request.nextToken());

        int max = Math.min(logStreamNames.size(), offset + maxLogStreamNamesInBatch);

        String namePrefix = request.logStreamNamePrefix();
        List<LogStream> logStreams = new ArrayList<LogStream>();
        for (String name : logStreamNames.subList(offset, max))
        {
            if ((namePrefix == null) || name.startsWith(namePrefix))
            {
                LogStream stream = LogStream.builder()
                                   .logStreamName(name)
                                   .uploadSequenceToken(String.valueOf(nextSequenceToken))
                                   .build();
                logStreams.add(stream);
            }
        }

        String nextToken = (max == logStreamNames.size()) ? null : String.valueOf(max);

        return DescribeLogStreamsResponse.builder()
               .logStreams(logStreams)
               .nextToken(nextToken)
               .build();
    }


    // default implementation is successful, adds group name to those returned by describe
    protected CreateLogGroupResponse createLogGroup(CreateLogGroupRequest request)
    {
        logGroupNames.add(request.logGroupName());
        return CreateLogGroupResponse.builder().build();
    }


    // default implementation is successful, adds stream name to those returned by describe
    protected CreateLogStreamResponse createLogStream(CreateLogStreamRequest request)
    {
        logStreamNames.add(request.logStreamName());
        return CreateLogStreamResponse.builder().build();
    }


    // default implementation is successful, does nothing (invocation handler has recorded args)
    protected PutRetentionPolicyResponse putRetentionPolicy(PutRetentionPolicyRequest request)
    {
        switch (request.retentionInDays().intValue())
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
                return PutRetentionPolicyResponse.builder().build();
            default:
                throw InvalidParameterException.builder()
                      .message("invalid retention period: " + request.retentionInDays())
                      .build();
        }
    }


    protected PutLogEventsResponse putLogEvents(PutLogEventsRequest request)
    {
        if (! logGroupNames.contains(request.logGroupName()))
        {
            throw ResourceNotFoundException.builder()
                  .message("no such log group: " + request.logGroupName())
                  .build();
        }
        if (! logStreamNames.contains(request.logStreamName()))
        {
            throw ResourceNotFoundException.builder()
                  .message("no such log stream: " + request.logStreamName())
                  .build();
        }
        if (Integer.parseInt(request.sequenceToken()) != nextSequenceToken)
        {
            throw InvalidSequenceTokenException.builder()
                  .message("was " + request.sequenceToken() + " expected " + nextSequenceToken)
                  .build();
        }

        return PutLogEventsResponse.builder()
               .nextSequenceToken(String.valueOf(++nextSequenceToken))
               .build();
    }
}
