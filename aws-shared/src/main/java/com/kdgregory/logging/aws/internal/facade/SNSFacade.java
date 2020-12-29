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

package com.kdgregory.logging.aws.internal.facade;

import com.kdgregory.logging.common.LogMessage;

/**
 *  A facade for Simple Notification Service (SNS) operations. All operations
 *  may throw {@link SNSFacadeException}.
 */
public interface SNSFacade
{
    /**
     *  Attempts to match the configured topic ARN or name to an existing SNS
     *  topic in the configured account and region. Returns the topic ARN if
     *  found, null if not found. Throws if unable to retrieve topic information
     *  for any reason, including throttling.
     */
    String lookupTopic();


    /**
     *  Attempts to create a topic with the configured name. Returns the topic's
     *  ARN if successful, throws on any failure.
     *  <p>
     *  Note: SNS silently succeeds if the topic already exists.
     */
    String createTopic();


    /**
     *  Attempts to publish the provided message, using configured topic and subject.
     *  <p>
     *  Throws if unable, including throttling.
     */
    void publish(LogMessage message);


    /**
     *  Shuts down the underlying client.
     */
    void shutdown();
}
