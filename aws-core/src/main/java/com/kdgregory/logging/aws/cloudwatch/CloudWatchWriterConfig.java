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

package com.kdgregory.logging.aws.cloudwatch;

import com.kdgregory.logging.aws.common.DiscardAction;
import com.kdgregory.logging.aws.internal.AbstractWriterConfig;

/**
 *  Holds configuration that is passed to the writer factory.
 */
public class CloudWatchWriterConfig
extends AbstractWriterConfig
{
    public String logGroupName;
    public String logStreamName;


    /**
     *  @param actualLogGroup       Name of the log group, with all substitutions applied.
     *  @param actualLogStream      Name of the log stream, with all substitutions applied.
     *  @param batchDelay           Number of milliseconds to wait after receiving first
     *                              message in batch.
     *  @param discardThreshold     Maximum number of messages to retain if unable to send.
     *  @param discardAction        What to do with unsent messages over the threshold.
     *  @param clientFactoryMethod  Possibly-null FQN of a static method to create client.
     *  @param clientEndpoint       Possibly-null endpoint for client.
     */
    public CloudWatchWriterConfig(
        String actualLogGroup, String actualLogStream,
        long batchDelay, int discardThreshold, DiscardAction discardAction,
        String clientFactoryMethod, String clientEndpoint)
    {
        super(batchDelay, discardThreshold, discardAction, clientFactoryMethod, clientEndpoint);

        this.logGroupName = actualLogGroup;
        this.logStreamName = actualLogStream;
    }
}
