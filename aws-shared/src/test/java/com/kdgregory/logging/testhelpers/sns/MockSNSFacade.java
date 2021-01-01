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

package com.kdgregory.logging.testhelpers.sns;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import com.kdgregory.logging.aws.internal.facade.SNSFacade;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;
import com.kdgregory.logging.common.LogMessage;


/**
 *  A mock implementation of the facade. Maintains an internal list of known topics,
 *  so can be used for present/not-present tests without overriding methods.
 */
public class MockSNSFacade
implements InvocationHandler
{
    private final static String ARN_PREFIX = "arn:aws:sns:us-east-1:123456789012:";

    // saved from constructor
    private SNSWriterConfig config;
    private Map<String,String> knownTopicsByName = new HashMap<>();
    private Map<String,String> knownTopicsByArn = new HashMap<>();

    public int createTopicInvocationCount;
    public int lookupTopicInvocationCount;
    public int publishInvocationCount;
    public int shutdownInvocationCount;

    public String publishArn;
    public String publishSubject;
    public LogMessage publishMessage;


    public MockSNSFacade(SNSWriterConfig config, String... existingTopicNames)
    {
        this.config = config;
        for (String topicName : existingTopicNames)
        {
            String topicArn = ARN_PREFIX + topicName;
            knownTopicsByName.put(topicName, topicArn);
            knownTopicsByArn.put(topicArn,topicArn);
        }
    }


    public SNSFacade newInstance()
    {
        return (SNSFacade)Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] { SNSFacade.class },
                            this);
    }


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        String methodName = method.getName();
        switch (methodName)
        {
            case "createTopic" :
                createTopicInvocationCount++;
                return createTopic();
            case "lookupTopic" :
                lookupTopicInvocationCount++;
                return lookupTopic();
            case "publish" :
                publishInvocationCount++;
                publishArn = config.getTopicArn();
                publishSubject = config.getSubject();
                publishMessage = (LogMessage)args[0];
                publish(publishMessage);
                return null;
            case "shutdown" :
                shutdownInvocationCount++;
                shutdown();
                return null;
            default :
                throw new RuntimeException("unexpected method: " + methodName);
        }
    }

//----------------------------------------------------------------------------
//  SNSFacade -- override these to test behavior
//----------------------------------------------------------------------------

    public String lookupTopic()
    {
        return (config.getTopicArn() != null)
             ? knownTopicsByArn.get(config.getTopicArn())
             : knownTopicsByName.get(config.getTopicName());
    }


    public String createTopic()
    {
        String topicArn = ARN_PREFIX + config.getTopicName();
        knownTopicsByName.put(config.getTopicName(), topicArn);
        knownTopicsByArn.put(topicArn, topicArn);
        return topicArn;
    }


    public void publish(LogMessage message)
    {
    }


    public void shutdown()
    {
    }

}
