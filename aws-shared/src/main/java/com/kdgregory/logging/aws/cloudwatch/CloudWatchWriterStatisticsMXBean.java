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

package com.kdgregory.logging.aws.cloudwatch;

import java.util.Date;
import java.util.List;

/**
 *  Defines the JMX Bean interface for {@link CloudWatchWriterStatistics}.
 */
public interface CloudWatchWriterStatisticsMXBean
{
    /**
     *  Returns the actual log group name for the appender, after substitutions.
     */
    String getActualLogGroupName();


    /**
     *  Returns the actual log stream name for the appender, after substitutions.
     */
    String getActualLogStreamName();


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
     *  Returns the number of messages successfully sent to the logstream, by all
     *  writers.
     */
    int getMessagesSent();


    /**
     *  Returns the number of messages discarded by the current writer's message queue.
     *  Note that writer rotation (which can happen due to errors) will reset this.
     */
    int getMessagesDiscardedByCurrentWriter();


    /**
     *  Returns the number of retries due to <code>InvalidSequenceTokenException</code>
     *  response from <code>PutLogEvents</code>. This exception will be thrown when
     *  there is a race between two writers: both get the same sequence token but only
     *  once can use it. This may happen when there are many instances that are started
     *  at the same time or have a low batch delay.
     */
    int getWriterRaceRetries();


    /**
     *  Returns the times that the writer had to requeue a batch after receiving and
     *  repeated <code>InvalidSequenceTokenException</code> errors. This number should
     *  remain 0; if it is non-zero, and particularly if it is a noticeable percentage
     *  of <code>WriterRaceRetries</code>, it indicates that you have too many writers
     *  concurrently accessing the same stream. Either increase batch delay or (better)
     *  direct output to different streams.
     */
    int getUnrecoveredWriterRaceRetries();
}
