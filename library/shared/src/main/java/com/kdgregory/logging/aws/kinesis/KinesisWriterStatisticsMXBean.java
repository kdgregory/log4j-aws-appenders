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

package com.kdgregory.logging.aws.kinesis;

import java.util.Date;
import java.util.List;

import javax.management.MXBean;


/**
 *  Defines the JMX Bean interface for {@link KinesisWriterStatistics}.
 */
@MXBean
public interface KinesisWriterStatisticsMXBean
{
    /**
     *  Returns the actual log stream name used by the writer.
     */
    String getActualStreamName();


    /**
     *  Returns the most recent error from the writer. This will be null if there
     *  have been no errors.
     */
    String getLastErrorMessage();


    /**
     *  Returns the timestamp of the most recent error from the writer. This will be
     *  null if there have been no errors.
     */
    Date getLastErrorTimestamp();


    /**
     *  Returns the stack trace of the most recent error from the writer. This will be
     *  null if there have been no errors or if the error did not have an associated
     *  exception.
     */
    List<String> getLastErrorStacktrace();


    /**
     *  Returns the number of messages that were either dropped or truncated because
     *  they were too large for the service to accept.
     */
    int getOversizeMessages();


    /**
     *  Returns the number of messages successfully sent to the stream.
     */
    int getMessagesSent();


    /**
     *  Returns the number of messages successfully sent to the stream in the last
     *  batch. This should be at least 1; higher values indicate how many messages
     *  will be lost if the program shuts down unexpectedly.
     */
    int getMessagesSentLastBatch();


    /**
     *  Returns the number of messages requeued because they could not be sent. This
     *  should be 0; non-zero values indicate throttling or error. If you are able to
     *  send some messages but not all, it indicates that individual partitions are
     *  at their limit and the stream should be resharded or the partition key changed
     *  (perhaps to a random key).
     */
    int getMessagesRequeuedLastBatch();


    /**
     *  Returns the number of messages discarded by the writer's message queue.
     */
    int getMessagesDiscarded();
}
