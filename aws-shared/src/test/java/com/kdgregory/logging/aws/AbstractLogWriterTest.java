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
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.lang.StringUtil;
import net.sf.kdgcommons.lang.ThreadUtil;
import static net.sf.kdgcommons.test.StringAsserts.*;

import com.kdgregory.logging.aws.internal.AbstractWriterStatistics;
import com.kdgregory.logging.aws.testhelpers.TestableInternalLogger;
import com.kdgregory.logging.aws.internal.AbstractLogWriter;
import com.kdgregory.logging.aws.internal.AbstractWriterConfig;
import com.kdgregory.logging.common.factories.ClientFactory;
import com.kdgregory.logging.common.factories.DefaultThreadFactory;
import com.kdgregory.logging.common.factories.WriterFactory;
import com.kdgregory.logging.common.util.MessageQueue;

/**
 *  Base class for the writer tests. Defines utility methods and variables
 *  used by all tests.
 */
public abstract class AbstractLogWriterTest
<
    WriterType extends AbstractLogWriter<?,?,?>,
    ConfigType extends AbstractWriterConfig,
    StatsType extends AbstractWriterStatistics,
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

        new DefaultThreadFactory("test").startLoggingThread(writer, defaultUncaughtExceptionHandler);

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


//----------------------------------------------------------------------------
//  Utility methods to synchronize main and writer threads.
//----------------------------------------------------------------------------

    /**
     *  Joins to the writer's dispatch thread. This should only be called when
     *  the writer is explicitly stopped.
     */
    protected void joinWriterThread()
    throws Exception
    {
        Thread writerThread = ClassUtil.getFieldValue(writer, "dispatchThread", Thread.class);
        assertNotNull("expeted to retrieve writer thread", writerThread);
        writerThread.join();
    }

//----------------------------------------------------------------------------
//  The following methods are work-arounds for a race condition in which the
//  main thread is allowed to continue immediately after the mock client call,
//  and makes assertions before the writer thread has the chance to update
//  whatever is being asserted. This normally affects low-CPU-count machines
//  such as the t2.small that I use for final unit tests.
//----------------------------------------------------------------------------

    /**
     *  Asserts that the statistics object has recorded the expected number of
     *  sent messages.
     */
    protected void assertStatisticsMessagesSent(String message, int expected)
    {
        int actual = 0;
        for (int ii = 0 ; ii < 10 ; ii++)
        {
            actual = ((AbstractWriterStatistics)stats).getMessagesSent();
            if (expected == actual)
                return;
            ThreadUtil.sleepQuietly(10);
        }

        // this will always fail
        assertEquals(message, expected, actual);
    }

    /**
     *  A version of the message-sent assertion with fixed message.
     */
    protected void assertStatisticsMessagesSent(int expected)
    {
        assertStatisticsMessagesSent("statistics: messages sent", expected);
    }


    /**
     *  Asserts that an error message has been reported to the statistics object,
     *  with timestamp.
     */
    protected void assertStatisticsErrorMessage(String expectedMessageRegex)
    {
        String errorMessage = null;
        for (int ii = 0 ; ii < 10 ; ii++)
        {
            errorMessage = ((AbstractWriterStatistics)stats).getLastErrorMessage();
            if (errorMessage != null)
                break;
            ThreadUtil.sleepQuietly(10);
        }

        assertRegex("statistics: error message (was: " + errorMessage + ")", expectedMessageRegex, errorMessage);

        Date errorTimestamp = null;
        for (int ii = 0 ; ii < 10 ; ii++)
        {
            errorTimestamp = ((AbstractWriterStatistics)stats).getLastErrorTimestamp();
            if (errorTimestamp != null)
                break;
            ThreadUtil.sleepQuietly(10);
        }

        assertNotNull("statistics: error timestamp reported", errorTimestamp);
    }


    /**
     *  Asserts that an error object has been reported to the statistics object,
     *  with stack trace and specified message.
     */
    protected void assertStatisticsException(Class<?> expectedExceptionClass, String expectedMessageRegex)
    {
        Throwable actualException = null;
        for (int ii = 0 ; ii < 10 ; ii++)
        {
            actualException = ((AbstractWriterStatistics)stats).getLastError();
            if (actualException != null)
                break;
            ThreadUtil.sleepQuietly(10);
        }

        assertNotNull("statistics: reported exception", actualException);
        assertEquals("statistics: reported exception class", expectedExceptionClass, actualException.getClass());
        assertRegex("statistics: reported exception message (was: " + actualException.getMessage() + ")", expectedMessageRegex, actualException.getMessage());

        List<String> actualStacktrace = null;
        for (int ii = 0 ; ii < 10 ; ii++)
        {
            actualStacktrace = ((AbstractWriterStatistics)stats).getLastErrorStacktrace();
            if (actualStacktrace != null)
                break;
            ThreadUtil.sleepQuietly(10);
        }

        assertNotNull("statistics: reported exception includes stack trace", actualStacktrace);
        assertTrue("statistics: reported exception includes stack trace", actualStacktrace.size() > 0);
    }
}
