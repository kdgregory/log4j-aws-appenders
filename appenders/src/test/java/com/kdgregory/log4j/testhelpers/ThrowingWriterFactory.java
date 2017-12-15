// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.testhelpers;

import java.util.concurrent.CountDownLatch;

import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;


/**
 *  This factory creates a LogWriter that throws on its second invocation.
 *  It's used to test the uncaught exception handling in the appender.
 */
public class ThrowingWriterFactory<T> implements WriterFactory<T>
{
        @Override
        public LogWriter newLogWriter(T ignored)
        {
            return new LogWriter()
            {
                private CountDownLatch appendLatch = new CountDownLatch(2);

                @Override
                public void run()
                {
                    try
                    {
                        appendLatch.await();
                        throw new TestingException("danger, danger Will Robinson!");
                    }
                    catch (InterruptedException ignored2)
                    { /* nothing to do */ }
                }

                @Override
                public void stop()
                {
                    // not used
                }

                @Override
                public void setBatchDelay(long value)
                {
                    // not used
                }

                @Override
                public void addMessage(LogMessage message)
                {
                    appendLatch.countDown();
                }
            };
        }
}
