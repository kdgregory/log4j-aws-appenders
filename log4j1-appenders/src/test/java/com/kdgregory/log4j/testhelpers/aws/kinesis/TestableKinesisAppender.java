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

package com.kdgregory.log4j.testhelpers.aws.kinesis;

import com.kdgregory.aws.logging.common.LogWriter;
import com.kdgregory.aws.logging.common.ThreadFactory;
import com.kdgregory.aws.logging.common.WriterFactory;
import com.kdgregory.aws.logging.kinesis.KinesisAppenderStatistics;
import com.kdgregory.aws.logging.kinesis.KinesisWriterConfig;
import com.kdgregory.aws.logging.testhelpers.kinesis.MockKinesisWriter;
import com.kdgregory.log4j.aws.KinesisAppender;


/**
 *  This class provides visibility into the protected variables held by
 *  KinesisAppender and AbstractAppender.
 */
public class TestableKinesisAppender extends KinesisAppender
{

    public void setThreadFactory(ThreadFactory threadFactory)
    {
        this.threadFactory = threadFactory;
    }


    public void setWriterFactory(WriterFactory<KinesisWriterConfig, KinesisAppenderStatistics> writerFactory)
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