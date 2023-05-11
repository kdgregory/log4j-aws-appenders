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
import java.util.List;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.facade.CloudWatchFacade;
import com.kdgregory.logging.aws.facade.CloudWatchFacadeException;
import com.kdgregory.logging.common.LogMessage;


/**
 *  A mock object for testing <code>CloudWatchLogWriter</code>.
 *  <p>
 *  The default implementation assumes that everything works; override methods
 *  to test failure behavior.
 */
public class MockCloudWatchFacade
implements InvocationHandler
{
    // set by constructor; consumers may modify between calls
    public CloudWatchWriterConfig config;

    // invocation counters
    public int validateConfigInvocationCount;
    public int findLogGroupInvocationCount;
    public int findLogStreamInvocationCount;
    public int createLogGroupInvocationCount;
    public int setLogGroupRetentionInvocationCount;
    public int createLogStreamInvocationCount;
    public int putEventsInvocationCount;
    public int shutdownInvocationCount;

    // recorded arguments for methods that have them
    public List<LogMessage> putEventsMessages;
    public Thread putEventsThread;

    // this is only updated by the default implementation of sendMessages()
    // it just contains the messages themselves, so can be asserted easily
    public List<String> allMessagesSent = new ArrayList<>();


    public MockCloudWatchFacade(CloudWatchWriterConfig config)
    {
        this.config = config;
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
            case "findLogGroup":
                findLogGroupInvocationCount++;
                return findLogGroup();
            case "findLogStream":
                findLogStreamInvocationCount++;
                return findLogStream();
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
            case "putEvents":
                putEventsInvocationCount++;
                putEventsThread = Thread.currentThread();
                putEventsMessages = (List<LogMessage>)args[0];
                sendMessages(putEventsMessages);
                return null;
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

    public String findLogGroup() throws CloudWatchFacadeException
    {
        return "arn:aws:logs:us-east-1:123456789012:log-group:" + config.getLogGroupName();
    }


    public String findLogStream() throws CloudWatchFacadeException
    {
        return "arn:aws:logs:us-east-1:123456789012:log-group:" + config.getLogGroupName() + ":logstream:" + config.getLogStreamName();
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


    public void sendMessages(List<LogMessage> messages)
    throws CloudWatchFacadeException
    {
        messages.stream().forEach(m -> allMessagesSent.add(m.getMessage()));
    }


    public void shutdown() throws CloudWatchFacadeException
    {
        // nothing special here
    }
}
