// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.kinesis;

import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;


/**
 *  A factory for {@link KinesisLogWriter} instances. This is exposed for
 *  testing.
 */
public class KinesisWriterFactory implements WriterFactory<KinesisWriterConfig>
{
    @Override
    public LogWriter newLogWriter(KinesisWriterConfig config)
    {
        return new KinesisLogWriter(config);
    }
}
