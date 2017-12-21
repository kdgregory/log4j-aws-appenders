// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.sns;

import com.kdgregory.log4j.aws.internal.shared.MessageQueue.DiscardAction;

/**
 *  Holds configuration for the LogWriter. This is a simple struct, with writable fields
 *  (because config can change). It is not exposed to the outside world.
 */
public class SNSWriterConfig
{
    public String topicName;
    public String topicArn;
    public String subject;
    public int discardThreshold;
    public DiscardAction discardAction;


    /**
     *  @param  topicName           Identifies the destination topic by name.
     *  @param  topicArn            Identifies the destination topic by ARN.
     *  @param  subject             The subject to be applied to outgoing messages;
     *                              blank disables.
     *  @param  discardThreshold    The maximum number of messages that will be retained
     *                              in the queue.
     *  @param  discardAction       Controls how messages are discarded from the queue to
     *                              remain within threshold.
     */
    public SNSWriterConfig(String topicName, String topicArn, String subject, int discardThreshold, DiscardAction discardAction)
    {
        this.topicName = topicName;
        this.topicArn = topicArn;
        this.subject = subject;
        this.discardThreshold = discardThreshold;
        this.discardAction = discardAction;
    }
}
