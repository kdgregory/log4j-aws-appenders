// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.testhelpers.log4j.aws.cloudwatch;

import com.kdgregory.log4j.aws.CloudWatchAppender;
import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;


public class MockCloudWatchWriterFactory implements WriterFactory<CloudWatchWriterConfig>
{
    public CloudWatchAppender appender;

    public int invocationCount = 0;
    public MockCloudWatchWriter writer;


    public MockCloudWatchWriterFactory(CloudWatchAppender appender)
    {
        this.appender = appender;
    }


    @Override
    public LogWriter newLogWriter(CloudWatchWriterConfig config)
    {
        invocationCount++;
        writer = new MockCloudWatchWriter(config);
        return writer;
    }
}