// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.shared;


/**
 *  Creates and starts a new thread for running the LogWriter.
 *  <p>
 *  Appenders are constructed with an instance of {@link DefaultThreadFactory}
 *  (or  perhaps an appender-specific fatory), and lazily call this factory on
 *  first append. For testing, you can replace the default factory with {@link
 *  MockThreadFactory}, which does not actually start a thread.
 *  <p>
 *  The thread is not returned by the factory; it should exit normally when the
 *  logging framework calls {@link LogWriter#stop}.
 */
public interface ThreadFactory
{
    void startLoggingThread(LogWriter writer);
}
