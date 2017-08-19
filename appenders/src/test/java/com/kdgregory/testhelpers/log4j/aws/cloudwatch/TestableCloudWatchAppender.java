// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.testhelpers.log4j.aws.cloudwatch;

import com.kdgregory.log4j.aws.CloudWatchAppender;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.ThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;


/**
 *  This class provides visibility into the protected variables held by
 *  CloudWatchAppender and AbstractAppender.
 */
public class TestableCloudWatchAppender extends CloudWatchAppender
{

    public void setThreadFactory(ThreadFactory threadFactory)
    {
        this.threadFactory = threadFactory;
    }


    public void setWriterFactory(WriterFactory writerFactory)
    {
        this.writerFactory = writerFactory;
    }


    // this is used when we just want to see if the factory has been set
    public WriterFactory getWriterFactory()
    {
        return writerFactory;
    }


    // this is called most often, so that we can examine what's written
    public MockWriterFactory getMockWriterFactory()
    {
        return (MockWriterFactory)writerFactory;
    }


    // called when we don't care about the writer
    public LogWriter getWriter()
    {
        return writer;
    }


    // and when we do
    public MockCloudWatchWriter getMockWriter()
    {
        return (MockCloudWatchWriter)writer;
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
}
