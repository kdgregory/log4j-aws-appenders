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

import com.kdgregory.logging.aws.internal.AbstractWriterStatistics;


public class SNSWriterStatistics
extends AbstractWriterStatistics
implements SNSWriterStatisticsMXBean
{
    private String actualTopicName;
    private String actualTopicArn;
    private String actualSubject;


    public void setActualTopicName(String value)
    {
        actualTopicName = value;
    }

    @Override
    public String getActualTopicName()
    {
        return actualTopicName;
    }


    public void setActualTopicArn(String value)
    {
        actualTopicArn = value;
    }

    @Override
    public String getActualTopicArn()
    {
        return actualTopicArn;
    }


    public void setActualSubject(String value)
    {
        actualSubject = value;
    }

    @Override
    public String getActualSubject()
    {
        return actualSubject;
    }
}
