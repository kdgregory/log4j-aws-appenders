// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


/**
 *  A thread-safe message queue that keeps track of the current number of entries.
 *  <p>
 *  Implementation note: all operations are coded as update queue followed by update
 *  count. This means that it is possible that {@link #size()} will not indicate the
 *  actual size of the queue, and should not be used for decision-making.
 */
public class MessageQueue
{
    private LinkedBlockingDeque<LogMessage> messageQueue = new LinkedBlockingDeque<LogMessage>();
    private AtomicInteger messageCount = new AtomicInteger();


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
     */
    public void enqueue(LogMessage message)
    {
        messageQueue.addLast(message);
        messageCount.incrementAndGet();
    }


    /**
     *  Adds a message to the end of the queue.
     */
    public void requeue(LogMessage message)
    {
        messageQueue.addFirst(message);
        messageCount.incrementAndGet();
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
}
