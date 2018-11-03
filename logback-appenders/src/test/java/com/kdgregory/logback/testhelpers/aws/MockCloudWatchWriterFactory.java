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

package com.kdgregory.logback.testhelpers.aws;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.testhelpers.cloudwatch.MockCloudWatchWriter;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.factories.WriterFactory;
import com.kdgregory.logging.common.util.InternalLogger;


public class MockCloudWatchWriterFactory
implements WriterFactory<CloudWatchWriterConfig,CloudWatchWriterStatistics>
{
    public int invocationCount = 0;
    public MockCloudWatchWriter writer;


    @Override
    public LogWriter newLogWriter(CloudWatchWriterConfig config, CloudWatchWriterStatistics stats, InternalLogger ignored)
    {
        invocationCount++;
        writer = new MockCloudWatchWriter(config);
        return writer;
    }
}
