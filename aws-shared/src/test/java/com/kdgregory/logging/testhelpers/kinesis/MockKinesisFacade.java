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

package com.kdgregory.logging.testhelpers.kinesis;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.kdgregory.logging.aws.internal.facade.KinesisFacade;
import com.kdgregory.logging.aws.kinesis.KinesisConstants.StreamStatus;
import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;
import com.kdgregory.logging.common.LogMessage;


/**
 *  A mock object for testing <code>KinesisLogWriter</code>. 
 *  <p>
 *  The default implementation assumes that everything works; override methods
 *  to test failure behavior. 
 *  <p>
 *  Since stream status is a big deal, you can construct  with a list of status values 
 *  that are returned in order. After that list is exhausted, <code>retrieveStreamStatus()</code> 
 *  always returns the last item in the list.
 */
public class MockKinesisFacade
implements InvocationHandler
{
    // these are set by constructor
    private Iterator<StreamStatus> statusItx;

    // invocation counters
    public int retrieveStreamStatusInvocationCount;
    public int createStreamInvocationCount;
    public int setRetentionPeriodInvocationCount;
    public int putRecordsInvocationCount;
    public int shutdownInvocationCount;

    // arguments passed to putRecords()
    public List<LogMessage> putRecordsBatch;
    public List<LogMessage> putRecordsHistory = new ArrayList<LogMessage>();
    public Thread putRecordsThread;

    // this is the status returned after iterator expires; updated from iterator
    private StreamStatus defaultStatus = StreamStatus.ACTIVE;


    // note: we pass config even though we don't (currently) use it
    public MockKinesisFacade(KinesisWriterConfig config, StreamStatus... statusReturns)
    {
        statusItx = Arrays.asList(statusReturns).iterator();
    }


    public KinesisFacade newInstance()
    {
        return (KinesisFacade)Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] { KinesisFacade.class },
                            this);
    }


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        switch (method.getName())
        {
            case "retrieveStreamStatus":
                retrieveStreamStatusInvocationCount++;
                return retrieveStreamStatus();
            case "createStream":
                createStreamInvocationCount++;
                createStream();
                return null;
            case "setRetentionPeriod":
                setRetentionPeriodInvocationCount++;
                setRetentionPeriod();
                return null;
            case "putRecords":
                putRecordsInvocationCount++;
                putRecordsThread = Thread.currentThread();
                putRecordsBatch = (List<LogMessage>)args[0];
                putRecordsHistory.addAll(putRecordsBatch);
                return putRecords(putRecordsBatch);
            case "shutdown":
                shutdownInvocationCount++;
                shutdown();
                return null;
            default:
                throw new RuntimeException("unexpected method: " + method.getName());
        }
    }

//----------------------------------------------------------------------------
//  KinesisFacade -- override these to return testable values
//----------------------------------------------------------------------------

    public StreamStatus retrieveStreamStatus()
    {
        if (statusItx.hasNext())
        {
            defaultStatus = statusItx.next();
        }

        return defaultStatus;
    }

    public void createStream()
    {
        // default does nothing
    }

    public void setRetentionPeriod()
    {
        // default does nothing
    }

    public List<LogMessage> putRecords(List<LogMessage> batch)
    {
        return Collections.emptyList();
    }

    public void shutdown()
    {
        throw new UnsupportedOperationException("FIXME - implement");
    }
}
