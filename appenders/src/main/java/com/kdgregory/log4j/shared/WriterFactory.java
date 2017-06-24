// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.shared;


/**
 *  Defines a function to create a {@link LogWriter}.
 *  <p>
 *  This is normally subclassed by the appender as an anonymous inner class so
 *  that it has access to all configuration variables defined by the appender.
 *  As a result, the factory function does not take any parameters.
 *  <p>
 *  In order to support testing, the appender not rely on the factory to apply
 *  substitutions. Instead, it should apply them and make the results available
 *  for examination by the test class.
 */
public interface WriterFactory
{
    LogWriter newLogWriter();
}
