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

package com.kdgregory.logging.common;

import com.kdgregory.logging.common.util.DiscardAction;


/**
 *  Defines the contract between appenders and writers.
 *  <p>
 *  Writers normally run on a background thread, gathering messages into batches and
 *  sending them based on a time delay. Writers can also operate in "synchronous"
 *  mode, in which batch building/sending is triggered by the appender.
 */
public interface LogWriter
extends Runnable
{
    /**
     *  Sets the batch delay for the writer. The appender is assumed to expose a delay
     *  parameter, and this method allows it to change the writer's delay at runtime.
     *  Changes may or may not take place immediately.
     *  <p>
     *  This is a no-op if the writer doesn't support batching or is in synchronous mode.
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


    /**
     *  Returns a flag to indicate whether the writer is operating in synchrnous mode.
     */
    boolean isSynchronous();


    /**
     *  Returns the maximum allowed UTF-8 message size for the destination.
     */
    int maxMessageSize();


    /**
     *  Waits up to the specified amount of time for the writer to initialize.
     *
     *  @return <code>true</code> if it initialized successfully, <code>false</code>
     *          if writer failed to initialize, the timeout expired, or the calling
     *          thread was interrupted.
     */
    boolean waitUntilInitialized(long millisToWait);


    /**
     *  Adds a message to the writer's message queue. If in synchronous mode, this
     *  also triggers batch processing.
     *  <p>
     *  Implementations should assume that they are invoked within a synchronized
     *  block, and therefore should not perform excessive amounts work (synchronous
     *  mode being an exception).
     */
    void addMessage(LogMessage message);


    /**
     *  Signals the writer that it will no longer receive batches. It should, however,
     *  make a best effort to send any batches that it already has before exiting its
     *  <code>run()</code> method.
     */
    void stop();


    /**
     *  Waits until the writer thread has stopped, the timeout has expired, or the
     *  calling thread is interrupted.
     */
    void waitUntilStopped(long millisToWait);
}
