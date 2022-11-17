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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.test.NumericAsserts;

import com.kdgregory.logging.common.util.RetryManager2;
import com.kdgregory.logging.common.util.RetryManager2.TimeoutException;


public class TestRetryManager2
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
//
//  Most of the assertions in these testcases are based on elapsed time. This
//  depends on the basic performance of the processor and load on the system.
//  As a result, all expected times are measured with relatively wide ranges.
//
//  It's still possible, however, that a test may fail. The best solution in
//  this situation is to rerun it. If it consistently fails, verify that the
//  actual time is reasonable, and increase the expected time if necessary.
//----------------------------------------------------------------------------

    @Test
    public void testSleepQuietly() throws Exception
    {
        long start = System.currentTimeMillis();
        boolean result =  RetryManager2.sleepQuietly(50);
        long elapsed = System.currentTimeMillis() - start;

        assertTrue("returned normally", result);
        NumericAsserts.assertInRange("slept for expected time", 40, 70, elapsed);
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
        boolean result =  RetryManager2.sleepQuietly(150);
        long elapsed = System.currentTimeMillis() - start;

        assertFalse("returned abnormally", result);

        // we don't know how long it will take to start a thread, but we know
        // the minimum expected time before interrupt, and will assert something
        // less than the desired sleep time
        NumericAsserts.assertInRange("sleep was cut short", 50, 130, elapsed);
    }


    @Test
    public void testRetryManagerImmediateReturn() throws Exception
    {
        InvokeTarget target = new InvokeTarget(TEST_VALUE);
        RetryManager2 rm = new RetryManager2("test", Duration.ofMillis(500), false, false);     // long sleep time == there was a problem

        long start = System.currentTimeMillis();
        String result = rm.invoke(Instant.now().plusMillis(400), target);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals("returned expected value", TEST_VALUE, result);
        NumericAsserts.assertInRange("did not sleep", 0, 50, elapsed);      // upper bound driven by slow processors; still well below timeout value
    }


    @Test
    public void testRetryManagerLinearBackoff() throws Exception
    {
        InvokeTarget target = new InvokeTarget(null, null, null, TEST_VALUE);
        RetryManager2 rm = new RetryManager2("test", Duration.ofMillis(50), false, false);

        long start = System.currentTimeMillis();
        String result = rm.invoke(Instant.now().plusMillis(200), target);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals("returned expected value", TEST_VALUE, result);

        // 50 + 50 + 50 = 150
        NumericAsserts.assertInRange("slept expected time", 140, 170, elapsed);
    }


    @Test
    public void testRetryManagerExponentialBackoff() throws Exception
    {
        InvokeTarget target = new InvokeTarget(null, null, null, TEST_VALUE);
        RetryManager2 rm = new RetryManager2("test", Duration.ofMillis(50), true, false);

        long start = System.currentTimeMillis();
        String result = rm.invoke(Instant.now().plusMillis(500), target);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals("returned expected value", TEST_VALUE, result);

        // 50, 100, 200 = 350
        NumericAsserts.assertInRange("slept expected time", 340, 370, elapsed);
    }


    @Test
    public void testRetryManagerTimeoutThrow() throws Exception
    {
        InvokeTarget target = new InvokeTarget(null, null, null, TEST_VALUE);
        RetryManager2 rm = new RetryManager2("myOperation", Duration.ofMillis(50), false, true);

        long start = System.currentTimeMillis();
        Instant expectedFinish = Instant.now().plusMillis(90);
        try
        {
            rm.invoke(expectedFinish, target);
            fail("expected RetryManager to throw");
        }
        catch (TimeoutException ex)
        {
            Instant now = Instant.now();
            long retryTime = ex.getActualTimeout().toEpochMilli() - start;
            assertEquals("exception contains expected timeout", expectedFinish, ex.getExpectedTimeout());
            assertNotNull("exception contains actual timeout", ex.getActualTimeout());
            assertTrue("actual timeout >= expected (was + " + ex.getActualTimeout() + " versus " + expectedFinish + ")",
                       ex.getActualTimeout().compareTo(expectedFinish) >= 0);
            assertTrue("current instant >= expected finish ( is " + now + " versus " + expectedFinish + ")",
                       now.compareTo(expectedFinish) >= 0);
            NumericAsserts.assertInRange("waited expected time", 90, 150, retryTime);    // should be ~100 millis
            assertTrue("exception message contains operation name (was: " + ex.getMessage() + ")",
                       ex.getMessage().contains("myOperation"));
            assertTrue("exception message contains expected timeut (was: " + ex.getMessage() + ")",
                       ex.getMessage().contains(expectedFinish.toString()));
            assertTrue("exception message contains actual timeut (was: " + ex.getMessage() + ")",
                       ex.getMessage().contains(ex.getActualTimeout().toString()));
        }
    }


    @Test
    public void testRetryManagerTimeoutNoThrow() throws Exception
    {
        InvokeTarget target = new InvokeTarget(null, null, null, TEST_VALUE);

        // should retry twice
        RetryManager2 rm = new RetryManager2("test", Duration.ofMillis(50), false, false);

        long start = System.currentTimeMillis();
        String result = rm.invoke(Instant.now().plusMillis(100), target);
        long elapsed = System.currentTimeMillis() - start;

        assertNull("returned expected value", result);
        NumericAsserts.assertInRange("slept expected time", 90, 130, elapsed);
    }


    @Test
    public void testRetryManagerUncaughtException() throws Exception
    {
        InvokeTarget target = new InvokeTarget(null, TEST_EXC);
        RetryManager2 rm = new RetryManager2("test", Duration.ofMillis(50), false, false);

        long start = System.currentTimeMillis();
        try
        {
            // one 50 ms sleep to verify that we retry
            rm.invoke(Instant.now().plusMillis(150), target);
            fail("should have thrown");
        }
        catch (RuntimeException caught)
        {
            assertSame("thrown exception", TEST_EXC, caught);
        }
        long elapsed = System.currentTimeMillis() - start;

        NumericAsserts.assertInRange("slept expected time", 40, 70, elapsed);
    }


    @Test
    public void testRetryManagerHandledException() throws Exception
    {
        InvokeTarget target = new InvokeTarget(null, TEST_EXC, TEST_EXC, TEST_VALUE);
        ExceptionHandler handler = new ExceptionHandler();

        RetryManager2 rm = new RetryManager2("test", Duration.ofMillis(50), false, false);

        long start = System.currentTimeMillis();
        String result = rm.invoke(Instant.now().plusMillis(250), target, handler);
        long elapsed = System.currentTimeMillis() - start;

        assertEquals("handler received exceptions",             Arrays.asList(TEST_EXC, TEST_EXC),  handler.handledExceptions);
        assertEquals("result returned after exception handled", TEST_VALUE,                         result);
        NumericAsserts.assertInRange("slept expected time",     140, 170,                           elapsed);
    }
}