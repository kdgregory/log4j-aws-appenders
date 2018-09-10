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

package com.kdgregory.log4j.testhelpers.aws.sns;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.*;

import com.kdgregory.log4j.aws.sns.SNSAppenderStatistics;
import com.kdgregory.log4j.aws.sns.SNSLogWriter;
import com.kdgregory.log4j.aws.sns.SNSWriterConfig;
import com.kdgregory.log4j.common.LogWriter;
import com.kdgregory.log4j.common.WriterFactory;


/**
 *  A proxy-based mock instance that allows unit testing of appender and writer behavior.
 *  <p>
 *  Constructed with the "topic under test", which is used to verify publish operations,
 *  and a list of all topics, which is used by ListTopics.
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

    // the name/arn of the topic under test
    private String topicName;
    private String topicArn;

    // the ARN of all known topics for ListTopics
    private List<Topic> allTopics;

    // the maximum number of topics that will be returned by a single describe
    private int maxTopicsPerDescribe;

    // these semaphores coordinate the calls to Publish with the assertions
    // that we make in the main thread; note that both start unacquired
    private Semaphore allowMainThread = new Semaphore(0);
    private Semaphore allowWriterThread = new Semaphore(0);

    // the following record invocations and are exposed for testing
    public volatile int listTopicsInvocationCount;
    public volatile int createTopicInvocationCount;
    public volatile int publishInvocationCount;

    public volatile String lastPublishArn;
    public volatile String lastPublishSubject;
    public volatile String lastPublishMessage;


    /**
     *  Base constructor.
     */
    public MockSNSClient(String topicName, List<String> allTopicNames, int maxTopicsPerDescribe)
    {
        this.topicName = topicName;
        this.topicArn = ARN_PREFIX + topicName;

        this.maxTopicsPerDescribe = maxTopicsPerDescribe;

        allTopics = new ArrayList<Topic>();
        for (String name : allTopicNames)
        {
            allTopics.add(new Topic().withTopicArn(ARN_PREFIX + name));
        }
    }


    /**
     *  Convenience constructor, which returns all topics in a single list operation.
     */
    public MockSNSClient(String topicName, List<String> allTopicNames)
    {
        this(topicName, allTopicNames, Integer.MAX_VALUE);
    }

//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    /**
     *  Creates a client proxy outside of the factory.
     */
    public AmazonSNS createClient()
    {
        return (AmazonSNS)Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] { AmazonSNS.class },
                                    MockSNSClient.this);
    }


    /**
     *  Returns a WriterFactory that includes our mock client.
     */
    public WriterFactory<SNSWriterConfig,SNSAppenderStatistics> newWriterFactory()
    {
        return new WriterFactory<SNSWriterConfig,SNSAppenderStatistics>()
        {
            @Override
            public LogWriter newLogWriter(SNSWriterConfig config, SNSAppenderStatistics stats)
            {
                return new SNSLogWriter(config, stats)
                {
                    @Override
                    protected void createAWSClient()
                    {
                        client = createClient();
                    }
                };
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


//----------------------------------------------------------------------------
//  Invocation Handler
//----------------------------------------------------------------------------

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        String methodName = method.getName();
        if (methodName.equals("listTopics"))
        {
            listTopicsInvocationCount++;
            return listTopics((ListTopicsRequest)args[0]);
        }
        else if ((methodName.equals("createTopic")) && (args[0] instanceof String))
        {
            createTopicInvocationCount++;
            return createTopic((String)args[0]);
        }
        else if (methodName.equals("publish"))
        {
            try
            {
                allowWriterThread.acquire();
                publishInvocationCount++;
                PublishRequest request = (PublishRequest)args[0];
                lastPublishArn     = request.getTopicArn();
                lastPublishSubject = request.getSubject();
                lastPublishMessage = request.getMessage();
                return publish(request);
            }
            catch (InterruptedException ex)
            {
                // this should never happen
                throw new RuntimeException("publish lock interrupted");
            }
            finally
            {
                allowMainThread.release();
            }
        }

        throw new IllegalStateException("unexpected method called: " + methodName);
    }


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
    protected CreateTopicResult createTopic(String name)
    {
        if (topicName.equals(name))
            return new CreateTopicResult().withTopicArn(topicArn);
        else
            throw new IllegalArgumentException("unexpected topic name: " + name);
    }


    /**
     *  Invocation handler for Publish. The default implementation waits until the
     *  main thread releases the writer, then calls publish0() to return a success
     *  message. Override this method if you don't want thread coordination,
     *  publish0() if you want to change the result.
     */
    protected PublishResult publish(PublishRequest request)
    {
        return new PublishResult().withMessageId(UUID.randomUUID().toString());
    }

}
