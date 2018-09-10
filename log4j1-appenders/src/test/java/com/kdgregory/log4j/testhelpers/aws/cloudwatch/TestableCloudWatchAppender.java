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

package com.kdgregory.log4j.testhelpers.aws.cloudwatch;

import com.kdgregory.log4j.aws.CloudWatchAppender;
import com.kdgregory.log4j.aws.cloudwatch.CloudWatchAppenderStatistics;
import com.kdgregory.log4j.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.log4j.common.LogMessage;
import com.kdgregory.log4j.common.LogWriter;
import com.kdgregory.log4j.common.ThreadFactory;
import com.kdgregory.log4j.common.WriterFactory;


/**
 *  This class provides visibility into the protected variables held by
 *  CloudWatchAppender and AbstractAppender.
 */
public class TestableCloudWatchAppender extends CloudWatchAppender
{

    public void setThreadFactory(ThreadFactory threadFactory)
    {
        this.threadFactory = threadFactory;
    }


    public void setWriterFactory(WriterFactory<CloudWatchWriterConfig,CloudWatchAppenderStatistics> writerFactory)
    {
        this.writerFactory = writerFactory;
    }


    public MockCloudWatchWriterFactory getWriterFactory()
    {
        return (MockCloudWatchWriterFactory)writerFactory;
    }


    public LogWriter getWriter()
    {
        return writer;
    }


    // a convenience function so that we're not always casting
    public MockCloudWatchWriter getMockWriter()
    {
        return (MockCloudWatchWriter)writer;
    }


    public void updateLastRotationTimestamp(long offset)
    {
        lastRotationTimestamp += offset;
    }


    public long getLastRotationTimestamp()
    {
        return lastRotationTimestamp;
    }


    @Override
    public boolean isMessageTooLarge(LogMessage message)
    {
        return super.isMessageTooLarge(message);
    }
}
