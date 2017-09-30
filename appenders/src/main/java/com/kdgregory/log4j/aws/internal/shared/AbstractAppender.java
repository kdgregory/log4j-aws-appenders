// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.shared;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;


/**
 *  Common implementation code that's shared between appenders.
 *  <p>
 *  For the most part, appenders have the same behavior: they initialize, transform
 *  log messages, and shut down. Most of the code to do that lives here, with a few
 *  hooks that are implemented in the appender proper.
 *  <p>
 *  Note that some behaviors, such as log rotation, are implemented here even if they
 *  are not supported by all appenders. The appenders that do not support those
 *  behaviors are responsible for disabling them. For example, an appender that does
 *  not support log rotation should throw if {@link #setRotationMode} is called.
 *  <p>
 *  Note that most of the member variables defined by this class are protected. This
 *  is primarily to support testing, but also follows my philosophy of related classes:
 *  there is no logical difference between direct access properties; in both cases
 *  you're defining an API. Of course, this is a public API for an internal class,
 *  so any application code that touches these variables should not be surprised if
 *  they cease to exist.
 */
public abstract class AbstractAppender<WriterConfigType>
extends AppenderSkeleton
{
    // flag to indicate whether we need to run setup

    private volatile boolean ready = false;

    // flag to indicate whether we can keep writing

    private volatile boolean closed = false;

    // factories for creating writer and thread
    // note: these must be explicitly assigned in subclass constructor, because they
    //       will almost certainly be inner classes that require access to the outer
    //       instance, and you can't create those in a super() call

    protected ThreadFactory threadFactory;
    protected WriterFactory<WriterConfigType> writerFactory;

    // the current writer; initialized on first append, changed after rotation or error

    protected volatile LogWriter writer;

    // the last time we rotated the writer

    protected volatile long lastRotationTimestamp;

    // number of messages since we last rotated the writer

    protected volatile int lastRotationCount;

    // this is strictly for testing

    protected volatile Throwable lastWriterException;

    // this object is used for synchronization of initialization and writer change

    private Object initializationLock = new Object();

    // this is used to synchronize access to the queue; queue updates are normally
    // very fast, so plain-old-synchronization should not cause undue contention

    private Object messageQueueLock = new Object();

    // all member vars below this point are shared configuration

    protected long            batchDelay;
    protected RotationMode    rotationMode;
    protected long            rotationInterval;
    protected AtomicInteger   sequence;


//----------------------------------------------------------------------------
//  Constructor
//----------------------------------------------------------------------------

    public AbstractAppender(ThreadFactory threadFactory, WriterFactory<WriterConfigType> writerFactory)
    {
        this.threadFactory = threadFactory;
        this.writerFactory = writerFactory;

        batchDelay = 2000;
        rotationMode = RotationMode.none;
        rotationInterval = -1;
        sequence = new AtomicInteger();
    }


//----------------------------------------------------------------------------
//  Shared Configuration Properties
//----------------------------------------------------------------------------

    /**
     *  Sets the maximum batch delay, in milliseconds.
     *  <p>
     *  The writer attempts to gather multiple logging messages into a batch, to
     *  reduce communication with the service. The batch delay controls the time
     *  that a message will remain in-memory while the writer builds this batch.
     *  In a low-volume environment it will be the main determinant of when the
     *  batch is sent; in a high volume environment it's likely that the maximum
     *  request size will be reached before the batch delay expires.
     *  <p>
     *  The default value is 2000, which is rather arbitrarily chosen.
     */
    public void setBatchDelay(long value)
    {
        this.batchDelay = value;
        if (writer != null)
            writer.setBatchDelay(value);
    }


    /**
     *  Returns the maximum batch delay; see {@link #setBatchDelay}. Primarily used
     *  for testing.
     */
    public long getBatchDelay()
    {
        return batchDelay;
    }


    /**
     *  Sets the rule for log stream rotation, for those appenders that support rotation.
     *  See {@link RotationMode} for allowed values.
     *  <p>
     *  Attempting to set an invalid mode is equivalent to "none", but will emit a warning to the
     *  Log4J internal log.
     */
    public void setRotationMode(String value)
    {
        this.rotationMode = RotationMode.lookup(value);
    }


    /**
     *  Returns the current rotation mode.
     */
    public String getRotationMode()
    {
        return this.rotationMode.name();
    }


    /**
     *  Sets the rotation interval, for those appenders that support rotation. This parameter
     *  is valid only when the <code>rotationMode</code> parameter is "interval" or "count":
     *  for the former, it's the number of milliseconds between rotations, for the latter the
     *  number of messages.
     *  <p>
     *  If using interval rotation, you should include <code>{timestamp}</code> in the log stream
     *  name. If using counted rotation, you should include <code>{sequence}</code>.
     */
    public void setRotationInterval(long value)
    {
        this.rotationInterval = value;
    }


    /**
     *  Returns the current rotation interval.
     */
    public long getRotationInterval()
    {
        return rotationInterval;
    }


    /**
     *  Sets the log sequence number, used by the <code>{sequence}</code> substitution variable.
     */
    public void setSequence(int value)
    {
        sequence.set(value);
    }


    /**
     *  Returns the current log sequence number.
     */
    public int getSequence()
    {
        return sequence.get();
    }


//----------------------------------------------------------------------------
//  Appender overrides
//----------------------------------------------------------------------------

    @Override
    protected void append(LoggingEvent event)
    {
        if (closed)
        {
            throw new IllegalStateException("appender is closed");
        }

        if (! ready)
        {
            initialize();
        }

        internalAppend(LogMessage.create(event, getLayout()));
    }


    @Override
    public void close()
    {
        synchronized (initializationLock)
        {
            if (closed)
            {
                // someone already closed us
                return;
            }

            stopWriter();
            closed = true;
        }
    }


    @Override
    public boolean requiresLayout()
    {
        return true;
    }


//----------------------------------------------------------------------------
//  Methods that may be exposed by subclasses
//----------------------------------------------------------------------------

    /**
     *  Rotates the log writer. This must be exposed by appenders that support
     *  log rotation; they can simply delegate to the super implementation.
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


    /**
     *  Called {@link #append} to ensure that we don't have a single message
     *  that violates AWS batching rules.
     */
    protected abstract boolean isMessageTooLarge(LogMessage message);


//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    /**
     *  Called by {@link #append} to lazily initialize appender.
     */
    private void initialize()
    {
        synchronized (initializationLock)
        {
            if (ready)
            {
                // someone else already initialized us
                return;
            }

            startWriter();
            ready = true;
        }
    }


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
                writer = writerFactory.newLogWriter(generateWriterConfig());
                threadFactory.startLoggingThread(writer, new UncaughtExceptionHandler()
                {
                    @Override
                    public void uncaughtException(Thread t, Throwable ex)
                    {
                        LogLog.error("LogWriter failure", ex);
                        writer = null;
                        lastWriterException = ex;
                    }
                });

                if (layout.getHeader() != null)
                {
                    internalAppend(LogMessage.create(layout.getHeader()));
                }

                lastRotationTimestamp = System.currentTimeMillis();
                lastRotationCount = 0;
            }
            catch (Exception ex)
            {
                LogLog.error("exception while initializing writer", ex);
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
            if (writer == null)
                return;

            if (layout.getFooter() != null)
            {
                internalAppend(LogMessage.create(layout.getFooter()));
            }

            writer.stop();
            writer = null;
        }
    }


    private void internalAppend(LogMessage message)
    {
        if (message == null)
            return;

        if (isMessageTooLarge(message))
        {
            LogLog.warn("attempted to append a message > AWS batch size; ignored");
            return;
        }

        rotateIfNeeded(System.currentTimeMillis());

        synchronized (messageQueueLock)
        {
            if (writer == null)
            {
                LogLog.warn("appender not properly configured: writer is null");
            }
            else
            {
                writer.addMessage(message);
                lastRotationCount++;
            }
        }
    }


    private void rotateIfNeeded(long now)
    {
        // double-checked locking: avoid contention for first check, but make sure we don't do things twice
        if (shouldRotate(now))
        {
            synchronized (initializationLock)
            {
                if (shouldRotate(now))
                {
                    rotate();
                }
            }
        }
    }


    private boolean shouldRotate(long now)
    {
        switch (rotationMode)
        {
            case none:
                return false;
            case count:
                return (rotationInterval > 0) && (lastRotationCount >= rotationInterval);
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
