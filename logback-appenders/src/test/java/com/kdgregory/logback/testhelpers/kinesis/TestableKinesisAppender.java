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

package com.kdgregory.logback.testhelpers.kinesis;

import com.kdgregory.logging.aws.kinesis.KinesisWriterStatistics;
import com.kdgregory.logback.aws.KinesisAppender;
import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.factories.ThreadFactory;
import com.kdgregory.logging.common.factories.WriterFactory;
import com.kdgregory.logging.testhelpers.InlineThreadFactory;
import com.kdgregory.logging.testhelpers.kinesis.MockKinesisWriter;
import com.kdgregory.logging.testhelpers.kinesis.MockKinesisWriterFactory;

import ch.qos.logback.classic.spi.ILoggingEvent;


/**
 *  This class provides visibility into the protected variables held by
 *  KinesisAppender and AbstractAppender. It also updates the factories
 *  so that we don't get a real writer.
 */
public class TestableKinesisAppender
extends KinesisAppender<ILoggingEvent>
{
    public TestableKinesisAppender()
    {
        super();
        setThreadFactory(new InlineThreadFactory());
        setWriterFactory(new MockKinesisWriterFactory());
    }

    public void setThreadFactory(ThreadFactory threadFactory)
    {
        this.threadFactory = threadFactory;
    }


    public void setWriterFactory(WriterFactory<KinesisWriterConfig, KinesisWriterStatistics> writerFactory)
    {
        this.writerFactory = writerFactory;
    }


    public MockKinesisWriterFactory getWriterFactory()
    {
        return (MockKinesisWriterFactory)writerFactory;
    }


    public LogWriter getWriter()
    {
        return writer;
    }


    public MockKinesisWriter getMockWriter()
    {
        return (MockKinesisWriter)writer;
    }
}
