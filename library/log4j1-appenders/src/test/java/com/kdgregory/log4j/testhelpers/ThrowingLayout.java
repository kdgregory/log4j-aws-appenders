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

import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;

import com.kdgregory.logging.testhelpers.TestingException;


/**
 *  This layout is used to test appender error handling.
 */
public class ThrowingLayout
extends Layout
{
    @Override
    public void activateOptions()
    {
        // nothing happening here
    }

    @Override
    public String format(LoggingEvent event)
    {
        throw new TestingException("That trick never works!");
    }

    @Override
    public boolean ignoresThrowable()
    {
        return false;
    }
}
