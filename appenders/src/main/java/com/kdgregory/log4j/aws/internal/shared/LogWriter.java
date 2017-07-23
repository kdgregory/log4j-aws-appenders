// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.shared;


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
     *  Sets the batch delay for the writer. The appender is assumed to expose a delay
     *  parameter, and this method allows it to change the writer's delay at runtime.
     *  Changes may or may not take place immediately.
     *  <p>
     *  If the writer doesn't support batching, this will be a no-op.
     */
    void setBatchDelay(long value);


    /**
     *  Signals the writer that it will no longer receive batches. It should, however,
     *  make a best effort to send any batches that it already has before exiting its
     *  <code>run()</code> method.
     */
    void stop();
}
