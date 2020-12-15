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

import java.util.List;

import com.kdgregory.logging.common.LogMessage;


/**
 *  Exposes the CloudWatch Logs APIs that the logwriter needs, invoked through
 *  the v1 SDK. Instances are tied to a single log group/stream, provided via
 *  configuration.
 *  <p>
 *  All operations other than {@link #validateConfig} will throw
 *  {@link CloudWatchFacadeException} to indicate any situation not on the
 *  "happy path". Callers must catch this exception (it's checked), and take
 *  action based on the reason code that it exposes.
 */
public interface CloudWatchFacade
{
    /**
     *  Determines whether the configured log group exists, and returns its ARN
     *  if it does. Returns <code>null</code> if the log group doesn't exist or
     *  the call is throttled.
     */
    String findLogGroup()
    throws CloudWatchFacadeException;


    /**
     *  Attempts to create the configured log group.
     *  <p>
     *  Log group creation is an asynchronous operation: the group may not be available
     *  for use for several seconds afterward. You should call {@link #findLogGroup} in
     *  a loop (with delays) until it returns the group's ARN.
     */
    void createLogGroup()
    throws CloudWatchFacadeException;


    /**
     *  Sets the retention period on the configured log group. The configured retention
     *  period must be one of the acceptable values.
     */
    void setLogGroupRetention()
    throws CloudWatchFacadeException;


    /**
     *  Attempts to create the configured log stream.
     *  <p>
     *  Stream creation is an asynchronous operation: the stream may not be available for
     *  several seconds after creation. You should call {@link #retrieveSequenceToken} in
     *  a loop (with delays) until it returns a value.
     */
    void createLogStream()
    throws CloudWatchFacadeException;


    /**
     *  If the configured stream exists, returns the sequence token needed to write to it.
     *  Returns <code>null</code> if the stream does not exist or the retrieve operation
     *  is thottled.
     *  <p>
     *  Note: if the log group does not exist, this is treated as if the stream does not
     *        exist. The log-writer must use other mechanism (eg, failed write) to determine
     *        that it needs to recreate the log group.
     */
    String retrieveSequenceToken()
    throws CloudWatchFacadeException;


    /**
     *  Attempts to send a batch of messages. If successful, returns the next sequence token
     *  for the stream. If unsuccessful, throws an exception that should determine caller's
     *  next steps.
     *
     *  @param  sequenceToken   Sequence token from a prior call to this method or an explicit
     *                          call to {@link #retrieveSequenceToken}.
     *  @param  messages        The messages to send. These messages must meet all of the
     *                          requirements for <code>PutLogEvents</code> (sorted by
     *                          timestamp, and within acceptable timestamp ranges).
     */
    String sendMessages(String sequenceToken, List<LogMessage> messages)
    throws CloudWatchFacadeException;


    /**
     *  Shuts down the underlying client.
     */
    public void shutdown()
    throws CloudWatchFacadeException;
}
