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

package com.kdgregory.logging.common.factories;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.Test;
import static org.junit.Assert.*;

import static net.sf.kdgcommons.test.StringAsserts.*;

import com.kdgregory.logging.aws.internal.AbstractWriterConfig;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.testhelpers.MockLogWriter;
import com.kdgregory.logging.testhelpers.ThrowingWriterFactory;


public class TestDefaultThreadFactory
{
//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    private static class TestableDefaultThreadFactory
    extends DefaultThreadFactory
    {
        public Collection<Thread> threads = new ConcurrentLinkedQueue<>();
        public Map<Thread,Throwable> uncaughtExceptions = new ConcurrentHashMap<>();

        public TestableDefaultThreadFactory(String appenderName)
        {
            super(appenderName);
        }

        // this is only valid once the threads have been initialized
        public void waitForThreadsToFinish()
        throws InterruptedException
        {
            for (Thread thread : threads)
                thread.join();
        }

        // a convenience method that will use a built-in exception handler
        public void startLoggingThread(LogWriter writer)
        {
            startLoggingThread(writer, false, new UncaughtExceptionHandler()
            {
                @Override
                public void uncaughtException(Thread t, Throwable e)
                {
                    uncaughtExceptions.put(t, e);
                }
            });
        }

        @Override
        protected Thread createThread(LogWriter writer, UncaughtExceptionHandler exceptionHandler)
        {
            Thread thread = super.createThread(writer, exceptionHandler);
            threads.add(thread);
            return thread;
        }
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testBasicOperation() throws Exception
    {
        TestableDefaultThreadFactory factory = new TestableDefaultThreadFactory("test");
        MockLogWriter<AbstractWriterConfig<?>> writer = new MockLogWriter<>(null);

        factory.startLoggingThread(writer);
        assertTrue("writer was started", writer.waitUntilInitialized(1000));
        assertTrue("no uncaught exceptions", factory.uncaughtExceptions.isEmpty());

        ArrayList<Thread> threads = new ArrayList<>(factory.threads);
        assertEquals("number of threads created", 1, threads.size());

        assertRegex("thread name", ".*logwriter-test-\\d+", threads.get(0).getName());
        assertSame("writer was started on thread", threads.get(0), writer.writerThread);
    }


    @Test
    public void testUncaughtExceptions() throws Exception
    {
        TestableDefaultThreadFactory factory = new TestableDefaultThreadFactory("test");
        LogWriter writer = new ThrowingWriterFactory<>().newLogWriter(null, null, null);

        factory.startLoggingThread(writer);
        assertTrue("writer was started", writer.waitUntilInitialized(1000));

        // these will trigger the uncaught exception
        writer.addMessage(null);
        writer.addMessage(null);

        // after which the writer thread will exit
        factory.waitForThreadsToFinish();

        Set<Thread> allThreads = new HashSet<>(factory.threads);
        Set<Thread> exceptionThreads = new HashSet<>(factory.uncaughtExceptions.keySet());

        assertEquals("caught exception", 1, exceptionThreads.size());
        assertEquals("exception threads", allThreads, exceptionThreads);
    }
}
