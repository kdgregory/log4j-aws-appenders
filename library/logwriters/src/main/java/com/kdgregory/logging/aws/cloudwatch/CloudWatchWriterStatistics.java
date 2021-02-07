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

package com.kdgregory.logging.aws.cloudwatch;

import java.util.concurrent.atomic.AtomicInteger;

import com.kdgregory.logging.aws.internal.AbstractWriterStatistics;


/**
 *  Statistics specific to CloudWatchLogWriter.
 */
public class CloudWatchWriterStatistics
extends AbstractWriterStatistics
implements CloudWatchWriterStatisticsMXBean
{
    private volatile String  actualLogGroupName;
    private volatile String  actualLogStreamName;
    private volatile AtomicInteger writerRaceRetries = new AtomicInteger(0);
    private volatile AtomicInteger unrecoveredWriterRaceRetries = new AtomicInteger(0);


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


    @Override
    public int getMessagesDiscarded()
    {
        return super.getMessagesDiscarded();
    }


    @Override
    public int getWriterRaceRetries()
    {
        return writerRaceRetries.get();
    }


    public void updateWriterRaceRetries()
    {
        writerRaceRetries.incrementAndGet();
    }


    @Override
    public int getUnrecoveredWriterRaceRetries()
    {
        return unrecoveredWriterRaceRetries.get();
    }


    public void updateUnrecoveredWriterRaceRetries()
    {
        unrecoveredWriterRaceRetries.incrementAndGet();
    }
}
