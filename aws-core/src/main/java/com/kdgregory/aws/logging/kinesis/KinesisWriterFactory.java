// Copyright (c) Keith D Gregory
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.kdgregory.aws.logging.kinesis;

import com.kdgregory.aws.logging.common.LogWriter;
import com.kdgregory.aws.logging.common.WriterFactory;
import com.kdgregory.aws.logging.internal.InternalLogger;


/**
 *  A factory for {@link KinesisLogWriter} instances. This is exposed for
 *  testing.
 */
public class KinesisWriterFactory implements WriterFactory<KinesisWriterConfig, KinesisAppenderStatistics>
{
    private InternalLogger internalLogger;


    public KinesisWriterFactory(InternalLogger internalLogger)
    {
        this.internalLogger = internalLogger;
    }


    @Override
    public LogWriter newLogWriter(KinesisWriterConfig config, KinesisAppenderStatistics stats)
    {
        return new KinesisLogWriter(config, stats, internalLogger);
    }
}
