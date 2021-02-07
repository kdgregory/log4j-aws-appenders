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

package com.kdgregory.logging.aws.internal;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.kdgregory.logging.common.util.MessageQueue;


/**
 *  Base class for writer statistics, providing fields that are used by all
 *  writer implementations. Concrete appender implementations hold/expose a
 *  subclass.
 *  <p>
 *  Statistics are limited to primitives and strings so that they can be read
 *  by JMX. Statistics will be read and written by different threads, so must
 *  be marked as volatile. A given statistic will only be written by appender
 *  or writer, not both, so there's no need for synchronization unless there's
 *  the possibility of multiple writers. In that case, implement an override
 *  in the subclass and synchronize there.
 *  <p>
 *  Note: the MXBean interface implemented by subclasses must explicitly expose
 *  any desired getters (we can't use a superinterface because JMX introspection
 *  only looks at declared methods).
 */
public abstract class AbstractWriterStatistics
{
    private volatile MessageQueue messageQueue;

    private volatile Throwable lastError;
    private volatile String lastErrorMessage;
    private volatile Date lastErrorTimestamp;
    private volatile List<String> lastErrorStacktrace;

    private volatile int messageSizeViolations;

    private volatile int messagesSent;
    private volatile int messagesSentLastBatch;
    private volatile int messagesRequeuedLastBatch;

    private AtomicInteger throttledWrites = new AtomicInteger();


    /**
     *  Stores the current writer's message queue. This should be called during
     *  writer initialization.
     */
    public void setMessageQueue(MessageQueue messageQueue)
    {
        this.messageQueue = messageQueue;
    }


    /**
     *  Sets the last error. Either the message or the throwable may be null (but
     *  not both).
     *  <p>
     *  Note: if multiple errors occur in rapid succession, the observer may see a
     *  mixture of reported values. This is unlikely to occur outside of testing,
     *  and the cure is to syncronize this method with the various gets, which I
     *  don't particularly want to do.
     */
    public void setLastError(String message, Throwable error)
    {
        lastErrorTimestamp = new Date();
        lastError = error;
        lastErrorMessage = (message != null) ? message : error.toString();

        if (error != null)
        {
            List<String> stacktrace = new ArrayList<String>();
            for (StackTraceElement ste : error.getStackTrace())
            {
                stacktrace.add(ste.toString());
            }
            lastErrorStacktrace = stacktrace;
        }
        else
        {
            lastErrorStacktrace = null;
        }
    }


    public Throwable getLastError()
    {
        return lastError;
    }


    public String getLastErrorMessage()
    {
        return lastErrorMessage;
    }


    public Date getLastErrorTimestamp()
    {
        return lastErrorTimestamp;
    }


    public List<String> getLastErrorStacktrace()
    {
        return lastErrorStacktrace;
    }


    public synchronized void incrementOversizeMessages()
    {
        messageSizeViolations++;
    }


    public int getOversizeMessages()
    {
        return messageSizeViolations;
    }


    /**
     *  Updates the number of messages sent with the given count. This should only
     *  be called after all failures have been identified.
     */
    public synchronized void updateMessagesSent(int count)
    {
        messagesSent += count;
    }


    public int getMessagesSent()
    {
        return messagesSent;
    }


    /**
     *  Sets the number of messages sent in the last batch.
     */
    public void setMessagesSentLastBatch(int count)
    {
        messagesSentLastBatch = count;
    }


    public int getMessagesSentLastBatch()
    {
        return messagesSentLastBatch;
    }


    /**
     *  Sets the number of messages requeued in the last batch.
     */
    public void setMessagesRequeuedLastBatch(int count)
    {
        messagesRequeuedLastBatch = count;
    }


    public int getMessagesRequeuedLastBatch()
    {
        return messagesRequeuedLastBatch;
    }


    /**
     *  Returns the number of messages discarded by the current writer's message queue.
     */
    public int getMessagesDiscarded()
    {
        return messageQueue.getDroppedMessageCount();
    }


    /**
     *  Returns the number of writes that have been retried due to throttling.
     */
    public int getThrottledWrites()
    {
        return throttledWrites.get();
    }


    /**
     *  Increments the number of writes that have been throttled.
     */
    public void incrementThrottledWrites()
    {
        throttledWrites.incrementAndGet();
    }
}
