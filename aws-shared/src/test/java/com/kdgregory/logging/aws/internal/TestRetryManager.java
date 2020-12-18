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

package com.kdgregory.logging.aws.internal;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.test.NumericAsserts;


public class TestRetryManager
{
    private final static String TEST_VALUE = "test value";
    private final static RuntimeException TEST_EXC = new RuntimeException("test exception");


    public static class InvokeTarget
    implements Supplier<String>
    {
        private Iterator<Object> itx;

        public InvokeTarget(Object... returns)
        {
            itx = Arrays.asList(returns).iterator();
        }

        @Override
        public String get()
        {
            Object value = itx.next();
            if (value == null)
                return null;
            else if (value instanceof RuntimeException)
                throw (RuntimeException)value;
            else
                return String.valueOf(value);
        }
    }


    public static class ExceptionHandler
    implements Consumer<RuntimeException>
    {
        public List<RuntimeException> handledExceptions = new ArrayList<>();

        @Override
        public void accept(RuntimeException ex)
        {
            handledExceptions.add(ex);
        }
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testSleepQuietly() throws Exception
    {
        long start = System.currentTimeMillis();
        boolean result =  RetryManager.sleepQuietly(50);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue("returned normally", result);
        NumericAsserts.assertInRange("slept for expected time", 40, 60, elapsed);
    }


    @Test
    public void testSleepQuietlyWithInterruption() throws Exception
    {
        final Thread mainThread = Thread.currentThread();
        new Thread() {
            @Override
            public void run()
            {
                try
                {
                    Thread.sleep(50);
                    mainThread.interrupt();
                }
                catch (InterruptedException e)
                {
                    // not likely, but :shrug:
                    System.err.println("interruptor thread was interrupted");
                }
            }
        }.start();

        long start = System.currentTimeMillis();
        boolean result =  RetryManager.sleepQuietly(150);
        long elapsed = System.currentTimeMillis() - start;

        assertFalse("returned abnormally", result);

        // we don't know how long it will take to start a thread, but we know
        // the minimum expected time before interrupt, and will assert something
        // less than the desired sleep time
        NumericAsserts.assertInRange("sleep was cut short", 50, 100, elapsed);
    }


    @Test
    public void testRetryManagerImmediateReturn() throws Exception
    {
        InvokeTarget target = new InvokeTarget(TEST_VALUE);

        // long sleeps should alert us to a problem
        RetryManager rm = new RetryManager(Duration.ofMillis(500), Duration.ofMillis(1000), false);

        long start = System.currentTimeMillis();
        String result = rm.invoke(target);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals("returned expected value", TEST_VALUE, result);
        NumericAsserts.assertInRange("did not sleep", 0, 10, elapsed);
    }


    @Test
    public void testRetryManagerLinearBackoff() throws Exception
    {
        InvokeTarget target = new InvokeTarget(null, null, null, TEST_VALUE);

        // 50, 50, 50 = 150
        RetryManager rm = new RetryManager(Duration.ofMillis(50), Duration.ofMillis(200), false);

        long start = System.currentTimeMillis();
        String result = rm.invoke(target);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals("returned expected value", TEST_VALUE, result);
        NumericAsserts.assertInRange("slept expected time", 140, 160, elapsed);
    }


    @Test
    public void testRetryManagerExponentialBackoff() throws Exception
    {
        InvokeTarget target = new InvokeTarget(null, null, null, TEST_VALUE);

        // 50, 100, 200 = 350
        RetryManager rm = new RetryManager(Duration.ofMillis(50), Duration.ofMillis(500), true);

        long start = System.currentTimeMillis();
        String result = rm.invoke(target);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals("returned expected value", TEST_VALUE, result);
        NumericAsserts.assertInRange("slept expected time", 340, 360, elapsed);
    }


    @Test
    public void testRetryManagerTimeout() throws Exception
    {
        InvokeTarget target = new InvokeTarget(null, null, null, TEST_VALUE);

        // 50, 50, 50 = 150
        RetryManager rm = new RetryManager(Duration.ofMillis(50), Duration.ofMillis(100), false);

        long start = System.currentTimeMillis();
        String result = rm.invoke(target);
        long elapsed = System.currentTimeMillis() - start;

        assertNull("returned expected value", result);
        NumericAsserts.assertInRange("slept expected time", 90, 110, elapsed);
    }


    @Test
    public void testRetryManagerUncaughtException() throws Exception
    {
        InvokeTarget target = new InvokeTarget(null, TEST_EXC);

        // one 50 ms sleep to verify that we retry
        RetryManager rm = new RetryManager(Duration.ofMillis(50), Duration.ofMillis(150), false);

        long start = System.currentTimeMillis();
        try
        {
            rm.invoke(target);
            fail("should have thrown");
        }
        catch (RuntimeException caught)
        {
            assertSame("thrown exception", TEST_EXC, caught);
        }
        long elapsed = System.currentTimeMillis() - start;

        NumericAsserts.assertInRange("slept expected time", 40, 60, elapsed);
    }


    @Test
    public void testRetryManagerHandledException() throws Exception
    {
        InvokeTarget target = new InvokeTarget(null, TEST_EXC, TEST_EXC, TEST_VALUE);
        ExceptionHandler handler = new ExceptionHandler();

        RetryManager rm = new RetryManager(Duration.ofMillis(50), Duration.ofMillis(250), false);

        long start = System.currentTimeMillis();
        String result = rm.invoke(target, handler);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals("handler received exceptions",             Arrays.asList(TEST_EXC, TEST_EXC),  handler.handledExceptions);
        assertEquals("result returned after exception handled", TEST_VALUE,                         result);
        NumericAsserts.assertInRange("slept expected time",     140, 160,                           elapsed);
    }
}
