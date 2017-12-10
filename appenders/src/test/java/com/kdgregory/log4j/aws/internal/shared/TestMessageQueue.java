// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.shared;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Test;
import static org.junit.Assert.*;

import com.kdgregory.log4j.aws.internal.shared.MessageQueue.DiscardAction;


public class TestMessageQueue
{
    LogMessage m1 = new LogMessage(System.currentTimeMillis(), "m1");
    LogMessage m2 = new LogMessage(System.currentTimeMillis(), "m2");
    LogMessage m3 = new LogMessage(System.currentTimeMillis(), "m3");


    @Test
    public void testBasicOperation() throws Exception
    {
        MessageQueue queue = new MessageQueue(1000, DiscardAction.none);

        assertTrue("newly constructed queue is empty",              queue.isEmpty());

        queue.enqueue(m1);
        assertEquals("after first enqueue, reported counter size",  1,                          queue.size());
        assertEquals("after first enqueue, reported queue size",    1,                          queue.queueSize());
        assertFalse("after first enqueue, queue is not empty",      queue.isEmpty());

        queue.enqueue(m2);
        assertEquals("after second enqueue, reported counter size", 2,                          queue.size());
        assertEquals("after second enqueue, reported queue size",   2,                          queue.queueSize());
        assertEquals("after second enqueue, contents",              Arrays.asList(m1, m2),      queue.toList());

        assertEquals("dequeue of first message",                    m1,                         queue.dequeue());
        assertEquals("after first dequeue, reported counter size",  1,                          queue.size());
        assertEquals("after first dequeue, reported queue size",    1,                          queue.queueSize());
        assertEquals("dequeue of second message",                   m2,                         queue.dequeue());

        assertEquals("after second dequeue, reported counter size", 0,                          queue.size());
        assertEquals("after second dequeue, reported queue size",   0,                          queue.queueSize());

        assertEquals("dequeue of nonexistent message",              null,                       queue.dequeue());
        assertTrue("after all dequeues, queue is empty",            queue.isEmpty());

        queue.requeue(m1);
        queue.requeue(m2);
        queue.requeue(m3);
        assertEquals("after requeues",                              Arrays.asList(m3, m2, m1),  queue.toList());
    }


    @Test
    public void testTimedDequeue() throws Exception
    {
        MessageQueue queue = new MessageQueue(1000, DiscardAction.none);

        queue.enqueue(m1);

        long t1start = System.currentTimeMillis();
        assertNotNull("expected dequeue to return message", queue.dequeue(1000L));
        long t1finish = System.currentTimeMillis();
        long t1elapsed = t1finish - t1start;
        assertTrue("dequeue happened immediately", t1elapsed < 10);

        long t2start = System.currentTimeMillis();
        assertNull("expected dequeue to return null", queue.dequeue(200L));
        long t2finish = System.currentTimeMillis();
        long t2elapsed = t2finish - t2start;
        assertTrue("dequeue took roughly 200ms (was " + t2elapsed + ")", (t2elapsed > 180) && (t2elapsed < 220));

        long t3start = System.currentTimeMillis();
        assertNull("expected dequeue to return null", queue.dequeue(-10));
        long t3finish = System.currentTimeMillis();
        long t3elapsed = t3finish - t3start;
        assertTrue("dequeue happened immediately", t3elapsed < 10);
    }


    @Test
    public void testConcurrentChanges() throws Exception
    {
        // This is a probabilistic test: we run lots of threads that randomly mutate
        // the queue, either adding or deleting items. Based on the probability of
        // each action, we should end up with a queue that contains a certain number
        // of items. We'll accept +/- 20%.

        final int numThreads            = 16;
        final int operationsPerThread   = 10000;
        final double enqueueThreshold   = .50;
        final double requeueThreshold   = .60;
        final double netAddPercent      = .20;  // (.60 enqueue or requueue - .40 dequeue)
        final int expectedMessagesAtEnd = (int)((numThreads * operationsPerThread) * netAddPercent);
        final int minExpectedMessages   = (int)(expectedMessagesAtEnd * .8);
        final int maxExpectedMessages   = (int)(expectedMessagesAtEnd * 1.2);

        final MessageQueue queue = new MessageQueue(100000, DiscardAction.none);

        final List<Thread> threads = new ArrayList<Thread>(numThreads);
        for (int threadIdx = 0 ; threadIdx < numThreads ; threadIdx++)
        {
            threads.add(new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    long myThreadId = Thread.currentThread().getId();
                    Random rnd = new Random(myThreadId);
                    LogMessage myMessage = new LogMessage(System.currentTimeMillis(), String.valueOf(myThreadId));

                    for (int ii = 0 ; ii < operationsPerThread ; ii++)
                    {
                        double decision = rnd.nextDouble();
                        if (decision <= enqueueThreshold)
                            queue.enqueue(myMessage);
                        else if (decision <= requeueThreshold)
                            queue.requeue(myMessage);
                        else
                            queue.dequeue();
                        Thread.yield();
                    }
                }
            }));
        }

        for (Thread thread : threads)
        {
            thread.start();
        }
        for (Thread thread : threads)
        {
            thread.join();
        }

        assertEquals("count reported by size() and queueSize()", queue.size(), queue.queueSize());
        assertTrue("queue size > " + minExpectedMessages + " (was " + queue.size() + ")",
                   queue.size() > minExpectedMessages);
        assertTrue("queue size < " + maxExpectedMessages + " (was " + queue.size() + ")",
                   queue.size() < maxExpectedMessages);
    }


    @Test
    public void testDiscardNone() throws Exception
    {
        final int discardThreshold = 10;
        final int messagesToEnqueue = 20;

        MessageQueue queue = new MessageQueue(discardThreshold, DiscardAction.none);

        for (int ii = 0 ; ii < messagesToEnqueue ; ii++)
        {
            queue.enqueue(new LogMessage(System.currentTimeMillis(), String.valueOf(ii)));
        }

        assertEquals("queue ignores discard threshold", messagesToEnqueue, queue.size());
    }


    @Test
    public void testDiscardOldest() throws Exception
    {
        final int discardThreshold = 10;
        final int messagesToEnqueue = 20;

        MessageQueue queue = new MessageQueue(discardThreshold, DiscardAction.oldest);

        for (int ii = 0 ; ii < messagesToEnqueue ; ii++)
        {
            queue.enqueue(new LogMessage(System.currentTimeMillis(), String.valueOf(ii)));
        }

        assertEquals("queue size at threshold", discardThreshold, queue.size());

        List<LogMessage> messages = queue.toList();
        assertEquals("first message in queue",  "10", messages.get(0).getMessage());
        assertEquals("last message in queue",   "19", messages.get(discardThreshold - 1).getMessage());
    }


    @Test
    public void testDiscardNewest() throws Exception
    {
        final int discardThreshold = 10;
        final int messagesToEnqueue = 20;

        MessageQueue queue = new MessageQueue(discardThreshold, DiscardAction.newest);

        for (int ii = 0 ; ii < messagesToEnqueue ; ii++)
        {
            queue.enqueue(new LogMessage(System.currentTimeMillis(), String.valueOf(ii)));
        }

        assertEquals("queue size at threshold", discardThreshold, queue.size());

        List<LogMessage> messages = queue.toList();
        assertEquals("first message in queue",  "0", messages.get(0).getMessage());
        assertEquals("last message in queue",   "9", messages.get(discardThreshold - 1).getMessage());
    }
}
