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

import com.kdgregory.log4j.aws.internal.shared.MessageQueue.DiscardAction;


/**
 *  Holds configuration for the LogWriter. This is a simple struct, with writable fields
 *  (because config can change). It is not exposed to the outside world.
 */
public class KinesisWriterConfig
{
    public String           streamName;
    public String           partitionKey;
    public int              partitionKeyLength;
    public long             batchDelay;
    public int              discardThreshold;
    public DiscardAction    discardAction;
    public String           clientFactoryMethod;
    public String           clientEndpoint;
    public boolean          autoCreate;
    public int              shardCount;
    public Integer          retentionPeriod;


    /**
     *  @param streamName           Name of the stream where messages will be written.
     *  @param partitionKey         Partition key for messages written to stream. If blank
     *                              we'll generate a random partition key for each message.
     *  @param partitionKeyLength   Length of the partition key in bytes, after conversion
     *                              to UTF-8. Used to calculate message packing.
     *  @param batchDelay           Number of milliseconds to wait for messages to be
     *                              ready to send.
     *  @param discardThreshold     Maximum number of messages to retain if unable to send.
     *  @param discardAction        What to do with unsent messages over the threshold.
     *  @param clientFactoryMethod  Possibly-null FQN of a static method to create client.
     *  @param clientEndpoint       Possibly-null endpoint for client.
     *  @param autoCreate           If true, stream will be created if it doesn't already
     *                              exist. If false, writer will fail to start.
     *  @param shardCount           Number of shards to use when creating the stream
     *                              (ignored if stream already exists).
     *  @param retentionPeriod      Retention period to use when creating the stream
     *                              (ignored if stream already exists); null indicates
     *                              use the default retention period.
     */
    public KinesisWriterConfig(
        String streamName, String partitionKey, int partitionKeyLength,
        long batchDelay, int discardThreshold, DiscardAction discardAction,
        String clientFactoryMethod, String clientEndpoint,
        boolean autoCreate, int shardCount, Integer retentionPeriod)
    {
        this.streamName = streamName;
        this.partitionKey = partitionKey;
        this.partitionKeyLength = (partitionKeyLength > 0) ? partitionKeyLength : 8;
        this.batchDelay = batchDelay;
        this.discardThreshold = discardThreshold;
        this.discardAction = discardAction;
        this.clientFactoryMethod = clientFactoryMethod;
        this.clientEndpoint = clientEndpoint;
        this.autoCreate = autoCreate;
        this.shardCount = shardCount;
        this.retentionPeriod = retentionPeriod;
    }
}
