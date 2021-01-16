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

package com.kdgregory.log4j2.testhelpers;

import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Node;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;

import com.kdgregory.logging.testhelpers.TestingException;


/**
 *  This layout is used to test appender error handling.
 */
@Plugin(name = "ThrowingLayout", category = Node.CATEGORY, elementType = Layout.ELEMENT_TYPE, printObject = true)
public class ThrowingLayout
extends AbstractStringLayout
{
    @PluginFactory
    public static ThrowingLayout createLayout() {
        return new ThrowingLayout();
    }


    public ThrowingLayout()
    {
        super(StandardCharsets.UTF_8);
    }


    @Override
    public String toSerializable(LogEvent event)
    {
        throw new TestingException("That trick never works!");
    }
}
