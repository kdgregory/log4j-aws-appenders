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

package com.kdgregory.log4j.testhelpers;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static net.sf.kdgcommons.test.StringAsserts.*;

import com.kdgregory.log4j.aws.internal.Log4JInternalLogger;


/**
 *  Mocks all operations of <code>Log4JInternalLogger</code>, tracking log messages
 *  in lists.
 */
public class TestableLog4JInternalLogger
extends Log4JInternalLogger
{
    public String appenderName;

    public List<String> debugMessages = new ArrayList<String>();
    public List<String> warnMessages = new ArrayList<String>();
    public List<String> errorMessages = new ArrayList<String>();
    public List<Throwable> errorThrowables = new ArrayList<Throwable>();


    public TestableLog4JInternalLogger(String appenderName)
    {
        super(appenderName);
        this.appenderName = appenderName;
    }


    @Override
    public void setAppenderName(String value)
    {
        appenderName = value;
    }


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
        errorThrowables.add(ex);
    }


    /**
     *  Asserts that the debug log contains the specified regexes.
     */
    public void assertDebugLog(String... regexes)
    {
        assertEquals("internal debug log number of entries", regexes.length, debugMessages.size());

        for (int ii = 0 ; ii < regexes.length ; ii++)
        {
            assertRegex("internal debug log entry " + ii + " (was: " + debugMessages.get(ii) + ")",
                        regexes[ii], debugMessages.get(ii));
        }
    }


    /**
     *  Asserts that the warning log contains the specified regexes.
     */
    public void assertWarningLog(String... regexes)
    {
        assertEquals("internal warning log number of entries", regexes.length, warnMessages.size());

        for (int ii = 0 ; ii < regexes.length ; ii++)
        {
            assertRegex("internal warning log entry " + ii + " (was: " + warnMessages.get(ii) + ")",
                        regexes[ii], warnMessages.get(ii));
        }
    }


    /**
     *  Asserts that the error log contains the specified regexes.
     */
    public void assertErrorLog(String... regexes)
    {
        assertEquals("internal error log number of entries", regexes.length, errorMessages.size());

        for (int ii = 0 ; ii < regexes.length ; ii++)
        {
            assertRegex("internal error log entry " + ii + " (was: " + errorMessages.get(ii) + ")",
                        regexes[ii], errorMessages.get(ii));
        }
    }


    /**
     *  Asserts that the error log contains the specified regexes.
     */
    public void assertErrorThrowables(Class<?>... klasses)
    {
        assertEquals("internal exception log number of entries", klasses.length, errorThrowables.size());

        for (int ii = 0 ; ii < klasses.length ; ii++)
        {
            assertSame("internal error exception entry " + ii, klasses[ii], errorThrowables.get(ii).getClass());
        }
    }
}
