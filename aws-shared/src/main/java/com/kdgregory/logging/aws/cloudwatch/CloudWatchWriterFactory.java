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

package com.kdgregory.logging.aws.cloudwatch;

import com.amazonaws.services.logs.AWSLogs;

import com.kdgregory.logging.aws.common.DefaultClientFactory;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.factories.WriterFactory;
import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  Factory to create <code>CloudWatchLogWriter</code> instances.
 */
public class CloudWatchWriterFactory
implements WriterFactory<CloudWatchWriterConfig,CloudWatchWriterStatistics>
{
    @Override
    public LogWriter newLogWriter(CloudWatchWriterConfig config, CloudWatchWriterStatistics stats, InternalLogger logger)
    {
        return new CloudWatchLogWriter(
                config, stats, logger,
                new DefaultClientFactory<AWSLogs>(AWSLogs.class, config.clientFactoryMethod, config.clientEndpoint, logger));
    }
}
