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

package com.kdgregory.log4j.testhelpers.aws.sns;

import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;
import com.kdgregory.log4j.aws.internal.sns.SNSAppenderStatistics;
import com.kdgregory.log4j.aws.internal.sns.SNSWriterConfig;


public class MockSNSWriterFactory
implements WriterFactory<SNSWriterConfig,SNSAppenderStatistics>
{
    public int invocationCount = 0;
    public MockSNSWriter writer;


    @Override
    public LogWriter newLogWriter(SNSWriterConfig config, SNSAppenderStatistics stats)
    {
        invocationCount++;
        writer = new MockSNSWriter(config);
        return writer;
    }
}
