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

import com.kdgregory.logging.aws.kinesis.KinesisConstants.StreamStatus;
import com.kdgregory.logging.common.LogMessage;


/**
 *  Exposes the CloudWatch Logs APIs used by <code>KinesisLogWriter</code>.
 *  <p>
 *  Instances are created by {@link FacadaFactory}, and are tied to a single
 *  writer's configuration.
 *  <p>
 *  All operations may throw {@link KinesisFacadeException}. Callers are expected
 *  to catch this exception (it's checked), and take action based on the reason code
 *  that it exposes.
 */
public interface KinesisFacade
{
    /**
     *  Returns the current status of the stream, null if the status cannot be
     *  determined due to a retryable condition.
     */
    StreamStatus retrieveStreamStatus();


    /**
     *  Creates the stream. Note that the stream will not be active for up to 60
     *  seconds after this call returns. Caller must be prepared to retry if the
     *  call is throttled.
     */
    void createStream();


    /**
     *  Sets the stream's retention period, iff configuration specifies a non-null
     *  period. Caller must be prepared to retry if the call is throttled.
     */
    void setRetentionPeriod();


    /**
     *  Attempts to send records to the stream. The entire call may fail, or individual
     *  records may be rejected. The returned list contains any records that were not
     *  successfully written.
     */
    List<LogMessage> putRecords(List<LogMessage> batch);


    /**
     *  Shuts down the underlying client.
     */
    void shutdown();
}
