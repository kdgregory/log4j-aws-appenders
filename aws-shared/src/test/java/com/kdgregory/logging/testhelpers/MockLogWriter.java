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

package com.kdgregory.logging.testhelpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.kdgregory.logging.aws.internal.AbstractWriterConfig;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.util.DiscardAction;


/**
 *  Common functionality for destination-specific writer mocks.
 */
public class MockLogWriter<T extends AbstractWriterConfig<?>>
implements LogWriter
{
    public T config;

    public volatile Thread writerThread;
    public Thread shutdownHook;

    // the actual writers use a concurrent queue
    public List<LogMessage> messages = Collections.synchronizedList(new ArrayList<LogMessage>());
    public LogMessage lastMessage;

    public boolean stopped;

    public int initializeInvocationCount;
    public int processBatchInvocationCount;
    public long processBatchLastTimeout;
    public int cleanupInvocationCount;
    public int waitUntilStoppedInvocationCount;


    public MockLogWriter(T config)
    {
        this.config = config;
    }

//----------------------------------------------------------------------------
//  LogWriter
//----------------------------------------------------------------------------

    @Override
    public void setBatchDelay(long value)
    {
        this.config.setBatchDelay(value);
    }


    @Override
    public void setDiscardThreshold(int value)
    {
        this.config.setDiscardThreshold(value);
    }


    @Override
    public void setDiscardAction(DiscardAction value)
    {
        this.config.setDiscardAction(value);
    }


    @Override
    public void setShutdownHook(Thread shutdownHook)
    {
        this.shutdownHook = shutdownHook;
    }


    @Override
    public int maxMessageSize()
    {
        // destination-specific subclasses should override this
        return 0;
    }


    @Override
    public boolean isSynchronous()
    {
        return config.getSynchronous();
    }


    @Override
    public void addMessage(LogMessage message)
    {
        messages.add(message);
        lastMessage = message;
    }


    public boolean initialize()
    {
        initializeInvocationCount++;
        return true;
    }


    @Override
    public boolean waitUntilInitialized(long millisToWait)
    {
        try
        {
            long sleepUntil = System.currentTimeMillis() + millisToWait;
            while (System.currentTimeMillis() < sleepUntil)
            {
                if (writerThread != null)
                    return true;
                Thread.sleep(10);
            }
            return false;
        }
        catch (InterruptedException ex)
        {
            throw new RuntimeException("unexpected interrupt");
        }
    }


    public synchronized void processBatch(long shutdownTime)
    {
        processBatchInvocationCount++;
        processBatchLastTimeout = shutdownTime;
    }


    @Override
    public void stop()
    {
        stopped = true;
    }


    @Override
    public void waitUntilStopped(long millisToWait)
    {
        waitUntilStoppedInvocationCount++;
        try
        {
            if ((writerThread != null) && (writerThread != Thread.currentThread()))
            {
                writerThread.join(millisToWait);
            }
        }
        catch (InterruptedException ex)
        {
            throw new RuntimeException("unexpected interrupt");
        }
    }

    public void cleanup()
    {
        cleanupInvocationCount++;
    }

//----------------------------------------------------------------------------
//  Runnable
//----------------------------------------------------------------------------

    @Override
    public void run()
    {
        // most of the tests don't want the writer running on a thread, but
        // for those that do, we need to track it
        writerThread = Thread.currentThread();
    }

//----------------------------------------------------------------------------
//  Mock-specific methods
//----------------------------------------------------------------------------

    /**
     *  Returns the text for the numbered message (starting at 0).
     */
    public String getMessage(int msgnum)
    throws Exception
    {
        return messages.get(msgnum).getMessage();
    }
}
