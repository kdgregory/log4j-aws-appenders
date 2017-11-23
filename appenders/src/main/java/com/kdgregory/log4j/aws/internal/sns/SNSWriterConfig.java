// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.sns;


/**
 *  Holds configuration for the LogWriter. This is a simple struct, with writable fields
 *  (because config can change). It is not exposed to the outside world.
 */
public class SNSWriterConfig
{
    public String topicName;
    public String topicArn;


    /**
     *  @param  topicName   Identifies the destination topic by name.
     *  @param  topicArn    Identifies the destination topic by ARN.
     */
    public SNSWriterConfig(String topicName, String topicArn)
    {
        this.topicName = topicName;
        this.topicArn = topicArn;
    }
}
