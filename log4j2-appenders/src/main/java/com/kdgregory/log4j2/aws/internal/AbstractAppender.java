// Copyright (c) Keith D Gregory
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.kdgregory.log4j2.aws.internal;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.core.LogEvent;

import com.kdgregory.logging.aws.internal.AbstractWriterStatistics;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.factories.ThreadFactory;
import com.kdgregory.logging.common.factories.WriterFactory;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.common.util.InternalLogger;
import com.kdgregory.logging.common.util.RotationMode;


/**
 *  Common implementation code that's shared between appenders.
 */
public abstract class AbstractAppender<
    AppenderConfigType extends AbstractAppenderConfig,
    AppenderStatsType extends AbstractWriterStatistics,
    WriterConfigType>
extends org.apache.logging.log4j.core.appender.AbstractAppender
{
    // factories for creating writer and thread
    // note: these must be explicitly assigned in subclass constructor, because they
    //       will almost certainly be inner classes that require access to the outer
    //       instance, and you can't create those in a super() call

    protected ThreadFactory threadFactory;
    protected WriterFactory<WriterConfigType,AppenderStatsType> writerFactory;

    // used for internal logging: we manage this and expose it to our subclasses

    protected InternalLogger internalLogger;

    // the appender stats object; we keep the reference because we call writer factory

    protected AppenderStatsType appenderStats;

    // the last time we rotated the writer

    protected volatile long lastRotationTimestamp;

    // number of messages since we last rotated the writer (used for count-based rotation)

    protected volatile int messagesSinceLastRotation;

    // this keeps track of the number of rotations (it must be explicitly initialized)

    protected AtomicInteger sequence = new AtomicInteger();

    // this object is used for synchronization of initialization and writer change

    private Object initializationLock = new Object();

    // this object is used to synchronize the critical section in append()

    private Object appendLock = new Object();

    // this is provided to us by subclass

    protected AppenderConfigType config;

    // holding this separately because of Log4J's "setters? we don't need no stinkin' setters!"
    // approach to configuration
    protected DiscardAction discardAction;

    // rotation mode is something that we care about, so will pull it out of config
    protected RotationMode rotationMode;
    protected long rotationInterval;

    // the current writer; initialized on first append, changed after rotation or error

    protected volatile LogWriter writer;


    protected AbstractAppender(
        String name,
        ThreadFactory threadFactory,
        WriterFactory<WriterConfigType,AppenderStatsType> writerFactory,
        AppenderStatsType appenderStats,
        AppenderConfigType config,
        InternalLogger providedInternalLogger)
    {
        super(name, config.getFilter(), config.getLayout());
        this.config = config;
        this.threadFactory = threadFactory;
        this.writerFactory = writerFactory;
        this.appenderStats = appenderStats;
        this.internalLogger = (providedInternalLogger != null)
                            ? providedInternalLogger
                            : new Log4J2InternalLogger(this);

        this.sequence.set(config.getSequence());

        discardAction = DiscardAction.lookup(config.getDiscardAction());
        if (discardAction == null)
        {
            internalLogger.error("invalid discard action: " + config.getDiscardAction(), null);
            discardAction = DiscardAction.oldest;
        }

        rotationMode = RotationMode.lookup(config.getRotationMode());
        if (rotationMode == null)
        {
            internalLogger.error("invalid rotation mode: " + config.getRotationMode(), null);
            rotationMode = RotationMode.none;
        }

        rotationInterval = config.getRotationInterval();
    }


//----------------------------------------------------------------------------
//  Other accessors
//----------------------------------------------------------------------------

    /**
     *  Returns the appender statistics object.
     */
    public AppenderStatsType getAppenderStatistics()
    {
        return appenderStats;
    }


    /**
     *  Exposes configuration for testing.
     */
    public AppenderConfigType getConfig()
    {
        return config;
    }

//----------------------------------------------------------------------------
//  Appender implementation
//----------------------------------------------------------------------------


    @Override
    public void start()
    {
        synchronized (initializationLock)
        {
            if (isStarted())
            {
                // someone else already initialized us
                return;
            }

            startWriter();
            registerStatisticsBean();
        }
        super.start();
    }


    @Override
    public void stop()
    {
        // TODO - the framework should never call this code; this exception
        //        is here to catch any copy-paste errors in tests, and can
        //        be removed after unit tests are fully written
        throw new IllegalStateException("stop() called -- don't do that");
    }


    @Override
    public boolean stop(long timeout, TimeUnit timeUnit)
    {
        synchronized (initializationLock)
        {
            if (writer != null)
            {
                stopWriter();
            }

            unregisterStatisticsBean();
        }

        return super.stop(timeout, timeUnit);
    }


    @Override
    public void append(LogEvent event)
    {
        if (! isStarted())
        {
            internalLogger.warn("append called before appender was started");
            return;
        }

        String message = null;
        try
        {
            message = getConfig().getLayout().toSerializable(event);
        }
        catch (Exception ex)
        {
            internalLogger.error("unable to apply layout", ex);
            return;
        }

        try
        {
            internalAppend(new LogMessage(event.getTimeMillis(), message));
        }
        catch (Exception ex)
        {
            internalLogger.error("unable to append event", ex);
        }
    }


//----------------------------------------------------------------------------
//  Methods that may be exposed by subclasses
//----------------------------------------------------------------------------

    /**
     *  Rotates the log writer. This will create in a new writer thread, with
     *  the pre-rotation writer shutting down after processing all messages in
     *  its queue.
     */
    protected void rotate()
    {
        synchronized (initializationLock)
        {
            stopWriter();
            sequence.incrementAndGet();
            startWriter();
        }
    }

//----------------------------------------------------------------------------
//  Subclass hooks
//----------------------------------------------------------------------------

    /**
     *  Called just before a writer is created, so that the subclass can
     *  perform substitutions on the configuration.
     */
    protected abstract WriterConfigType generateWriterConfig();

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    /**
     *  Called by {@link #initialize} and also {@link #rotate}, to switch to a new
     *  writer. Does not close the old writer, if any.
     */
    private void startWriter()
    {
        synchronized (initializationLock)
        {
            try
            {
                writer = writerFactory.newLogWriter(generateWriterConfig(), appenderStats, internalLogger);
                if (config.isSynchronous())
                {
                    writer.initialize();
                }
                else
                {
                    threadFactory.startLoggingThread(writer, config.isUseShutdownHook(), new UncaughtExceptionHandler()
                    {
                        @Override
                        public void uncaughtException(Thread t, Throwable ex)
                        {
                            internalLogger.error("unhandled exception in writer", ex);
                            appenderStats.setLastError(null, ex);
                            writer = null;
                        }
                    });

                    if (! writer.waitUntilInitialized(60000))
                    {
                        internalLogger.error("writer initialization timed out", null);
                    }
                }

                if (getLayout().getHeader() != null)
                {
                    String header = new String(getLayout().getHeader(), "UTF-8");
                    if (header.length() > 0)
                    {
                        internalAppend(new LogMessage(System.currentTimeMillis(), header));
                    }
                }

                // note the header doesn't contribute to the message count

                lastRotationTimestamp = System.currentTimeMillis();
                messagesSinceLastRotation = 0;
            }
            catch (Exception ex)
            {
                internalLogger.error("exception while initializing writer", ex);
            }
        }
    }


    /**
     *  Closes the current writer.
     */
    private void stopWriter()
    {
        synchronized (initializationLock)
        {
            try
            {
                if (writer == null)
                    return;

                if (getLayout().getFooter() != null)
                {
                    String header = new String(getLayout().getFooter(), "UTF-8");
                    if (header.length() > 0)
                    {
                        internalAppend(new LogMessage(System.currentTimeMillis(), header));
                    }
                }

                writer.stop();

                if (config.isSynchronous())
                {
                    writer.cleanup();
                }
            }
            catch (Exception ex)
            {
                internalLogger.error("exception while shutting down writer", ex);
            }

            writer = null;
        }
    }


    /**
     *  Registers the appender statistics with JMX. Logs but otherwise ignores failure.
     *  <p>
     *  The name for the bean is consistent with the Log4J <code>LayoutDynamicMBean</code>,
     *  so that it will appear in the hierarchy under the appender.
     *  <p>
     *  Note: this method is protected so that it can be avoided during unit tests.
     */
    protected void registerStatisticsBean()
    {
        // TODO - implement
    }


    /**
     *  Unregisters the appender statistics from JMX. This is called when the appender
     *  is closed. Logs but otherwise ignores failure.
     *  <p>
     *  Note: this method is protected so that it can be avoided during unit tests.
     */
    protected void unregisterStatisticsBean()
    {
        // TODO - implement
    }



    private void internalAppend(LogMessage message)
    {
        if (message == null)
        {
            internalLogger.error("internal error: message was null", null);
            return;
        }

        synchronized (appendLock)
        {
            if (writer.isMessageTooLarge(message))
            {
                internalLogger.warn("attempted to append a message > AWS batch size; ignored");
                return;
            }

            long now = System.currentTimeMillis();
            if (shouldRotate(now))
            {
                long secondsSinceLastRotation = (now - lastRotationTimestamp) / 1000;
                internalLogger.debug("rotating: messagesSinceLastRotation = " + messagesSinceLastRotation + ", secondsSinceLastRotation = " + secondsSinceLastRotation);
                rotate();
            }

            if (writer == null)
            {
                internalLogger.error("appender not properly configured: writer is null", null);
            }
            else
            {
                writer.addMessage(message);
                messagesSinceLastRotation++;
            }
        }

        // by processing the batch outside of the appendLock (and relying on writer internal
        // synchronization), we may end up with a single batch with more than one record (but
        // it's unlikely)
        if (getConfig().isSynchronous())
        {
            writer.processBatch(System.currentTimeMillis());
        }
    }


    private boolean shouldRotate(long now)
    {
        switch (rotationMode)
        {
            case none:
                return false;
            case count:
                return (rotationInterval > 0) && (messagesSinceLastRotation >= rotationInterval);
            case interval:
                return (rotationInterval > 0) && ((now - lastRotationTimestamp) > rotationInterval);
            case hourly:
                return (lastRotationTimestamp / 3600000) < (now / 3600000);
            case daily:
                return (lastRotationTimestamp / 86400000) < (now / 86400000);
            default:
                return false;
        }
    }
}
