// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.shared;


/**
 *  A {@link ThreadFactory} used for testing: it doesn't actually start a thread.
 */
public class NullThreadFactory implements ThreadFactory
{
    @Override
    public void startLoggingThread(LogWriter writer)
    {
        // nuthin happenin here
    }
}
