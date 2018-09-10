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

package com.kdgregory.aws.logwriters.cloudwatch;

import com.kdgregory.aws.logwriters.common.LogWriter;
import com.kdgregory.aws.logwriters.internal.WriterFactory;

/**
 *  Factory to create CloudWatchLogWriter instances.
 */
public class CloudWatchWriterFactory
implements WriterFactory<CloudWatchWriterConfig,CloudWatchAppenderStatistics>
{
    @Override
    public LogWriter newLogWriter(CloudWatchWriterConfig config, CloudWatchAppenderStatistics stats)
    {
        return new CloudWatchLogWriter(config, stats);
    }
}
