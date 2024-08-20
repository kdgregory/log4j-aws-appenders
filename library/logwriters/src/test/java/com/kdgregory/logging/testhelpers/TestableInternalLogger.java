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

package com.kdgregory.logging.testhelpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.ThreadUtil;
import static net.sf.kdgcommons.test.StringAsserts.*;

import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  An implementation of <code>InternalLogger</code> that retains messages for
 *  analysis by test code. Thread-safe for adding messages, but not for reading.
 */
public class TestableInternalLogger
implements InternalLogger
{
    public List<String> debugMessages = new ArrayList<String>();
    public List<String> warnMessages = new ArrayList<String>();
    public List<String> errorMessages = new ArrayList<String>();

    public List<Throwable> warnExceptions = new ArrayList<Throwable>();
    public List<Throwable> errorExceptions = new ArrayList<Throwable>();


    @Override
    public synchronized void debug(String message)
    {
        debugMessages.add(message);
    }


    @Override
    public synchronized void warn(String message)
    {
        warnMessages.add(message);
        warnExceptions.add(null);
    }


    @Override
    public synchronized void warn(String message, Throwable ex)
    {
        warnMessages.add(message);
        warnExceptions.add(ex);
    }


    @Override
    public synchronized void error(String message, Throwable ex)
    {
        errorMessages.add(message);
        errorExceptions.add(ex);
    }

//----------------------------------------------------------------------------
//  These assertions exist to deal with with the possibility of a race between
//  when the log is written by the writer thread and when it's read by the
//  main (test) thread.
//
//  Note: most of these assertions wait for a minimum number of entries. There
//  may still be a race condition in which additional rows are added after the
//  assertion. This can only be resolved via test design.
//----------------------------------------------------------------------------

    /**
     *  Asserts that the debug log contains the expected number of entries,
     *  and that those entries match the expected series of regexes (if any).
     */
    public void assertInternalDebugLog(String... expectedRegexes)
    {
        assertLogBecomesExpectedSize("debug", debugMessages, expectedRegexes.length, 1000);
        assertLogMessages("debug", debugMessages, expectedRegexes);
    }


    /**
     *  Asserts that the debug log contains the expected regexes, ignoring any
     *  other lines (used to verify that a specific test's output exists).
     */
    public void assertInternalDebugLogContains(String... expectedRegexes)
    {
        List<String> testedRegexes = new ArrayList<>(Arrays.asList(expectedRegexes));
        Iterator<String> testItx = testedRegexes.iterator();
        while (testItx.hasNext())
        {
            String expected = testItx.next();
            Pattern pattern = Pattern.compile(expected);
            for (String actual : debugMessages)
            {
                if (pattern.matcher(actual).matches())
                    testItx.remove();
            }
        }
        if (testedRegexes.size() == 0)
            return;

        fail("did not find log messages: " + testedRegexes);
    }


    /**
     *  Asserts that the warning log contains the expected number of entries,
     *  and that those entries match the expected series of regexes (if any).
     */
    public void assertInternalWarningLog(String... expectedRegexes)
    {
        assertLogBecomesExpectedSize("warning", warnMessages, expectedRegexes.length, 1000);
        assertLogMessages("warning", warnMessages, expectedRegexes);
    }


    /**
     *  Asserts that the error log contains the expected number of entries.
     */
    public void assertInternalErrorLog(String... expectedRegexes)
    {
        assertLogBecomesExpectedSize("error", errorMessages, expectedRegexes.length, 1000);
        assertLogMessages("error", errorMessages, expectedRegexes);
    }

    /**
     *  Asserts that the internal error log contains the expected number and type of
     *  throwable. Assumes that the list has been fully populated; should only call
     *  after verifying that log is up-to-date.
     *  <p>
     *  Note: additional rows may be added after the assertion is made.
     */
    public void assertInternalErrorLogExceptionTypes(Class<?>... expectedTypes)
    {
        assertThrowables("error", errorExceptions, expectedTypes);
    }

//-----------------------------------------------------------------------------
//  Common assertion functions, available for other InternalLog implementations
//-----------------------------------------------------------------------------

    /**
     *  Waits for a list to reach an expected size, failing if it does not reach
     *  that size within a given timeout. This function can be used with lists
     *  that do not intentionally support concurrent access, as long as the
     *  <code>size()</code> function does not depend on consistent internal state.
     */
    public static void assertLogBecomesExpectedSize(String logName, List<String> logBuffer, int expectedSize, long timeoutMs)
    {
        int actualSize = 0;
        long timeoutAt = System.currentTimeMillis() + timeoutMs;

        while (System.currentTimeMillis() < timeoutAt)
        {
            actualSize = logBuffer.size();
            if (actualSize == expectedSize)
                break;
            ThreadUtil.sleepQuietly(10);
        }

        assertEquals("internal " + logName + " log number of entries", expectedSize, actualSize);
    }


    /**
     *  Shared method to compare a log buffer to a (possibly empty) list of regexes.
     *  This assumes that the log buffer has the correct size, so should be called
     *  after {@link #assertLogBecomesExpectedSize} in a multi-threaded test..
     */
    public static void assertLogMessages(String logName, List<String> logBuffer, String[] expectedRegexes)
    {
        assertEquals("internal " + logName + " log size", expectedRegexes.length, logBuffer.size());

        for (int ii = 0 ; ii < expectedRegexes.length ; ii++)
        {
            // we know that buffers are ArrayLists, so List.get() isn't a concern
            String actual = logBuffer.get(ii);
            assertRegex("internal " + logName + " log entry " + ii + " (was: " + actual + ")", expectedRegexes[ii], actual);
        }
    }


    /**
     *  Shared method to compare a list of exceptions to those that are expected.
     */
    public static void assertThrowables(String logName, List<Throwable> logBuffer, Class<?>[] expectedTypes)
    {
        assertEquals("internal " + logName + " throwables size", expectedTypes.length, logBuffer.size());

        for (int ii = 0 ; ii < expectedTypes.length ; ii++)
        {
            // we know that buffers are ArrayLists, so List.get() isn't a concern
            Throwable throwable = logBuffer.get(ii);
            Class<?> actualType = (throwable == null) ? null : throwable.getClass();
            assertSame("internal " + logName + " exception " + ii, expectedTypes[ii], actualType);
        }
    }
}
