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

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.*;
import software.amazon.awssdk.services.sns.paginators.ListTopicsIterable;


/**
 *  Supports mock-object testing of the SNS facade.
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
 *  This mock can be constructed with a list of known topic names, which are used
 *  by the "describe" and "publish" operations. It also supports a batch size for
 *  the "describe" operation, and simulates the API's pagination behavior.
 */
public class SNSClientMock implements InvocationHandler
{
    // this prefix transforms a topic name into an ARN
    public final static String ARN_PREFIX = "arn:aws:sns:us-east-1:123456789012:";

    // all configured topics -- used for listing and also lookup before publish
    private List<Topic> allTopics = new ArrayList<>();
    private Set<String> allTopicsLookup = new HashSet<>();

    // the maximum number of topics that will be returned by a single describe
    private int maxTopicsPerDescribe;

    // ListTopicsIterable needs a client, so we'll hold onto it after creating
    private SnsClient cachedClient;

    // the following record invocations and are exposed for testing
    public volatile int listTopicsInvocationCount;
    public volatile int createTopicInvocationCount;
    public volatile int publishInvocationCount;
    public volatile int closeInvocationCount;

    // after this are the invocation arguments for the various methods

    public volatile String createTopicName;

    public volatile String publishArn;
    public volatile String publishSubject;
    public volatile String publishMessage;


    /**
     *  Base constructor.
     */
    public SNSClientMock(List<String> knownTopicNames, int maxTopicsPerDescribe)
    {
        this.maxTopicsPerDescribe = maxTopicsPerDescribe;

        for (String name : knownTopicNames)
        {
            String arn = ARN_PREFIX + name;
            Topic topic = Topic.builder().topicArn(arn).build();
            allTopics.add(topic);
            allTopicsLookup.add(arn);
        }
    }


    /**
     *  Convenience constructor, which returns all topics in a single list operation.
     */
    public SNSClientMock(List<String> allTopicNames)
    {
        this(allTopicNames, Integer.MAX_VALUE);
    }

//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    public SnsClient createClient()
    {
        cachedClient = (SnsClient)Proxy.newProxyInstance(
                                            getClass().getClassLoader(),
                                            new Class<?>[] { SnsClient.class },
                                            SNSClientMock.this);
        return cachedClient;
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
            case "listTopicsPaginator":
                // this is a helper object, not an API call, so I'll handle here
                return new ListTopicsIterable(cachedClient, ListTopicsRequest.builder().build());
            case "listTopics":
                listTopicsInvocationCount++;
                return listTopics((ListTopicsRequest)args[0]);
            case "createTopic":
                createTopicInvocationCount++;
                createTopicName = (args[0] instanceof CreateTopicRequest)
                                ? ((CreateTopicRequest)args[0]).name()
                                : (String)args[0];
                return createTopic(createTopicName);
            case "publish":
                publishInvocationCount++;
                PublishRequest request = (PublishRequest)args[0];
                publishArn     = request.topicArn();
                publishSubject = request.subject();
                publishMessage = request.message();
                return publish(request);
            case "close":
                closeInvocationCount++;
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
    protected ListTopicsResponse listTopics(ListTopicsRequest request)
    {
        int startOfSublist = (request.nextToken() == null)
                           ? 0
                           : Integer.parseInt(request.nextToken());
        int endOfSublist   = Math.min(startOfSublist + maxTopicsPerDescribe, allTopics.size());
        String nextToken   = endOfSublist == allTopics.size()
                           ? null
                           : String.valueOf(endOfSublist);

        return ListTopicsResponse.builder()
                                 .topics(allTopics.subList(startOfSublist, endOfSublist))
                                 .nextToken(nextToken)
                                 .build();
    }


    /**
     *  Invocation handler for CreateTopic. Returns an ARN based on the name passed
     *  to the constructor.
     */
    protected CreateTopicResponse createTopic(String topicName)
    {
        // we don't retrieve this topic in any tests, so no need to store it in list of known topics
        return CreateTopicResponse.builder().topicArn(ARN_PREFIX + topicName).build();
    }


    /**
     *  Invocation handler for Publish. The default implementation waits until the
     *  main thread releases the writer, then calls publish0() to return a success
     *  message. Override this method if you don't want thread coordination,
     *  publish0() if you want to change the result.
     */
    protected PublishResponse publish(PublishRequest request)
    {
        if (! allTopicsLookup.contains(request.topicArn()))
        {
            throw NotFoundException.builder().message("topic not found").build();
        }

        return PublishResponse.builder().messageId(UUID.randomUUID().toString()).build();
    }
}
