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

package com.kdgregory.logging.aws;

import java.lang.Thread.UncaughtExceptionHandler;

import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.lang.StringUtil;

import com.kdgregory.logging.aws.common.ClientFactory;
import com.kdgregory.logging.aws.common.DefaultThreadFactory;
import com.kdgregory.logging.aws.common.WriterFactory;
import com.kdgregory.logging.aws.internal.AbstractAppenderStatistics;
import com.kdgregory.logging.aws.internal.AbstractLogWriter;
import com.kdgregory.logging.aws.internal.AbstractWriterConfig;
import com.kdgregory.logging.aws.internal.MessageQueue;
import com.kdgregory.logging.aws.testhelpers.TestableInternalLogger;

/**
 *  Base class for the writer tests. Defines utility methods and variables
 *  used by all tests.
 */
public abstract class AbstractLogWriterTest
<
    WriterType extends AbstractLogWriter<?,?,?>,
    ConfigType extends AbstractWriterConfig,
    StatsType extends AbstractAppenderStatistics,
    AWSClientType
>
{
    protected TestableInternalLogger internalLogger = new TestableInternalLogger();

    /**
     *  Default configuration is set in setUp(), potentially overridden by tests.
     */
    protected ConfigType config;

    /**
     *  Statistics object is initialized by setUp().
     */
    protected StatsType stats;

    /**
     *  This is set by createWriter().
     */
    protected WriterType writer;

    /**
     *  This is set by createWriter().
     */
    protected MessageQueue messageQueue;

    /**
     *  This is set by the writer thread's uncaught exception handler. It should
     *  be checked by tearDown() to verify no unexpcted exceptions.
     */
    protected Throwable uncaughtException;


    /**
     *  This should be passed to any thread factory.
     */
    protected UncaughtExceptionHandler defaultUncaughtExceptionHandler
        = new UncaughtExceptionHandler()
        {
            @Override
            public void uncaughtException(Thread t, Throwable e)
            {
                uncaughtException = e;
            }
        };


    /**
     *  This is used whenever we explicitly create a writer (rather than use the
     *  mock factory. It creates a null client, so any attempt to use that client
     *  will throw.
     */
    protected ClientFactory<AWSClientType> dummyClientFactory = new ClientFactory<AWSClientType>()
    {
        @Override
        public AWSClientType createClient()
        {
            return null;
        }
    };


    /**
     *  Creates a writer using the provided factory, waiting for it to be initialized.
     */
    protected void createWriter(WriterFactory<ConfigType,StatsType> factory)
    throws Exception
    {
        writer = (WriterType)factory.newLogWriter(config, stats, internalLogger);
        messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        new DefaultThreadFactory().startLoggingThread(writer, defaultUncaughtExceptionHandler);

        // we'll spin until either the writer is initialized, signals an error,
        // or a 5-second timeout expires
        for (int ii = 0 ; ii < 100 ; ii++)
        {
            if (writer.isInitializationComplete())
                return;
            if (! StringUtil.isEmpty(stats.getLastErrorMessage()))
                return;
            Thread.sleep(50);
        }

        fail("unable to initialize writer");
    }

}
