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

import com.kdgregory.logging.testhelpers.TestingException;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.LayoutBase;


/**
 *  This layout is used to test appender error handling.
 */
public class ThrowingLayout
extends LayoutBase<ILoggingEvent>
{
    @Override
    public String doLayout(ILoggingEvent event)
    {
        throw new TestingException("That trick never works!");
    }
}
