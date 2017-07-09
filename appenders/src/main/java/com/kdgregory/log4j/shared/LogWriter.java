// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.shared;

import java.util.List;


/**
 *  Defines the contract between appenders and writers.
 *  <p>
 *  Writers run on a background thread, accepting batches of messages and retaining
 *  them until sent or discarded.
 */
public interface LogWriter
extends Runnable
{
    /**
     *  Adds a batch of messages to the writer.
     *  <p>
     *  Implementations are expected to be synchronized, so that concurrent calls will
     *  not interleave messages.
     */
    void addBatch(List<LogMessage> batch);


    /**
     *  Signals the writer that it will no longer receive batches. It should, however,
     *  make a best effort to send any batches that it already has before exiting its
     *  <code>run()</code> method.
     */
    void stop();
}
