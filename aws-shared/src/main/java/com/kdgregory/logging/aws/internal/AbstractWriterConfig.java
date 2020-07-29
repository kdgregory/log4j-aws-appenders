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

package com.kdgregory.logging.aws.internal;

import com.kdgregory.logging.common.util.DiscardAction;

/**
 *  Holds common configuration; all writer-specific config objects are subclasses.
 */
public class AbstractWriterConfig
{
    public volatile boolean truncateOversizeMessages;
    public volatile long batchDelay;
    public volatile int discardThreshold;
    public volatile DiscardAction discardAction;
    public String clientFactoryMethod;
    public String assumedRole;
    public String clientRegion;
    public String clientEndpoint;

    /**
     *  @param truncateOversizeMessages If true, messages that are too large for the service are
     *                                  truncated to fit; if false, they are discarded.
     *  @param batchDelay               Number of milliseconds to wait after receiving first
     *                                  message in batch. May be updated while running.
     *  @param discardThreshold         Maximum number of messages to retain if unable to send.
     *  @param discardAction            What to do with unsent messages over the threshold.
     *  @param clientFactoryMethod      Optional: fully-qualified name of a static method to create client.
     *  @param assumedRole              Optional: name or ARN of a role to assume when creating client.
     *  @param clientRegion             Optional: explicit region for client (used with ctor and SDK builder).
     *  @param clientEndpoint           Optional: explicit endpoint for client (only used with constructors).
     */
    public AbstractWriterConfig(
        boolean truncateOversizeMessages, long batchDelay, int discardThreshold, DiscardAction discardAction,
        String clientFactoryMethod, String assumedRole, String clientRegion, String clientEndpoint)
    {
        this.truncateOversizeMessages = truncateOversizeMessages;
        this.batchDelay = batchDelay;
        this.discardThreshold = discardThreshold;
        this.discardAction = discardAction;
        this.clientFactoryMethod = clientFactoryMethod;
        this.assumedRole = assumedRole;
        this.clientRegion = clientRegion;
        this.clientEndpoint = clientEndpoint;
    }
}
