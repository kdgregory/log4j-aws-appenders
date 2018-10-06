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

package com.kdgregory.logging.common.util;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.kdgregory.logging.common.LogMessage;


/**
 *  A thread-safe message queue that keeps track of the current number of entries
 *  and optionally discards messages after its size reaches a given threshold.
 *  <p>
 *  Implementation note: all operations are coded as update queue followed by update
 *  count. This means that it is possible that {@link #size()} may not reflect the
 *  actual size of the queue at any given point in time (but usually will).
 */
public class MessageQueue
{

//----------------------------------------------------------------------------
//  Instance variables and constructor
//----------------------------------------------------------------------------

    private LinkedBlockingDeque<LogMessage> messageQueue = new LinkedBlockingDeque<LogMessage>();
    private AtomicInteger messageCount = new AtomicInteger();
    private AtomicInteger droppedMessageCount = new AtomicInteger();

    private volatile int discardThreshold;
    private volatile DiscardAction discardAction;


    public MessageQueue(int discardThreshold, DiscardAction discardAction)
    {
        this.discardThreshold = discardThreshold;
        this.discardAction = discardAction;
    }


//----------------------------------------------------------------------------
//  Properties
//----------------------------------------------------------------------------

    /**
     *  Changes the discard threshold.
     */
    public void setDiscardThreshold(int value)
    {
        discardThreshold = value;
    }


    /**
     *  Returns the current discard threshold; this is intended for testing.
     */
    public int getDiscardThreshold()
    {
        return discardThreshold;
    }


    /**
     *  Changes the discard action
     */
    public void setDiscardAction(DiscardAction value)
    {
        discardAction = value;
    }


    /**
     *  Returns the current discard action; this is intended for testing.
     */
    public DiscardAction getDiscardAction()
    {
        return discardAction;
    }


    /**
     *  Returns the number of messages that have been dropped.
     */
    public int getDroppedMessageCount()
    {
        return droppedMessageCount.get();
    }


//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    /**
     *  Determines whether the queue is empty. Beware that the queue could be empty
     *  when checked, but another thread could concurrently add a message.
     */
    public boolean isEmpty()
    {
        return (messageQueue.peek() == null);
    }


    /**
     *  Adds a message to the end of the queue.
     *  <p>
     *  Note: discard policy is checked after adding the message. If the policy is
     *  "newest", then this message will be removed.
     */
    public void enqueue(LogMessage message)
    {
        messageQueue.addLast(message);
        messageCount.incrementAndGet();
        applyDiscard();
    }


    /**
     *  Adds a message to the start of the queue.
     *  <p>
     *  Note: discard policy is checked after adding the message. If the policy is
     *  "oldest", then this message will be removed (assuming that someone else
     *  has not dequeued it).
     */
    public void requeue(LogMessage message)
    {
        messageQueue.addFirst(message);
        messageCount.incrementAndGet();
        applyDiscard();
    }


    /**
     *  Removes a message from the front of the queue. Returns null if there are no messages.
     */
    public LogMessage dequeue()
    {
        LogMessage message = messageQueue.poll();
        if (message != null)
        {
            messageCount.decrementAndGet();
        }
        return message;
    }


    /**
     *  Removes a message from the front of the queue, waiting for a specified number of
     *  milliseconds if the queue is empty. Returns null if there are no messages in the
     *  desired time, or if the thread is interrupted.
     */
    public LogMessage dequeue(long waitTime)
    {
        // the wait time is calculated, so might not be positive
        if (waitTime < 0) waitTime = 0;

        try
        {
            LogMessage message = messageQueue.poll(waitTime, TimeUnit.MILLISECONDS);
            if (message != null)
            {
                messageCount.decrementAndGet();
            }
            return message;
        }
        catch (InterruptedException ex)
        {
            return null;
        }
    }


    /**
     *  Returns the current number of elements in the queue, as recorded by the atomic
     *  counter. This is an O(1) operation, but might not be exact.
     */
    public int size()
    {
        return messageCount.get();
    }


    /**
     *  Returns the current number of elements in the queue, as recorded by the queue
     *  itself. This is an O(N) operation. This is intended for testing.
     */
    public int queueSize()
    {
        return messageQueue.size();
    }


    /**
     *  Copies the current queue contents into a List. This is intended for testing.
     */
    public List<LogMessage> toList()
    {
        return new ArrayList<LogMessage>(messageQueue);
    }


//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    /**
     *  Checks the current queue size, and applies the discard policy if it's
     *  above the threshold.
     */
    private void applyDiscard()
    {
        if (discardAction == DiscardAction.none) return;
        if (size() <= discardThreshold) return;

        // note: with concurrent enqueues/dequeues, there is a race condition
        // between size() and actual queue size -- however, it's close enough
        // if we're at the point of discarding to not be concerned (and the
        // actual queue size, which is an O(N) operation, would have its own
        // race condition)

        while (size() > discardThreshold)
        {
            LogMessage discarded = (discardAction == DiscardAction.oldest)
                                 ? messageQueue.pollFirst()
                                 : messageQueue.pollLast();
            if (discarded != null)
            {
                messageCount.decrementAndGet();
                droppedMessageCount.incrementAndGet();
            }
        }
    }
}
