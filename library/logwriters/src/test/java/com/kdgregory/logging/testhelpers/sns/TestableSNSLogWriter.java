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

package com.kdgregory.logging.testhelpers.sns;

import java.time.Duration;
import java.util.concurrent.Semaphore;

import com.kdgregory.logging.aws.facade.SNSFacade;
import com.kdgregory.logging.aws.sns.SNSLogWriter;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;
import com.kdgregory.logging.aws.sns.SNSWriterStatistics;
import com.kdgregory.logging.common.util.InternalLogger;
import com.kdgregory.logging.common.util.RetryManager2;


/**
 *  Used by appender tests. Provides semaphores to synchronize test (main) and
 *  writer threads.
 */
public class TestableSNSLogWriter
extends SNSLogWriter
{

    private Semaphore allowMainThread   = new Semaphore(0);
    private Semaphore allowWriterThread = new Semaphore(0);


    public TestableSNSLogWriter(SNSWriterConfig config, SNSWriterStatistics stats, InternalLogger logger, SNSFacade facade)
    {
        super(config, stats, logger, facade);

        // replace the stndard retry timeouts with something that operates much more quickly
        describeTimeout = Duration.ofMillis(50);
        describeRetry = new RetryManager2("describe", Duration.ofMillis(50), false, false);
        createRetry = new RetryManager2("create", Duration.ofMillis(50), false, false);
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
