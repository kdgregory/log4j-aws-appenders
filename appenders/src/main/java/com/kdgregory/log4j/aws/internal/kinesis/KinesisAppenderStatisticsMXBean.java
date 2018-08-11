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

import java.util.Date;

import javax.management.MXBean;


/**
 *  Defines the JMX Bean interface for {@link KinesisAppenderStatistics}.
 */
@MXBean
public interface KinesisAppenderStatisticsMXBean
{
    /**
     *  Returns the actual log stream name used by the writer.
     */
    String getActualStreamName();


    /**
     *  Returns the most recent error from the writer. This will be null if there
     *  have been no errors.
     */
    String getLastErrorMessage();


    /**
     *  Returns the timestamp of the most recent error from the writer. This will be
     *  null if there have been no errors.
     */
    Date getLastErrorTimestamp();


    /**
     *  Returns the number of messages successfully sent to the stream.
     */
    int getMessagesSent();
}
