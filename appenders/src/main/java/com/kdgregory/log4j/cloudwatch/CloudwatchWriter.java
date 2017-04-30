// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.cloudwatch;

import java.util.List;

/**
 *  Defines the interactions between the appender and writer, allowing mock
 *  implementations for testing.
 */
public interface CloudwatchWriter
{
    /**
     *  Adds a batch of messages to the writer.
     *
     *  TODO: require that this be a concurrent list, and allow the writer to pull as
     *        many messages at it wants.
     */
    void addBatch(List<LogMessage> batch);
}
