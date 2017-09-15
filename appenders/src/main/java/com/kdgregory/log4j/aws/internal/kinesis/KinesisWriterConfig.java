// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.kinesis;

public class KinesisWriterConfig
{
    public String streamName;
    public String partitionKey;
    public long batchDelay;
    public int shardCount;
    
    
    public KinesisWriterConfig(String streamName, int shardCount, String partitionKey, long batchDelay)
    {
        this.streamName = streamName;
        this.shardCount = shardCount;
        this.partitionKey = partitionKey;
        this.batchDelay = batchDelay;
    }
}
