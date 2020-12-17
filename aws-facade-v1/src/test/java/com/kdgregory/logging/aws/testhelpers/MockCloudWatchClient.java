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

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;


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
 */
public class MockCloudWatchClient
implements InvocationHandler
{
    // the actual list of names used by this instance
    public List<String> logGroupNames;
    public List<String> logStreamNames;

    // the maximum number of names that will be returned in a single describe call
    private int maxLogGroupNamesInBatch;
    private int maxLogStreamNamesInBatch;

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

    public AWSLogs createClient()
    {
        return (AWSLogs)Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] { AWSLogs.class },
                            MockCloudWatchClient.this);
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
            if (! logGroupNames.contains(request.getLogGroupName()))
                throw new ResourceNotFoundException("no such log group: " + request.getLogGroupName());
            if (! logStreamNames.contains(request.getLogStreamName()))
                throw new ResourceNotFoundException("no such log stream: " + request.getLogStreamName());
            if (Integer.parseInt(request.getSequenceToken()) != nextSequenceToken)
                throw new InvalidSequenceTokenException("was " + request.getSequenceToken() + " expected " + nextSequenceToken);
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
                               .withUploadSequenceToken(String.valueOf(nextSequenceToken)));
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
        return new PutLogEventsResult()
               .withNextSequenceToken(String.valueOf(++nextSequenceToken));
    }
}
