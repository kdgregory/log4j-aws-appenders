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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.*;


/**
 *  A proxy-based mock instance that allows unit testing of facade behavior.
 *  <p>
 *  All invocation handlers are exposed so that they can be overridden to throw exceptions.
 *  <p>
 *  Invocation counts are incremented when the invocation handler is called, regardless of
 *  the result. If you need to track whether the invocation handler succeeded, you should
 *  decrement the count inside the failing handler.
 */
public class MockSNSClient implements InvocationHandler
{
    // this prefix transforms a topic name into an ARN
    public final static String ARN_PREFIX = "arn:aws:sns:us-east-1:123456789012:";

    // all configured topics -- used for listing and also lookup before publish
    private List<Topic> allTopics = new ArrayList<>();
    private Set<String> allTopicsLookup = new HashSet<>();

    // the maximum number of topics that will be returned by a single describe
    private int maxTopicsPerDescribe;

    // the following record invocations and are exposed for testing
    public volatile int listTopicsInvocationCount;
    public volatile int createTopicInvocationCount;
    public volatile int publishInvocationCount;
    public volatile int shutdownInvocationCount;

    // after this are the invocation arguments for the various methods

    public volatile String createTopicName;

    public volatile String publishArn;
    public volatile String publishSubject;
    public volatile String publishMessage;


    /**
     *  Base constructor.
     */
    public MockSNSClient(List<String> knownTopicNames, int maxTopicsPerDescribe)
    {
        this.maxTopicsPerDescribe = maxTopicsPerDescribe;

        for (String name : knownTopicNames)
        {
            String arn = ARN_PREFIX + name;
            allTopics.add(new Topic().withTopicArn(arn));
            allTopicsLookup.add(arn);
        }
    }


    /**
     *  Convenience constructor, which returns all topics in a single list operation.
     */
    public MockSNSClient(List<String> allTopicNames)
    {
        this(allTopicNames, Integer.MAX_VALUE);
    }

//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    public AmazonSNS createClient()
    {
        return (AmazonSNS)Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] { AmazonSNS.class },
                                    MockSNSClient.this);
    }

//----------------------------------------------------------------------------
//  Invocation Handler
//----------------------------------------------------------------------------

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        String methodName = method.getName();
        switch (methodName)
        {
            case "listTopics":
                listTopicsInvocationCount++;
                return listTopics((ListTopicsRequest)args[0]);
            case "createTopic":
                createTopicInvocationCount++;
                createTopicName = (args[0] instanceof CreateTopicRequest)
                                ? ((CreateTopicRequest)args[0]).getName()
                                : (String)args[0];
                return createTopic(createTopicName);
            case "publish":
                publishInvocationCount++;
                PublishRequest request = (PublishRequest)args[0];
                publishArn     = request.getTopicArn();
                publishSubject = request.getSubject();
                publishMessage = request.getMessage();
                return publish(request);
            case "shutdown":
                shutdownInvocationCount++;
                return null;
            default:
                System.err.println("invocation handler called unexpectedly: " + methodName);
                throw new IllegalStateException("unexpected method called: " + methodName);
        }
    }

//----------------------------------------------------------------------------
//  Default mock implementations -- override for specific tests
//----------------------------------------------------------------------------

    /**
     *  Invocation handler for ListTopics. This returns the appropriate segment
     *  of the list passed to the constructor.
     */
    protected ListTopicsResult listTopics(ListTopicsRequest request)
    {
        int startOfSublist = (request.getNextToken() == null)
                           ? 0
                           : Integer.parseInt(request.getNextToken());
        int endOfSublist   = Math.min(startOfSublist + maxTopicsPerDescribe, allTopics.size());
        String nextToken   = endOfSublist == allTopics.size()
                           ? null
                           : String.valueOf(endOfSublist);

        ListTopicsResult response = new ListTopicsResult();
        response.setTopics(allTopics.subList(startOfSublist, endOfSublist));
        response.setNextToken(nextToken);
        return response;
    }


    /**
     *  Invocation handler for CreateTopic. Returns an ARN based on the name passed
     *  to the constructor.
     */
    protected CreateTopicResult createTopic(String topicName)
    {
        return new CreateTopicResult().withTopicArn(ARN_PREFIX + topicName);
    }


    /**
     *  Invocation handler for Publish. The default implementation waits until the
     *  main thread releases the writer, then calls publish0() to return a success
     *  message. Override this method if you don't want thread coordination,
     *  publish0() if you want to change the result.
     */
    protected PublishResult publish(PublishRequest request)
    {
        if (! allTopicsLookup.contains(request.getTopicArn()))
        {
            throw new NotFoundException("topic not found");
        }

        return new PublishResult().withMessageId(UUID.randomUUID().toString());
    }
}
