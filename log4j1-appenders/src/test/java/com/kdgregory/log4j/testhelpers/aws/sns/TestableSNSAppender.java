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

import com.kdgregory.aws.logging.common.LogMessage;
import com.kdgregory.aws.logging.common.LogWriter;
import com.kdgregory.aws.logging.common.ThreadFactory;
import com.kdgregory.aws.logging.common.WriterFactory;
import com.kdgregory.aws.logging.sns.SNSAppenderStatistics;
import com.kdgregory.aws.logging.sns.SNSWriterConfig;
import com.kdgregory.aws.logging.testhelpers.sns.MockSNSWriter;
import com.kdgregory.log4j.aws.SNSAppender;


/**
 *  This class provides visibility into the protected variables held by
 *  SNSAppender and AbstractAppender.
 */
public class TestableSNSAppender
extends SNSAppender
{

    public void setThreadFactory(ThreadFactory threadFactory)
    {
        this.threadFactory = threadFactory;
    }


    public void setWriterFactory(WriterFactory<SNSWriterConfig,SNSAppenderStatistics> writerFactory)
    {
        this.writerFactory = writerFactory;
    }


    public WriterFactory<SNSWriterConfig,SNSAppenderStatistics> getWriterFactory()
    {
        return writerFactory;
    }


    public LogWriter getWriter()
    {
        return writer;
    }


    public MockSNSWriter getMockWriter()
    {
        return (MockSNSWriter)writer;
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
