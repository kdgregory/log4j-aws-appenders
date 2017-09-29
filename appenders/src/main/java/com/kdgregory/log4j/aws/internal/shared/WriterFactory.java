// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.shared;

/**
 *  Defines a function to create a {@link LogWriter}.
 *  <p>
 *  The factory is passed an appender-specific configuration object, which
 *  contains any post-substitution values needed by the writer (eg, stream
 *  name).
 */
public interface WriterFactory<T>
{
    LogWriter newLogWriter(T config);
}
