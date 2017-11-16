// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.testhelpers.aws.sns;

import java.lang.reflect.Field;

import com.kdgregory.log4j.aws.SNSAppender;
import com.kdgregory.log4j.aws.internal.shared.AbstractLogWriter;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.MessageQueue;
import com.kdgregory.log4j.aws.internal.shared.ThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;
import com.kdgregory.log4j.aws.internal.sns.SNSWriterConfig;


/**
 *  This class provides visibility into the protected variables held by
 *  SNSAppender and AbstractAppender.
 */
public class TestableSNSAppender
extends SNSAppender
{

    public void setThreadFactory(ThreadFactory threadFactory)
    {
        this.threadFactory = threadFactory;
    }


    public void setWriterFactory(WriterFactory<SNSWriterConfig> writerFactory)
    {
        this.writerFactory = writerFactory;
    }


    public MockSNSWriterFactory getWriterFactory()
    {
        return (MockSNSWriterFactory)writerFactory;
    }


    public MockSNSWriter getWriter()
    {
        return (MockSNSWriter)writer;
    }


    public MessageQueue getMessageQueue()
    {
        // note: will only work with the regular CloudWatchLogWriter
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

    public void updateLastRotationTimestamp(long offset)
    {
        lastRotationTimestamp += offset;
    }


    public long getLastRotationTimestamp()
    {
        return lastRotationTimestamp;
    }


    @Override
    public boolean isMessageTooLarge(LogMessage message)
    {
        return super.isMessageTooLarge(message);
    }
}
