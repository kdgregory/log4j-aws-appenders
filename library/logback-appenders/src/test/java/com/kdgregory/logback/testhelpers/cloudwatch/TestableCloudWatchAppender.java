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

package com.kdgregory.logback.testhelpers.cloudwatch;

import java.util.concurrent.atomic.AtomicInteger;

import com.kdgregory.logback.aws.CloudWatchAppender;
import com.kdgregory.logback.testhelpers.TestableLogbackInternalLogger;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.util.DefaultThreadFactory;
import com.kdgregory.logging.common.util.ThreadFactory;
import com.kdgregory.logging.common.util.WriterFactory;
import com.kdgregory.logging.testhelpers.InlineThreadFactory;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchWriter;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchWriterFactory;

import ch.qos.logback.classic.spi.ILoggingEvent;


/**
 *  This class provides visibility into the protected variables held by
 *  CloudWatchAppender and AbstractAppender. It also updates the factories
 *  so that we don't get a real writer.
 */
public class TestableCloudWatchAppender
extends CloudWatchAppender<ILoggingEvent>
{
    public AtomicInteger appendInvocationCount = new AtomicInteger();


    public TestableCloudWatchAppender()
    {
        super();
        setThreadFactory(new InlineThreadFactory());
        setWriterFactory(new MockCloudWatchWriterFactory());
        internalLogger = new TestableLogbackInternalLogger(this);
    }


    // since Logback initializes when the appender is created, we can't switch thread factories
    // after the fact; as a work-around, this configuration parameter will use the default
    public void setUseDefaultThreadFactory(boolean enabled)
    {
        if (enabled)
        {
            setThreadFactory(new DefaultThreadFactory("test"));
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


    public TestableLogbackInternalLogger getInternalLogger()
    {
        return (TestableLogbackInternalLogger)internalLogger;
    }


    @Override
    protected void append(ILoggingEvent event)
    {
        appendInvocationCount.incrementAndGet();
        super.append(event);
    }
}
