// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.testhelpers.log4j.aws.kinesis;

import com.kdgregory.log4j.aws.KinesisAppender;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;


public class MockWriterFactory implements WriterFactory
{
    public KinesisAppender appender;

    public int invocationCount = 0;
    public String lastStreamName;
    public String lastPartitionKey;
    public MockKinesisWriter writer;

    public MockWriterFactory(KinesisAppender appender)
    {
        this.appender = appender;
    }


    @Override
    public LogWriter newLogWriter()
    {
        invocationCount++;
        lastStreamName = appender.getActualStreamName();
        lastPartitionKey = appender.getActualPartitionKey();
        writer = new MockKinesisWriter();
        return writer;
    }
}