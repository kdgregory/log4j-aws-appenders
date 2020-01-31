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

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;

import com.kdgregory.log4j2.aws.internal.CloudWatchAppenderConfig;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.testhelpers.ThrowingWriterFactory;


/**
 *  This class is used to test uncaught exception handling.
 */
@Plugin(name = "ThrowingWriterCloudWatchAppender", category = "core", elementType = Appender.ELEMENT_TYPE)
public class ThrowingWriterCloudWatchAppender
extends TestableCloudWatchAppender
{
    @PluginBuilderFactory
    public static ThrowingWriterCloudWatchAppenderBuilder newBuilder() {
        return new ThrowingWriterCloudWatchAppenderBuilder();
    }

    
    public static class ThrowingWriterCloudWatchAppenderBuilder
    extends TestableCloudWatchAppenderBuilder
    {
        @Override
        public ThrowingWriterCloudWatchAppender build()
        {
            return new ThrowingWriterCloudWatchAppender(getName(), this);
        }
    }
    
    
    private ThrowingWriterCloudWatchAppender(String name, CloudWatchAppenderConfig config)
    {
        super(name, config, true);
        setWriterFactory(new ThrowingWriterFactory<CloudWatchWriterConfig,CloudWatchWriterStatistics>());
    }
}
