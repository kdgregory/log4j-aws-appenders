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

package com.kdgregory.logging.aws.facade;

import java.util.List;

import com.kdgregory.logging.common.LogMessage;


/**
 *  Exposes the CloudWatch Logs APIs used by <code>CloudWatchLogWriter</code>.
 *  <p>
 *  Instances are created by {@link FacadeFactory}, and are tied to a single
 *  writer's configuration.
 *  <p>
 *  All operations may throw {@link CloudWatchFacadeException}. Callers are expected
 *  to catch this exception (it's checked), and take action based on the reason code
 *  that it exposes.
 */
public interface CloudWatchFacade
{
    /**
     *  Determines whether the configured log group exists, and returns its ARN
     *  if it does. Returns <code>null</code> if the log group doesn't exist or
     *  the call is throttled.
     */
    String findLogGroup();


    /**
     *  Attempts to create the configured log group.
     *  <p>
     *  Log group creation is an asynchronous operation: the group may not be available
     *  for use for several seconds afterward. You should call {@link #findLogGroup} in
     *  a loop (with delays) until it returns the group's ARN.
     */
    void createLogGroup();


    /**
     *  Sets the retention period on the configured log group. The configured retention
     *  period must be one of the acceptable values.
     */
    void setLogGroupRetention();


    /**
     *  Determines whether the configured log stream exists, returning its ARN
     *  if it does. Returns <code>null</code> if the log group doesn't exist or
     *  the call is throttled.
     */
    String findLogStream();


    /**
     *  Attempts to create the configured log stream.
     *  <p>
     *  Stream creation is an asynchronous operation: the stream may not be available for
     *  several seconds after creation. You should call {@link #findLogStream} in a loop
     * (with delays) until it returns a value.
     */
    void createLogStream();


    /**
     *  Attempts to send a batch of messages. If unsuccessful, throws an exception that should
     *  determine caller's next steps.
     *
     *  @param  messages        The messages to send. These messages must meet all of the
     *                          requirements for <code>PutLogEvents</code> (sorted by
     *                          timestamp, and within acceptable timestamp ranges).
     */
    void putEvents(List<LogMessage> messages);


    /**
     *  Shuts down the underlying client.
     */
    public void shutdown();
}
