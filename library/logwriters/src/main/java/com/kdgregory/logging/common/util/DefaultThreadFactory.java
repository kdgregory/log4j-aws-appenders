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

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.atomic.AtomicInteger;

import com.kdgregory.logging.common.LogWriter;


/**
 *  The standard {@link ThreadFactory}: launches a daemon thread to run normal
 *  writers, runs synchronous writers inline.
 */
public class DefaultThreadFactory implements ThreadFactory
{
    private static AtomicInteger threadNumber = new AtomicInteger(0);

    private String appenderName;


    public DefaultThreadFactory(String appenderName)
    {
        this.appenderName = appenderName;
    }


    @Override
    public void startWriterThread(final LogWriter writer, UncaughtExceptionHandler exceptionHandler)
    {
        if (writer.isSynchronous())
        {
            try
            {
                writer.run();
            }
            catch (Throwable ex)
            {
                exceptionHandler.uncaughtException(Thread.currentThread(), ex);
            }
        }
        else
        {
            Thread writerThread = createThread(writer, exceptionHandler);
            writerThread.start();
        }
    }


    /**
     *  Creates and initializes the thread. This can be overridden by tests that need
     *  to work with the thread; in normal operation we just let it do its thing.
     */
    protected Thread createThread(LogWriter writer, UncaughtExceptionHandler exceptionHandler)
    {
        Thread writerThread = new Thread(writer);
        writerThread.setName("com-kdgregory-aws-logwriter-" + appenderName + "-" + threadNumber.incrementAndGet());
        writerThread.setPriority(Thread.NORM_PRIORITY);
        writerThread.setDaemon(true);
        writerThread.setUncaughtExceptionHandler(exceptionHandler);
        return writerThread;
    }
}
