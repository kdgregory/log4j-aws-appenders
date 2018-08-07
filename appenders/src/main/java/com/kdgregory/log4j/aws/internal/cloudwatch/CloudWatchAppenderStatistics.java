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

package com.kdgregory.log4j.aws.internal.cloudwatch;

import com.kdgregory.log4j.aws.internal.shared.AbstractAppenderStatistics;


/**
 *  Statistics specific to the CloudWatch appender.
 *  <p>
 *  Since it's possible that there may be multiple logwriters alive at once, we
 *  need to synchronize any methods that update a statistic. Simple writes are
 *  fine just being volatile (in general, those fields will only be set when the
 *  writer is created).
 */
public class CloudWatchAppenderStatistics
extends AbstractAppenderStatistics
implements CloudWatchAppenderStatisticsMXBean
{
    private volatile String  actualLogGroupName;
    private volatile String  actualLogStreamName;


    @Override
    public String getActualLogGroupName()
    {
        return actualLogGroupName;
    }

    public void setActualLogGroupName(String actualLogGroupName)
    {
        this.actualLogGroupName = actualLogGroupName;
    }


    @Override
    public String getActualLogStreamName()
    {
        return actualLogStreamName;
    }

    public void setActualLogStreamName(String actualLogStreamName)
    {
        this.actualLogStreamName = actualLogStreamName;
    }
}
