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

import com.kdgregory.logging.aws.internal.AbstractWriterConfig;
import com.kdgregory.logging.common.util.DiscardAction;

/**
 *  Holds configuration that is passed to the writer factory.
 */
public class CloudWatchWriterConfig
extends AbstractWriterConfig
{
    public String logGroupName;
    public String logStreamName;
    public Integer retentionPeriod;
    public boolean dedicatedWriter;


    /**
     *  @param actualLogGroup       Name of the log group, with all substitutions applied.
     *  @param actualLogStream      Name of the log stream, with all substitutions applied.
     *  @param retentionPeriod      A non-default retention period to use when creating log group.
     *  @param dedicatedWriter      Indicates whether the stream will only be written by this writer.
     *  @param batchDelay           Number of milliseconds to wait after receiving first
     *                              message in batch.
     *  @param discardThreshold     Maximum number of messages to retain if unable to send.
     *  @param discardAction        What to do with unsent messages over the threshold.
     *  @param clientFactoryMethod  Optional: fully-qualified name of a static method to create client.
     *  @param assumedRole          Optional: name or ARN of a role to assume when creating client.
     *  @param clientRegion         Optional: explicit region for client (used with ctor and SDK builder).
     *  @param clientEndpoint       Optional: explicit endpoint for client (only used with constructors).
     */
    public CloudWatchWriterConfig(
        String actualLogGroup, String actualLogStream, Integer retentionPeriod, boolean dedicatedWriter,
        long batchDelay, int discardThreshold, DiscardAction discardAction,
        String clientFactoryMethod, String assumedRole, String clientRegion, String clientEndpoint)
    {
        super(batchDelay, discardThreshold, discardAction, clientFactoryMethod, assumedRole, clientRegion, clientEndpoint);

        this.logGroupName = actualLogGroup;
        this.logStreamName = actualLogStream;
        this.retentionPeriod = retentionPeriod;
        this.dedicatedWriter = dedicatedWriter;
    }
}
