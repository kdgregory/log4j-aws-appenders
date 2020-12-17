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
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.internal.facade.CloudWatchFacade;
import com.kdgregory.logging.aws.internal.facade.CloudWatchFacadeException;
import com.kdgregory.logging.common.LogMessage;


/**
 *  A mock object that records its invocations.
 *  <p>
 *  This object is invoked from the writer thread. To avoid races between the
 *  writer and main (test) threads, you will need to apply synchronization in
 *  the logwriter so that actions can be inspected.
 */
public class MockCloudWatchFacade
implements InvocationHandler
{
    // set by constructor; consumers may modify between calls
    public CloudWatchWriterConfig config;

    // invocation counters
    public int validateConfigInvocationCount = 0;
    public int findLogGroupInvocationCount = 0;
    public int createLogGroupInvocationCount = 0;
    public int setLogGroupRetentionInvocationCount = 0;
    public int createLogStreamInvocationCount = 0;
    public int retrieveSequenceTokenInvocationCount = 0;
    public int putEventsInvocationCount = 0;
    public int shutdownInvocationCount = 0;

    // recorded arguments for methods that have them
    public String putEventsSequenceToken;
    public List<LogMessage> putEventsMessages;

    // this is only updated by the default implementation of sendMessages()
    // it just contains the messages themselves, so can be asserted easily
    public List<String> allMessagesSent = new ArrayList<>();

    // this is an arbitrary value, updated by each call to sendMessages()
    public String nextSequenceToken;


    public MockCloudWatchFacade(CloudWatchWriterConfig config)
    {
        this.config = config;
        nextSequenceToken = generateSequenceToken(config.getLogStreamName());
    }


    public CloudWatchFacade newInstance()
    {
        return (CloudWatchFacade)Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] { CloudWatchFacade.class },
                            this);
    }


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        switch (method.getName())
        {
            case "validateConfig":
                validateConfigInvocationCount++;
                return validateConfig();
            case "findLogGroup":
                findLogGroupInvocationCount++;
                return findLogGroup();
            case "createLogGroup":
                createLogGroupInvocationCount++;
                createLogGroup();
                return null;
            case "setLogGroupRetention":
                setLogGroupRetentionInvocationCount++;
                setLogGroupRetention();
                return null;
            case "createLogStream":
                createLogStreamInvocationCount++;
                createLogStream();
                return null;
            case "retrieveSequenceToken":
                retrieveSequenceTokenInvocationCount++;
                return retrieveSequenceToken();
            case "putEvents":
                putEventsInvocationCount++;
                putEventsSequenceToken = (String)args[0];
                putEventsMessages = (List<LogMessage>)args[1];
                return sendMessages(putEventsSequenceToken, putEventsMessages);
            case "shutdown":
                shutdownInvocationCount++;
                shutdown();
                return null;
            default:
                throw new RuntimeException("unimplemented method");
        }
    }

//----------------------------------------------------------------------------
//  CloudWatchFacade -- override these to return testable values
//----------------------------------------------------------------------------

    public List<String> validateConfig()
    {
        // default: all's well
        return Collections.emptyList();
    }


    public String findLogGroup() throws CloudWatchFacadeException
    {
        return "arn:aws:logs:us-east-1:123456789012:log-group:" + config.getLogGroupName();
    }


    public void createLogGroup() throws CloudWatchFacadeException
    {
        // nothing special here
    }


    public void setLogGroupRetention() throws CloudWatchFacadeException
    {
        // nothing special here
    }


    public void createLogStream() throws CloudWatchFacadeException
    {
        // nothing special here
    }


    public String retrieveSequenceToken() throws CloudWatchFacadeException
    {
        return nextSequenceToken;
    }


    public String sendMessages(String sequenceToken, List<LogMessage> messages)
    throws CloudWatchFacadeException
    {
        messages.stream().forEach(m -> allMessagesSent.add(m.getMessage()));

        nextSequenceToken = generateSequenceToken(nextSequenceToken);
        return nextSequenceToken;
    }


    public void shutdown() throws CloudWatchFacadeException
    {
        // nothing special here
    }

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    /**
     *  Creates a new sequence token from a base string. The goal is to make
     *  sequence tokens at least theoretically re-creatable.
     */
    private static String generateSequenceToken(String source)
    {
        // I don't think that this will converge
        return Base64.getEncoder().encodeToString(source.getBytes());
    }
}
