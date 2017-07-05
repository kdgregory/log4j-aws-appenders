// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.shared;

import java.util.concurrent.atomic.AtomicInteger;

/**
 *  The default {@link ThreadFactory} for most appenders: creates a normal-priority
 *  thread and starts it running with the specified writer.
 */
public class DefaultThreadFactory implements ThreadFactory
{
    private AtomicInteger threadNumber = new AtomicInteger();

    @Override
    public void startLoggingThread(LogWriter writer)
    {
        Thread writerThread = new Thread(writer);
        writerThread.setName("log4j-writer-" + threadNumber.getAndIncrement());
        writerThread.setPriority(Thread.NORM_PRIORITY);
        writerThread.start();
    }
}
