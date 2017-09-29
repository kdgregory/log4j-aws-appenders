// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.cloudwatch;


/**
 *  Holds configuration that is passed to the writer factory.
 */
public class CloudWatchWriterConfig
{
    public String logGroup;
    public String logStream;
    public long batchDelay;


    public CloudWatchWriterConfig(String actualLogGroup, String actualLogStream, long batchDelay)
    {
        this.logGroup = actualLogGroup;
        this.logStream = actualLogStream;
        this.batchDelay = batchDelay;
    }
}
