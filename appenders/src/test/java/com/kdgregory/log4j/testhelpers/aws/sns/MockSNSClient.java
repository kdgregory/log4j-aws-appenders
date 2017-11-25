// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.testhelpers.aws.sns;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    private final static String ARN_PREFIX = "arn:aws:sns:us-east-1:123456789012:";
    
    private String topicArn;
    private ArrayList<List<Topic>> allTopics = new ArrayList<List<Topic>>();
    
    // the following record invocations and are exposed for testing
    public volatile int listTopicsInvocationCount;
    public volatile int createTopicInvocationCount;
    public volatile int publishInvocationCount;
    
    
    public MockSNSClient(String topicName, List<String>... allTopicNames)
    {
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
                        client = (AmazonSNS)Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] { AmazonSNS.class },
                                    MockSNSClient.this);
                    }
                };
            }
        };
    }
    
    
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        String methodName = method.getName(); 
        if (methodName.equals("listTopics"))
        {
            listTopicsInvocationCount++;
            return listTopics((ListTopicsRequest)args[0]);
        }
        else if (methodName.equals("createTopic"))
        {
            createTopicInvocationCount++;
            return createTopic((CreateTopicRequest)args[0]);
        }
        else if (methodName.equals("publish"))
        {
            publishInvocationCount++;
            return publish((PublishRequest)(args[0]));
        }
        throw new IllegalStateException("unexpected method called: " + methodName);
    }
    
    
    /**
     *  Invocation handler for ListTopics. This returns the appropriate segment
     *  of the list passed to the constructor.
     */
    protected ListTopicsResult listTopics(ListTopicsRequest request )
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
    private CreateTopicResult createTopic(CreateTopicRequest request)
    {
        return new CreateTopicResult();
    }
    
    
    /**
     *  Invocation handler for Publish.
     */
    private PublishResult publish(PublishRequest request)
    {
        return new PublishResult();
    }
}
