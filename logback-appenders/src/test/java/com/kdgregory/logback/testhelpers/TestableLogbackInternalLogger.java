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

package com.kdgregory.logback.testhelpers;

import java.util.ArrayList;
import java.util.List;

import com.kdgregory.logback.aws.internal.LogbackInternalLogger;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;

import ch.qos.logback.core.spi.ContextAware;


/**
 *  Mocks all operations of <code>LogbackInternalLogger</code>, tracking log messages
 *  in lists.
 */
public class TestableLogbackInternalLogger
extends LogbackInternalLogger
{
    public String appenderName;

    public List<String> debugMessages = new ArrayList<String>();
    public List<String> warnMessages = new ArrayList<String>();
    public List<String> errorMessages = new ArrayList<String>();
    public List<Throwable> errorThrowables = new ArrayList<Throwable>();


    public TestableLogbackInternalLogger(ContextAware destination)
    {
        super(destination);
        appenderName = destination.toString();
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
        TestableInternalLogger.assertLogMessages("debug", debugMessages, regexes);
    }


    /**
     *  Asserts that the warning log contains the specified regexes.
     */
    public void assertWarningLog(String... regexes)
    {
        TestableInternalLogger.assertLogMessages("warning", warnMessages, regexes);
    }


    /**
     *  Asserts that the error log contains the specified regexes.
     */
    public void assertErrorLog(String... regexes)
    {
        TestableInternalLogger.assertLogMessages("error", errorMessages, regexes);
    }


    /**
     *  Asserts that the error log contains the specified regexes.
     */
    public void assertErrorThrowables(Class<?>... klasses)
    {
        TestableInternalLogger.assertThrowables("error", errorThrowables, klasses);
    }
}
