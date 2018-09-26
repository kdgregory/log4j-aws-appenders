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

package com.kdgregory.aws.logging.sns;

import com.kdgregory.aws.logging.common.DiscardAction;
import com.kdgregory.aws.logging.internal.AbstractWriterConfig;


/**
 *  Holds configuration for the LogWriter. This is a simple struct, with writable fields
 *  (because config can change). It is not exposed to the outside world.
 */
public class SNSWriterConfig
extends AbstractWriterConfig
{
    public String topicName;
    public String topicArn;
    public boolean autoCreate;
    public String subject;


    /**
     *  @param  topicName           Identifies the destination topic by name; may be null.
     *  @param  topicArn            Identifies the destination topic by ARN; may be null.
     *  @param  autoCreate          Flag to indicate topic should be created if it doesn't exist.
     *  @param  subject             The subject to be applied to outgoing messages; blank disables.
     *  @param  discardThreshold    The maximum number of messages that will be retained in the queue.
     *  @param  discardAction       Controls how messages are discarded from the queue to remain within threshold.
     *  @param  clientFactoryMethod FQN of static factory method to create SNS client.
     *  @param  clientEndpoint      Possibly-null endpoint for client.
     */
    public SNSWriterConfig(
        String topicName, String topicArn, boolean autoCreate, String subject,
        int discardThreshold, DiscardAction discardAction,
        String clientFactoryMethod, String clientEndpoint)
    {
        super(1, discardThreshold, discardAction, clientFactoryMethod, clientEndpoint);

        this.topicName = topicName;
        this.topicArn = topicArn;
        this.autoCreate = autoCreate;
        this.subject = subject;
    }
}
