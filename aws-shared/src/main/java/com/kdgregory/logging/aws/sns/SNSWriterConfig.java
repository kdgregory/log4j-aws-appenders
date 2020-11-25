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

package com.kdgregory.logging.aws.sns;

import com.kdgregory.logging.aws.internal.AbstractWriterConfig;


/**
 *  Configuration for SNSLogWriter.
 */
public class SNSWriterConfig
extends AbstractWriterConfig<SNSWriterConfig>
{
    private String  topicName;
    private String  topicArn;
    private boolean autoCreate;
    private String  subject;


    public SNSWriterConfig()
    {
        super();
        super.setBatchDelay(1);
    }


    @Override
    public SNSWriterConfig setBatchDelay(long value)
    {
        // this call is a no-op
        return this;
    }


    public String getTopicName()
    {
        return topicName;
    }

    public SNSWriterConfig setTopicName(String value)
    {
        topicName = value;
        return this;
    }


    public String getTopicArn()
    {
        return topicArn;
    }

    public SNSWriterConfig setTopicArn(String value)
    {
        topicArn = value;
        return this;
    }


    public boolean getAutoCreate()
    {
        return autoCreate;
    }

    public SNSWriterConfig setAutoCreate(boolean value)
    {
        autoCreate = value;
        return this;
    }


    public String getSubject()
    {
        return subject;
    }

    public SNSWriterConfig setSubject(String value)
    {
        subject = value;
        return this;
    }
}
