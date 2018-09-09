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

package com.kdgregory.log4j.aws.internal.kinesis;

import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;


/**
 *  A factory for {@link KinesisLogWriter} instances. This is exposed for
 *  testing.
 */
public class KinesisWriterFactory implements WriterFactory<KinesisWriterConfig, KinesisAppenderStatistics>
{
    @Override
    public LogWriter newLogWriter(KinesisWriterConfig config, KinesisAppenderStatistics stats)
    {
        return new KinesisLogWriter(config, stats);
    }
}
