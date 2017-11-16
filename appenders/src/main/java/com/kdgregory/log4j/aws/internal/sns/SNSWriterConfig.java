// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.sns;


/**
 *  Holds configuration for the LogWriter. This is a simple struct, with writable fields
 *  (because config can change). It is not exposed to the outside world.
 */
public class SNSWriterConfig
{
    public String topicArn;


    /**
     *  @param  topicArn    Identifies the destination topic for messages.
     */
    public SNSWriterConfig(String topicArn)
    {
        this.topicArn = topicArn;
    }
}
