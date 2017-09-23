// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.kinesis;


/**
 *  Holds configuration for the LogWriter. This is a simple struct, with writable fields
 *  (because config can change). It is not exposed to the outside world.
 */
public class KinesisWriterConfig
{
    public String   streamName;
    public int      shardCount;
    public String   partitionKey;
    public int      partitionKeyLength;
    public long     batchDelay;


    /**
     *  @param streamName           Name of the stream where messages will be written.
     *  @param shardCount           Number of shards to use when creating the stream
     *                              (ignored if stream already exists).
     *  @param partitionKey         Partition key for messages written to stream.
     *  @param partitionKeyLength   Length of the partition key in bytes, after conversion
     *                              to UTF-8. Used to calculate message packing.
     *  @param batchDelay           Number of milliseconds to wait for messages to be
     *                              ready to send.
     */
    public KinesisWriterConfig(String streamName, int shardCount, String partitionKey, int partitionKeyLength, long batchDelay)
    {
        this.streamName = streamName;
        this.shardCount = shardCount;
        this.partitionKey = partitionKey;
        this.partitionKeyLength = partitionKeyLength;
        this.batchDelay = batchDelay;
    }
}
