// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.shared;

import java.util.List;

/**
 *  Defines the interactions between the appender and writer, allowing mock
 *  implementations for testing.
 */
public interface LogWriter
{
    /**
     *  Adds a batch of messages to the writer.
     *
     *  TODO: require that this be a concurrent list, and allow the writer to pull as
     *        many messages at it wants.
     */
    void addBatch(List<LogMessage> batch);
    
    
    /**
     *  Breaks the writer out of any write loops that it might be running.
     */
    void stop();
}
