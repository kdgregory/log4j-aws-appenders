// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.kinesis;

import com.kdgregory.log4j.aws.internal.shared.MessageQueue.DiscardAction;


/**
 *  Holds configuration for the LogWriter. This is a simple struct, with writable fields
 *  (because config can change). It is not exposed to the outside world.
 */
public class KinesisWriterConfig
{
    public String           streamName;
    public int              shardCount;
    public Integer          retentionPeriod;
    public String           partitionKey;
    public int              partitionKeyLength;
    public long             batchDelay;
    public int              discardThreshold;
    public DiscardAction    discardAction;


    /**
     *  @param streamName           Name of the stream where messages will be written.
     *  @param shardCount           Number of shards to use when creating the stream
     *                              (ignored if stream already exists).
     *  @param retentionPeriod      Retention period to use when creating the stream
     *                              (ignored if stream already exists); null indicates
     *                              use the default retention period.
     *  @param partitionKey         Partition key for messages written to stream.
     *  @param partitionKeyLength   Length of the partition key in bytes, after conversion
     *                              to UTF-8. Used to calculate message packing.
     *  @param batchDelay           Number of milliseconds to wait for messages to be
     *                              ready to send.
     *  @param discardThreshold     Maximum number of messages to retain if unable to send.
     *  @param discardAction        What to do with unsent messages over the threshold.
     */
    public KinesisWriterConfig(
        String streamName, int shardCount, Integer retentionPeriod,
        String partitionKey, int partitionKeyLength, long batchDelay,
        int discardThreshold, DiscardAction discardAction)
    {
        this.streamName = streamName;
        this.shardCount = shardCount;
        this.retentionPeriod = retentionPeriod;
        this.partitionKey = partitionKey;
        this.partitionKeyLength = partitionKeyLength;
        this.batchDelay = batchDelay;
        this.discardThreshold = discardThreshold;
        this.discardAction = discardAction;
    }
}
