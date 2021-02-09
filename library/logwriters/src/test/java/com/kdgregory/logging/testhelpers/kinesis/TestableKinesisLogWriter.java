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

import java.util.concurrent.Semaphore;

import com.kdgregory.logging.aws.facade.KinesisFacade;
import com.kdgregory.logging.aws.kinesis.KinesisLogWriter;
import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;
import com.kdgregory.logging.aws.kinesis.KinesisWriterStatistics;
import com.kdgregory.logging.common.util.InternalLogger;
import com.kdgregory.logging.common.util.RetryManager;


/**
 *  Used by appender tests. Provides semaphores to synchronize test (main) and
 *  writer threads.
 */
public class TestableKinesisLogWriter
extends KinesisLogWriter
{
    private Semaphore allowMainThread   = new Semaphore(0);
    private Semaphore allowWriterThread = new Semaphore(0);

    public Thread writerThread;


    public TestableKinesisLogWriter(KinesisWriterConfig config, KinesisWriterStatistics stats, InternalLogger logger, KinesisFacade facade)
    {
        super(config, stats, logger, facade);

        // replace the standard retry logic with something that operates much more quickly
        describeRetry = new RetryManager(50, 200, false);
        createRetry = new RetryManager(50, 200, false);
        postCreateRetry = new RetryManager(50, 200, false);
        sendRetry = new RetryManager(50, 200, false);
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
        try
        {
            allowWriterThread.acquire();
            super.processBatch(waitUntil);
        }
        catch (InterruptedException ex)
        {
            throw new RuntimeException("could not acquire semaphore");
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
     *  Used for synchronous invocation tests: grants an "infinite" number of
     *  permits for the writer to proceed.
     */
    public void disableThreadSynchronization()
    {
        allowMainThread = new Semaphore(1000);
        allowWriterThread = new Semaphore(1000);
    }

}
