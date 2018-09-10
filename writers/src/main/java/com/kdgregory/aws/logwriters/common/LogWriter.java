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

package com.kdgregory.aws.logwriters.common;

/**
 *  Defines the contract between appenders and writers.
 *  <p>
 *  Writers run on a background thread, building batches of messages and attempting
 *  to send them to the destination.
 */
public interface LogWriter
extends Runnable
{
    /**
     *  Adds a message to the writer waiting for batch.
     *  <p>
     *  Implementations should assume that they are invoked within a synchronized
     *  block, and therefore should not perform excessive amounts of work.
     */
    void addMessage(LogMessage message);


    /**
     *  Signals the writer that it will no longer receive batches. It should, however,
     *  make a best effort to send any batches that it already has before exiting its
     *  <code>run()</code> method.
     */
    void stop();


    /**
     *  Sets the batch delay for the writer. The appender is assumed to expose a delay
     *  parameter, and this method allows it to change the writer's delay at runtime.
     *  Changes may or may not take place immediately.
     *  <p>
     *  If the writer doesn't support batching, this will be a no-op.
     */
    void setBatchDelay(long value);


    /**
     *  Updates the writer's discard threshold: the maximum number of message stored
     *  in its queue.
     */
    void setDiscardThreshold(int value);


    /**
     *  Updates the writer's discard action: how it discards messages once the threshold
     *  has been reached.
     */
    void setDiscardAction(DiscardAction value);
}
