// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.testhelpers.aws.kinesis;

import java.lang.reflect.Field;

import com.kdgregory.log4j.aws.KinesisAppender;
import com.kdgregory.log4j.aws.internal.kinesis.KinesisWriterConfig;
import com.kdgregory.log4j.aws.internal.shared.AbstractLogWriter;
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
    {
        // note: will only work with the regular KinesisLogWriter
        try
        {
            Field field = AbstractLogWriter.class.getDeclaredField("messageQueue");
            field.setAccessible(true);
            return (MessageQueue)field.get(writer);
        }
        catch (Exception ex)
        {
            throw new RuntimeException(ex);
        }
    }


    public Throwable getLastWriterException()
    {
        return lastWriterException;
    }
}
