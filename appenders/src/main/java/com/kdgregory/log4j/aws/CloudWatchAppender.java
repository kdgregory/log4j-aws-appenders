// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;

import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchConstants;
import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.log4j.aws.internal.shared.DefaultThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.Substitutions;
import com.kdgregory.log4j.aws.internal.shared.ThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;


/**
 *  Appender that writes to a Cloudwatch log stream.
 */
public class CloudWatchAppender extends AppenderSkeleton
{
    /**
     *  The different types of writer rotation that we support.
     */
    public enum RotationMode
    {
        /** Rotation is disabled. */
        none,

        /** Rotation is based on number of records. */
        count,

        /** Rotation is controlled by the <code>rotationInterval</code> parameter. */
        interval,

        /** Rotation happens with the first message after every hour. */
        hourly,

        /** Rotation happens with the first message after midnight UTC */
        daily
    }


    //*********************************************************************************
    // NOTE: any variables marked as protected will be replaced/examined during testing
    //*********************************************************************************

    // flag to indicate whether we need to run setup
    private volatile boolean ready = false;

    // flag to indicate whether we can keep writing; cannot be reset
    private volatile boolean closed = false;

    // factories for creating writer and thread

    protected ThreadFactory threadFactory = new DefaultThreadFactory();
    protected WriterFactory writerFactory = new WriterFactory()
    {
        @Override
        public LogWriter newLogWriter()
        {
            return new CloudWatchLogWriter(actualLogGroup, actualLogStream, batchDelay);
        }
    };

    // the current writer; initialized on first append, changed after rotation

    protected volatile LogWriter writer;

    // the last time we rotated the writer

    protected volatile long lastRotationTimestamp;

    // number of messages since we rotated the writer

    protected volatile int lastRotationCount;

    // this is strictly for testing

    protected volatile Throwable lastWriterException;

    // this object is used for synchronization of initialization and writer change

    private Object initializationLock = new Object();

    // this is used to synchronize access to the queue; queue updates are normally
    // very fast, so plain-old-synchronization should not cause undue contention

    private Object messageQueueLock = new Object();
    // these variables hold the post-substitution log-group and log-stream names
    // (mostly useful for testing)

    private String  actualLogGroup;
    private String  actualLogStream;

    // all vars below this point are configuration-controlled

    private String          logGroup;
    private String          logStream;
    private long            batchDelay;
    private RotationMode    rotationMode;
    private long            rotationInterval;
    private AtomicInteger   sequence;


    /**
     *  Base constructor: assigns default values to configuration properties.
     */
    public CloudWatchAppender()
    {
        logStream = "{startTimestamp}";
        batchDelay = 2000;
        rotationMode = RotationMode.none;
        rotationInterval = -1;
        sequence = new AtomicInteger();
    }


//----------------------------------------------------------------------------
//  Configuration
//----------------------------------------------------------------------------

    /**
     *  Sets the Cloudwatch Log Group associated with this appender.
     *  <p>
     *  You typically assign a single log group to an application, and then
     *  use multiple log streams for instances of that application.
     *  <p>
     *  Log group is required. If you do not configure the log group, the
     *  appender will be disabled and will report its misconfiguration.
     */
    public void setLogGroup(String value)
    {
        logGroup = value;
    }


    /**
     *  Returns the log group name; see {@link #setLogGroup}. Primarily used
     *  for testing.
     */
    public String getLogGroup()
    {
        return logGroup;
    }


    /**
     *  Returns the current log group name, post-substitutions. This is lazily
     *  populated, so will be <code>null</code> until the first message is written.
     */
    public String getActualLogGroup()
    {
        return actualLogGroup;
    }


    /**
     *  Sets the Cloudwatch Log Stream associated with this appender.
     *  <p>
     *  You typically create a separate log stream for each instance of the
     *  application.
     *  <p>
     *  If you do not explicitly configure the log stream, then it will use
     *  a string representation of the virtual machine start time (formatted
     *  "YYYYMMDDHHMMSS" using UTC time).
     */
    public void setLogStream(String value)
    {
        logStream = value;
    }


    /**
     *  Returns the log stream name; see {@link #setLogStream}. Primarily used
     *  for testing.
     */
    public String getLogStream()
    {
        return logStream;
    }


    /**
     *  Returns the current log stream name, post-substitutions. This is lazily
     *  populated, so will be <code>null</code> until the first message is written.
     */
    public String getActualLogStream()
    {
        return actualLogStream;
    }


    /**
     *  Sets the maximum batch delay, in milliseconds.
     *  <p>
     *  This acts as a counterbalance to batch size, for applications that have bursty
     *  logging: if the time since the first message was added to the batch exceeds
     *  this value, the batch will be sent.
     *  <p>
     *  <em>Note:</em> batch delay is only checked when calling {@link #append}. If you
     *  have infrequent log messages, you should set batch size to 1 rather than rely on
     *  this parameter.
     *  <p>
     *  The default value is 4000, which is rather arbitrarily chosen.
     */
    public void setBatchDelay(long value)
    {
        this.batchDelay = value;
        if (writer != null)
            writer.setBatchDelay(value);
    }


    /**
     *  Returns the maximum batch delay; see {@link #setMaxDelay}. Primarily used
     *  for testing.
     */
    public long getBatchDelay()
    {
        return batchDelay;
    }


    /**
     *  Sets the rule for rotating to a new log stream. Acceptable values are "none", "interval",
     *  "hourly", and "daily"; see {@link #RotationMode} for details.
     *  <p>
     *  Attempting to set an invalid mode is equivalent to "none", but will emit a warning to the
     *  Log4J internal log.
     */
    public void setRotationMode(String value)
    {
        try
        {
            this.rotationMode = RotationMode.valueOf(value);
        }
        catch (IllegalArgumentException ex)
        {
            this.rotationMode = RotationMode.none;
            LogLog.error("invalid rotationMode: " + value);
        }
    }


    /**
     *  Returns the current rotation mode.
     */
    public String getRotationMode()
    {
        return this.rotationMode.name();
    }


    /**
     *  Sets the rotation interval, in milliseconds. This parameter is used only when the
     *  <code>rotationMode</code> parameter is "interval"; to be useful, the log stream name
     *  should be timestamp-based.
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
     *  Sets the log sequence number, used by the <code>sequence</code> substitution variable.
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
//  Appender-specific methods
//----------------------------------------------------------------------------

    /**
     *  Rotates the log stream: flushes all outstanding messages to the current
     *  stream, and opens a new stream.
     */
    public void rotate()
    {
        synchronized (initializationLock)
        {
            stopWriter();
            sequence.incrementAndGet();
            startWriter();
        }
    }


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
                Substitutions subs = new Substitutions(new Date(), sequence.get());
                actualLogGroup  = CloudWatchConstants.ALLOWED_NAME_REGEX.matcher(subs.perform(logGroup)).replaceAll("");
                actualLogStream = CloudWatchConstants.ALLOWED_NAME_REGEX.matcher(subs.perform(logStream)).replaceAll("");

                writer = writerFactory.newLogWriter();
                threadFactory.startLoggingThread(writer, new UncaughtExceptionHandler()
                {
                    @Override
                    public void uncaughtException(Thread t, Throwable ex)
                    {
                        LogLog.error("CloudWatchLogWriter failure", ex);
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
     *  Closes the current writer, passing it any outstanding messages.
     */
    private void stopWriter()
    {
        synchronized (initializationLock)
        {
            if (writer == null)
                return;

            try
            {
                if (layout.getFooter() != null)
                {
                    internalAppend(LogMessage.create(layout.getFooter()));
                }
            }
            catch (Exception ex)
            {
                LogLog.error("exception while flushing writer", ex);
            }

            writer.stop();
            writer = null;
        }
    }


    private void internalAppend(LogMessage message)
    {
        if (message == null)
            return;

        if (message.size() + CloudWatchConstants.MESSAGE_OVERHEAD  >= CloudWatchConstants.MAX_BATCH_BYTES)
        {
            LogLog.warn("attempted to append a message > AWS batch size; ignored");
            return;
        }

        long now = System.currentTimeMillis();
        rotateIfNeeded(now);

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
