// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.testhelpers.aws.kinesis;

import com.kdgregory.log4j.aws.KinesisAppender;
import com.kdgregory.log4j.aws.internal.kinesis.KinesisWriterConfig;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;


public class MockKinesisWriterFactory implements WriterFactory<KinesisWriterConfig>
{
    public KinesisAppender appender;

    public int invocationCount = 0;
    public MockKinesisWriter writer;


    public MockKinesisWriterFactory(KinesisAppender appender)
    {
        this.appender = appender;
    }


    @Override
    public LogWriter newLogWriter(KinesisWriterConfig config)
    {
        invocationCount++;
        writer = new MockKinesisWriter(config);
        return writer;
    }
}
