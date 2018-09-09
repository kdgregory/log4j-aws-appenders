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

import com.kdgregory.log4j.aws.internal.shared.AbstractAppenderStatistics;


public class KinesisAppenderStatistics
extends AbstractAppenderStatistics
implements KinesisAppenderStatisticsMXBean
{
    private volatile String actualStreamName;


    public void setActualStreamName(String value)
    {
        actualStreamName = value;
    }

    @Override
    public String getActualStreamName()
    {
        return actualStreamName;
    }

}
