// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.testhelpers.log4j.aws.cloudwatch;

import com.kdgregory.log4j.aws.CloudWatchAppender;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;

public class MockWriterFactory implements WriterFactory
{
    public CloudWatchAppender appender;

    public int invocationCount = 0;
    public String lastLogGroupName;
    public String lastLogStreamName;
    public MockCloudWatchWriter writer;

    public MockWriterFactory(CloudWatchAppender appender)
    {
        this.appender = appender;
    }


    @Override
    public LogWriter newLogWriter()
    {
        invocationCount++;
        lastLogGroupName = appender.getActualLogGroup();
        lastLogStreamName = appender.getActualLogStream();
        writer = new MockCloudWatchWriter();
        return writer;
    }
}