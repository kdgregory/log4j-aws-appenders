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

package com.kdgregory.logging.aws.kinesis;

import com.kdgregory.logging.aws.internal.AbstractWriterConfig;
import com.kdgregory.logging.common.util.DiscardAction;


/**
 *  Holds configuration for the LogWriter. This is a simple struct, with writable fields
 *  (because config can change). It is not exposed to the outside world.
 */
public class KinesisWriterConfig
extends AbstractWriterConfig
{
    public String           streamName;
    public String           partitionKey;
    public boolean          autoCreate;
    public int              shardCount;
    public Integer          retentionPeriod;


    /**
     *  @param streamName               Name of the stream where messages will be written.
     *  @param partitionKey             Partition key for messages written to stream. If blank
     *                                  we'll generate a random partition key for each message.
     *  @param autoCreate               If true, stream will be created if it doesn't already
     *                                  exist. If false, writer will fail to start.
     *  @param shardCount               Number of shards to use when creating the stream
     *                                  (ignored if stream already exists).
     *  @param retentionPeriod          Retention period to use when creating the stream
     *                                  (ignored if stream already exists); null indicates
     *                                  use the default retention period.
     *  @param truncateOversizeMessages If true, messages that are too large for the service are
     *                                  truncated to fit; if false, they are discarded.
     *  @param batchDelay               Number of milliseconds to wait for messages to be
     *                                  ready to send.
     *  @param discardThreshold         Maximum number of messages to retain if unable to send.
     *  @param discardAction            What to do with unsent messages over the threshold.
     *  @param clientFactoryMethod      Optional: fully-qualified name of a static method to create client.
     *  @param assumedRole              Optional: name or ARN of a role to assume when creating client.
     *  @param clientRegion             Optional: explicit region for client (used with ctor and SDK builder).
     *  @param clientEndpoint           Optional: explicit endpoint for client (only used with constructors).
     */
    public KinesisWriterConfig(
        String streamName, String partitionKey,
        boolean autoCreate, int shardCount, Integer retentionPeriod,
        boolean truncateOversizeMessages, long batchDelay, int discardThreshold, DiscardAction discardAction,
        String clientFactoryMethod, String assumedRole, String clientRegion, String clientEndpoint)
    {
        super(truncateOversizeMessages, batchDelay, discardThreshold, discardAction, clientFactoryMethod, assumedRole, clientRegion, clientEndpoint);

        this.streamName = streamName;
        this.partitionKey = partitionKey;
        this.autoCreate = autoCreate;
        this.shardCount = shardCount;
        this.retentionPeriod = retentionPeriod;
    }
}
