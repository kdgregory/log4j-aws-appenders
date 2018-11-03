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
import java.util.List;

import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.ThreadUtil;
import static net.sf.kdgcommons.test.StringAsserts.*;

import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  An implementation of <code>InternalLogger</code> that retains messages for
 *  analysis by test code.
 */
public class TestableInternalLogger
implements InternalLogger
{
    private List<String> debugMessages = new ArrayList<String>();
    private List<String> warnMessages = new ArrayList<String>();
    private List<String> errorMessages = new ArrayList<String>();
    private List<Throwable> errorExceptions = new ArrayList<Throwable>();


    @Override
    public void debug(String message)
    {
        debugMessages.add(message);
    }



    @Override
    public void warn(String message)
    {
        warnMessages.add(message);
    }



    @Override
    public void error(String message, Throwable ex)
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
        assertInternalLogContentsExpectedSize("debug", debugMessages, expectedRegexes.length);
        assertInternalLogContents("debug", debugMessages, expectedRegexes);
    }


    /**
     *  Asserts that the warning log contains the expected number of entries,
     *  and that those entries match the expected series of regexes (if any).
     */
    public void assertInternalWarningLog(String... expectedRegexes)
    {
        assertInternalLogContentsExpectedSize("warning", warnMessages, expectedRegexes.length);
        assertInternalLogContents("warning", warnMessages, expectedRegexes);
    }


    /**
     *  Asserts that the error log contains the expected number of entries.
     */
    public void assertInternalErrorLog(String... expectedRegexes)
    {
        assertInternalLogContentsExpectedSize("error", errorMessages, expectedRegexes.length);
        assertInternalLogContents("error", errorMessages, expectedRegexes);
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
        for (int ii = 0 ; ii < expectedTypes.length ; ii++)
        {
            Throwable actualThrowable = errorExceptions.get(ii);
            Class<?> actualType = (actualThrowable == null) ? null : actualThrowable.getClass();
            assertEquals("internal error log throwable " + ii, expectedTypes[ii], actualType);
        }
    }


    /**
     *  Shared method to wait for an log list to reach an expected size.
     */
    private void assertInternalLogContentsExpectedSize(String bufferName, List<String> logBuffer, int expectedSize)
    {
        int actualSize = 0;
        for (int ii = 0 ; ii < 10 ; ii++)
        {
            actualSize = logBuffer.size();
            if (actualSize == expectedSize)
                break;
            ThreadUtil.sleepQuietly(10);
        }

        assertEquals("internal " + bufferName + "log number of entries", expectedSize, actualSize);
    }


    /**
     *  Shared method to compare a log buffer to a (possibly empty) list of regexes.
     *  This assumes that the log buffer has the correct size, so should be called
     *  after {@link #assertInternalLogContentsExpectedSize}.
     */
    private void assertInternalLogContents(String bufferName, List<String> logBuffer, String[] regexes)
    {
        for (int ii = 0 ; ii < regexes.length ; ii++)
        {
            // we know that buffers are ArrayLists, so List.get() isn't a concern
            String actual = logBuffer.get(ii);
            assertRegex("internal " + bufferName + " log entry " + ii + " (was: " + actual + ")", regexes[ii], actual);
        }
    }
}
