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
 *  Writers run on a background thread, building batches of messages and attempting
 *  to send them to the destination.
 */
public interface LogWriter
extends Runnable
{
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


    /**
     *  Used when shutdown hooks are in effect, so that the writer can remove that hook
     *  during cleanup. See {@link com.kdgregory.logging.common.factories.DefaultThreadFactory}
     *  for an example.
     */
    void setShutdownHook(Thread shutdownHook);


    /**
     *  @deprecated
     *  This was formerly used by appenders to check message size before appending. It has
     *  been replaced by checks within {@link #append} that rely on {@link #maxMessageSize}.
     *  <p>
     *  To be removed in 3.0.
     */
    @Deprecated
    public boolean isMessageTooLarge(LogMessage message);


    /**
     *  Returns the maximum allowed UTF-8 message size for the destination.
     */
    int maxMessageSize();


    /**
     *  Adds a message to the writer waiting for batch.
     *  <p>
     *  Implementations should assume that they are invoked within a synchronized
     *  block, and therefore should not perform excessive amounts of work.
     */
    void addMessage(LogMessage message);


    /**
     *  Initializes the writer. Normally called from the <code>run()</code>
     *  method, but exposed for synchronous operation.
     *
     *  @return <code>true</code> if initialization was successful, <code>false</code>
     *          if it failed for any reason. The writer is expected to clean up after
     *          itself on failure.
     */
    boolean initialize();


    /**
     *  Waits up to the specified amount of time for the writer to initialize.
     *
     *  @return <code>true</code> if it initialized successfully, <code>false</code>
     *          if writer failed to initialize, the timeout expired, or the calling
     *          thread was interrupted.
     */
    boolean waitUntilInitialized(long millisToWait);


    /**
     *  Processes a batch of messages. Normally called from the <code>run()</code>
     *  method, but exposed for synchronous operation. Note that execution time will
     *  depend on both initial wait time (which is passed here) and the batch delay
     *  (which is configured).
     *  <p>
     *  Implementations must be synchronized. In normal (threaded) operation this
     *  synchronization will be uncontested. However, for an appender in synchronous
     *  mode, it will prevent concurrent attempts to write to the destination, which
     *  might otherwise result in throttling or retries.
     *
     *  @param  waitUntil   a timestamp (not timeout) that determines how long this
     *                      method will wait for the initial message in the batch.
     */
    void processBatch(long waitUntil);


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


    /**
     *  Performs any cleanup before the writer is truly stopped. Normally called from the
     *  <code>run()</code> method, but exposed for synchronous operation.
     */
    void cleanup();
}
