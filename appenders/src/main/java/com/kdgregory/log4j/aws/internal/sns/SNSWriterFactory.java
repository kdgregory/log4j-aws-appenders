// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.sns;

import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;


public class SNSWriterFactory
implements WriterFactory<SNSWriterConfig>
{
    @Override
    public LogWriter newLogWriter(SNSWriterConfig config)
    {
        return new SNSLogWriter(config);
    }
}
