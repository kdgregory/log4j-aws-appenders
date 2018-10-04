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

package com.kdgregory.aws.logging.sns;

import com.amazonaws.services.sns.AmazonSNS;

import com.kdgregory.aws.logging.common.DefaultClientFactory;
import com.kdgregory.aws.logging.common.LogWriter;
import com.kdgregory.aws.logging.common.WriterFactory;
import com.kdgregory.aws.logging.internal.InternalLogger;

/**
 *  Factory to create <code>SNSLogWriter</code> instances.
 */
public class SNSWriterFactory
implements WriterFactory<SNSWriterConfig,SNSAppenderStatistics>
{
    @Override
    public LogWriter newLogWriter(SNSWriterConfig config, SNSAppenderStatistics stats, InternalLogger logger)
    {
        return new SNSLogWriter(
                config, stats, logger,
                new DefaultClientFactory<AmazonSNS>(AmazonSNS.class, config.clientFactoryMethod, config.clientEndpoint, logger));
    }
}
