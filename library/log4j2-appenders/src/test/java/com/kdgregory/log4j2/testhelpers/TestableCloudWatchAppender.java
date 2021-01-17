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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;

import com.kdgregory.log4j2.aws.CloudWatchAppender;
import com.kdgregory.log4j2.aws.internal.CloudWatchAppenderConfig;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.util.MessageQueue.DiscardAction;
import com.kdgregory.logging.common.util.DefaultThreadFactory;
import com.kdgregory.logging.common.util.ThreadFactory;
import com.kdgregory.logging.common.util.WriterFactory;
import com.kdgregory.logging.testhelpers.InlineThreadFactory;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchWriter;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchWriterFactory;


/**
 *  This class provides visibility into the protected variables held by
 *  CloudWatchAppender and AbstractAppender. It also updates the factories
 *  so that we don't get a real writer.
 */
@Plugin(name = "TestableCloudWatchAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class TestableCloudWatchAppender
extends CloudWatchAppender
{
//----------------------------------------------------------------------------
//  Plugin integration
//----------------------------------------------------------------------------

    @PluginBuilderFactory
    public static TestableCloudWatchAppenderBuilder newBuilder() {
        return new TestableCloudWatchAppenderBuilder();
    }

    public static class TestableCloudWatchAppenderBuilder
    extends CloudWatchAppenderBuilder
    {
        // since Log4J2 initializes when the appender is created, we can't switch thread factories
        // after the fact; as a work-around, this configuration parameter will use the default
        @PluginBuilderAttribute("useDefaultThreadFactory")
        private boolean useDefaultThreadFactory;

        public void setUseDefaultThreadFactory(boolean value)
        {
            this.useDefaultThreadFactory = value;
        }

        @Override
        public TestableCloudWatchAppender build()
        {
            return new TestableCloudWatchAppender(getName(), this, useDefaultThreadFactory);
        }
    }

//----------------------------------------------------------------------------
//  Constructor and hooks
//----------------------------------------------------------------------------

    public AtomicInteger appendInvocationCount = new AtomicInteger();

    protected TestableCloudWatchAppender(String name, CloudWatchAppenderConfig config, boolean useDefaultThreadFactory)
    {
        super(name, config, new TestableLog4J2InternalLogger());
        setWriterFactory(new MockCloudWatchWriterFactory());
        if (useDefaultThreadFactory)
        {
            setThreadFactory(new DefaultThreadFactory("test"));
        }
        else
        {
            setThreadFactory(new InlineThreadFactory());
        }
    }


    public void setThreadFactory(ThreadFactory threadFactory)
    {
        this.threadFactory = threadFactory;
    }


    public void setWriterFactory(WriterFactory<CloudWatchWriterConfig,CloudWatchWriterStatistics> writerFactory)
    {
        this.writerFactory = writerFactory;
    }

//----------------------------------------------------------------------------
//  Appender overrides
//----------------------------------------------------------------------------

    @Override
    public void append(LogEvent event)
    {
        appendInvocationCount.incrementAndGet();
        super.append(event);
    }

//----------------------------------------------------------------------------
//  Accessors
//----------------------------------------------------------------------------

    public MockCloudWatchWriterFactory getWriterFactory()
    {
        return (MockCloudWatchWriterFactory)writerFactory;
    }


    public LogWriter getWriter()
    {
        return writer;
    }


    // a convenience function so that we're not always casting
    public MockCloudWatchWriter getMockWriter()
    {
        return (MockCloudWatchWriter)writer;
    }


    public TestableLog4J2InternalLogger getInternalLogger()
    {
        return (TestableLog4J2InternalLogger)internalLogger;
    }


    public DiscardAction getDiscardAction()
    {
        return discardAction;
    }


    public Integer getRetentionPeriod()
    {
        return retentionPeriod;
    }


    public int getAppendInvocationCount()
    {
        return appendInvocationCount.get();
    }
}
