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

import net.sf.kdgcommons.lang.ClassUtil;

import com.kdgregory.log4j.aws.KinesisAppender;
import com.kdgregory.log4j.aws.internal.kinesis.KinesisWriterConfig;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.MessageQueue;
import com.kdgregory.log4j.aws.internal.shared.ThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;


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


    public void setWriterFactory(WriterFactory<KinesisWriterConfig> writerFactory)
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


    public MessageQueue getMessageQueue()
    throws NoSuchFieldException
    {
        return ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);
    }


    public Throwable getLastWriterException()
    {
        return lastWriterException;
    }
}
