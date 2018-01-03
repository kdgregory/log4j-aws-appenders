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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.*;

import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;
import com.kdgregory.log4j.aws.internal.sns.SNSLogWriter;
import com.kdgregory.log4j.aws.internal.sns.SNSWriterConfig;


/**
 *  A proxy-based mock instance that allows unit testing of appender and writer behavior.
 *  <p>
 *  You construct with two things: the name (not ARN) of the example topic, and zero or
 *  more lists of topic names that will be returned in response to a ListTopics call
 *  (multiple lists being used to verify the "next token" behavior).
 *  <p>
 *  All invocation handlers are exposed so that they can be overridden to throw exceptions.
 */
public class MockSNSClient implements InvocationHandler
{
    // this prefix transforms a topic name into an ARN
    public final static String ARN_PREFIX = "arn:aws:sns:us-east-1:123456789012:";

    // configuration
    private String topicName;
    private String topicArn;
    private ArrayList<List<Topic>> allTopics = new ArrayList<List<Topic>>();

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


    public MockSNSClient(String topicName, List<String>... allTopicNames)
    {
        this.topicName = topicName;
        this.topicArn = ARN_PREFIX + topicName;

        for (List<String> nameSegment : allTopicNames)
        {
            List<Topic> topicSegment = new ArrayList<Topic>();
            for (String name : nameSegment)
            {
                topicSegment.add(new Topic().withTopicArn(ARN_PREFIX + name));
            }
            allTopics.add(topicSegment);
        }

        // to simulate no existing topics
        if (this.allTopics.isEmpty())
        {
            this.allTopics.add(Collections.<Topic>emptyList());
        }
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
    public WriterFactory<SNSWriterConfig> newWriterFactory()
    {
        return new WriterFactory<SNSWriterConfig>()
        {
            @Override
            public LogWriter newLogWriter(SNSWriterConfig config)
            {
                return new SNSLogWriter(config)
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
            publishInvocationCount++;
            PublishRequest request = (PublishRequest)args[0];
            lastPublishArn     = request.getTopicArn();
            lastPublishSubject = request.getSubject();
            lastPublishMessage = request.getMessage();
            return publish(request);
        }

        throw new IllegalStateException("unexpected method called: " + methodName);
    }


    /**
     *  Invocation handler for ListTopics. This returns the appropriate segment
     *  of the list passed to the constructor.
     */
    protected ListTopicsResult listTopics(ListTopicsRequest request)
    {
        int currentSublist = (request.getNextToken() == null)
                           ? 0
                           : Integer.parseInt(request.getNextToken());
        String nextSublist = (currentSublist == allTopics.size() - 1)
                           ? null
                           : String.valueOf(currentSublist + 1);

        ListTopicsResult response = new ListTopicsResult();
        response.setTopics(allTopics.get(currentSublist));
        response.setNextToken(nextSublist);
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
        try
        {
            allowWriterThread.acquire();
            return publish0(request);
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


    protected PublishResult publish0(PublishRequest request)
    {
        return new PublishResult().withMessageId(UUID.randomUUID().toString());
    }

}
