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

package com.kdgregory.aws.logging.common;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.atomic.AtomicInteger;


/**
 *  The default {@link ThreadFactory} for most appenders: creates a normal-priority
 *  thread and starts it running with the specified writer.
 */
public class DefaultThreadFactory implements ThreadFactory
{
    private AtomicInteger threadNumber = new AtomicInteger();

    @Override
    public void startLoggingThread(LogWriter writer, UncaughtExceptionHandler exceptionHandler)
    {
        Thread writerThread = new Thread(writer);
        writerThread.setName("log4j-aws-writer-" + threadNumber.getAndIncrement());
        writerThread.setPriority(Thread.NORM_PRIORITY);
        writerThread.setDaemon(true);
        writerThread.setUncaughtExceptionHandler(exceptionHandler);
        writerThread.start();
    }
}
