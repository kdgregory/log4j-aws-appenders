// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.testhelpers.aws.sns;

import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;
import com.kdgregory.log4j.aws.internal.sns.SNSWriterConfig;


public class MockSNSWriterFactory
implements WriterFactory<SNSWriterConfig>
{
    public int invocationCount = 0;
    public MockSNSWriter writer;


    @Override
    public LogWriter newLogWriter(SNSWriterConfig config)
    {
        invocationCount++;
        writer = new MockSNSWriter(config);
        return writer;
    }
}
