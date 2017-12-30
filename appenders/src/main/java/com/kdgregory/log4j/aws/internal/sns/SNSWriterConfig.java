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
