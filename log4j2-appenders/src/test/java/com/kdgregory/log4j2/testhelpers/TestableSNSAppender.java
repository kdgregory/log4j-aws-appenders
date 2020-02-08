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
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;

import com.kdgregory.log4j2.aws.SNSAppender;
import com.kdgregory.log4j2.aws.internal.SNSAppenderConfig;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;
import com.kdgregory.logging.aws.sns.SNSWriterStatistics;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.factories.DefaultThreadFactory;
import com.kdgregory.logging.common.factories.ThreadFactory;
import com.kdgregory.logging.common.factories.WriterFactory;
import com.kdgregory.logging.testhelpers.InlineThreadFactory;
import com.kdgregory.logging.testhelpers.sns.MockSNSWriter;
import com.kdgregory.logging.testhelpers.sns.MockSNSWriterFactory;


@Plugin(name = "TestableSNSAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class TestableSNSAppender
extends SNSAppender
{

//----------------------------------------------------------------------------
//  Plugin integration
//----------------------------------------------------------------------------

    @PluginBuilderFactory
    public static TestableSNSAppenderBuilder newBuilder()
    {
        return new TestableSNSAppenderBuilder();
    }


    public static class TestableSNSAppenderBuilder
    extends SNSAppenderBuilder
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
        public TestableSNSAppender build()
        {
            return new TestableSNSAppender(this, useDefaultThreadFactory);
        }
    }


//----------------------------------------------------------------------------
//  Constructor and hooks
//----------------------------------------------------------------------------

    protected TestableSNSAppender(SNSAppenderConfig config, boolean useDefaultThreadFactory)
    {
        super(config.getName(), config, new TestableLog4J2InternalLogger());
        setWriterFactory(new MockSNSWriterFactory());
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


    public void setWriterFactory(WriterFactory<SNSWriterConfig,SNSWriterStatistics> writerFactory)
    {
        this.writerFactory = writerFactory;
    }

//----------------------------------------------------------------------------
//  Accessors
//----------------------------------------------------------------------------

    public MockSNSWriterFactory getWriterFactory()
    {
        return (MockSNSWriterFactory)writerFactory;
    }


    public LogWriter getWriter()
    {
        return writer;
    }


    // a convenience function so that we're not always casting
    public MockSNSWriter getMockWriter()
    {
        return (MockSNSWriter)writer;
    }


    public TestableLog4J2InternalLogger getInternalLogger()
    {
        return (TestableLog4J2InternalLogger)internalLogger;
    }

}
