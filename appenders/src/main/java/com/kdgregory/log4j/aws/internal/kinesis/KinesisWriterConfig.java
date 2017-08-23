// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.kinesis;

public class KinesisWriterConfig
{
    public String streamName;
    public String partitionKey;
    public long batchDelay;
    // TODO - add shard count
    
    
    public KinesisWriterConfig(String streamName, String partitionKey, long batchDelay)
    {
        this.streamName = streamName;
        this.partitionKey = partitionKey;
        this.batchDelay = batchDelay;
    }
}
