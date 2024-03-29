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

import java.time.Duration;
import java.util.concurrent.Semaphore;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.aws.facade.CloudWatchFacade;
import com.kdgregory.logging.common.util.InternalLogger;
import com.kdgregory.logging.common.util.RetryManager2;


/**
 *  Used by appender tests. Provides semaphores to synchronize test (main) and
 *  writer threads.
 */
public class TestableCloudWatchLogWriter
extends CloudWatchLogWriter
{
    private Semaphore allowMainThread   = new Semaphore(0);
    private Semaphore allowWriterThread = new Semaphore(0);

    public Thread writerThread;


    public TestableCloudWatchLogWriter(CloudWatchWriterConfig config, CloudWatchWriterStatistics stats, InternalLogger logger, CloudWatchFacade facade)
    {
        super(config, stats, logger, facade);

        // replace the stndard retry timeouts with something that operates much more quickly
        describeRetry = new RetryManager2("describe", Duration.ofMillis(50), false, true);
        createRetry = new RetryManager2("create", Duration.ofMillis(50), false, false);
        sendTimeout = Duration.ofMillis(200);
        sendRetry = new RetryManager2("send", Duration.ofMillis(50), false, false);
    }


    @Override
    public void run()
    {
        writerThread = Thread.currentThread();
        super.run();
    }


    @Override
    public synchronized void processBatch(long waitUntil)
    {
        if (!isRunning())
            return;

        try
        {
            allowWriterThread.acquire();
            super.processBatch(waitUntil);
        }
        catch (InterruptedException ex)
        {
            // this will happen when main thread calls stop()
        }
        finally
        {
            allowMainThread.release();
        }
    }


    /**
     *  Pauses the main thread and allows the writer thread to proceed.
     */
    public void waitForWriterThread()
    throws Exception
    {
        allowWriterThread.release();
        Thread.sleep(100);
        allowMainThread.acquire();
    }


    /**
     *  Allows the writer thread to proceed, without waiting (this is used in test teardown).
     */
    public void releaseWriterThread()
    throws Exception
    {
        allowWriterThread.release();
    }


    /**
     *  Used for synchronous invocation tests: grants an "infinite" number of
     *  permits for the writer to proceed.
     */
    public void disableThreadSynchronization()
    {
        allowMainThread = new Semaphore(1000);
        allowWriterThread = new Semaphore(1000);
    }
}
