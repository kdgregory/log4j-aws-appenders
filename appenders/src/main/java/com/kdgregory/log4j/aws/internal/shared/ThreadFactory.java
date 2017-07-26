// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.shared;

import java.lang.Thread.UncaughtExceptionHandler;


/**
 *  Creates and starts a new thread for running the LogWriter.
 *  <p>
 *  Appenders are constructed with an instance of {@link DefaultThreadFactory}
 *  (or  perhaps an appender-specific factory), and lazily call this factory on
 *  first append. The thread is not returned by the factory; it should exit
 *  normally when the appender calls {@link LogWriter#stop}.
 *  <p>
 *  To handle unexpected thread death, the appender must provide an uncaught
 *  exception handler. A typical handler will log the event using Log4J's internal
 *  logger, and then create a new writer.
 *  <p>
 *  In the test helpers, you will find <code>MockThreadFactory</code>,  which does
 *  not actually start a thread.
 */
public interface ThreadFactory
{
    void startLoggingThread(LogWriter writer, UncaughtExceptionHandler exceptionHandler);
}
