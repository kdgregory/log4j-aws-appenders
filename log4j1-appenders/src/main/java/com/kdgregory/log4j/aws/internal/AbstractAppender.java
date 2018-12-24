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

package com.kdgregory.log4j.aws.internal;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;

import com.kdgregory.logging.aws.internal.AbstractWriterStatistics;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.factories.ThreadFactory;
import com.kdgregory.logging.common.factories.WriterFactory;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.common.util.RotationMode;


/**
 *  Common implementation code that's shared between appenders.
 *  <p>
 *  For the most part, appenders have the same behavior: they initialize, transform
 *  log messages, and shut down. Most of the code to do that lives here, with a few
 *  hooks that are implemented in the appender proper.
 *  <p>
 *  Some behaviors, such as log rotation, are implemented here even if they are not
 *  supported by all appenders. The appenders that do not support those behaviors are
 *  responsible for disabling them. For example, an appender that does not support log
 *  rotation should throw if {@link #setRotationMode} is called.
 *  <p>
 *  Most of the member variables defined by this class are protected. This is intended
 *  to support testing. If you decide to subclass and access those variables, well,
 *  this is an internal class: they may go away.
 *  <p>
 *  Note: Log4J synchronizes calls to appenders by logger. In typical usage, this means
 *  that all calls will be synchronized and we shouldn't synchronize outself. However,
 *  it appears that there's no synchronization for loggers that use the same appender
 *  but do not share an appender list. For that reason, we use internal synchronization
 *  of critical sections.
 */
public abstract class AbstractAppender<WriterConfigType,AppenderStatsType extends AbstractWriterStatistics,AppenderStatsMXBeanType>
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
    protected WriterFactory<WriterConfigType,AppenderStatsType> writerFactory;

    // used for internal logging: we manage this and expose it to our subclasses

    protected Log4JInternalLogger logger;

    // the appender stats object; we keep the reference because we call writer factory

    protected AppenderStatsType appenderStats;

    // the MX bean type for the appender stats object

    private Class<AppenderStatsMXBeanType> appenderStatsMXBeanClass;

    // the current writer; initialized on first append, changed after rotation or error

    protected volatile LogWriter writer;

    // the last time we rotated the writer

    protected volatile long lastRotationTimestamp;

    // number of messages since we last rotated the writer (used for count-based rotation)

    protected volatile int messagesSinceLastRotation;

    // this object is used for synchronization of initialization and writer change

    private Object initializationLock = new Object();

    // this object is used to synchronize the critical section in append()
    private Object appendLock = new Object();

    // all member vars below this point are shared configuration

    protected long                  batchDelay;
    protected int                   discardThreshold;
    protected DiscardAction         discardAction;
    protected volatile RotationMode rotationMode = RotationMode.none;
    protected volatile long         rotationInterval;
    protected AtomicInteger         sequence;
    protected String                clientFactory;
    protected String                clientEndpoint;

//----------------------------------------------------------------------------
//  Constructor
//----------------------------------------------------------------------------

    public AbstractAppender(
        ThreadFactory threadFactory,
        WriterFactory<WriterConfigType,AppenderStatsType> writerFactory,
        AppenderStatsType appenderStats,
        Class<AppenderStatsMXBeanType> appenderStatsMXBeanClass)
    {
        this.threadFactory = threadFactory;
        this.writerFactory = writerFactory;
        this.appenderStats = appenderStats;
        this.appenderStatsMXBeanClass = appenderStatsMXBeanClass;

        this.logger = new Log4JInternalLogger(getClass().getSimpleName());

        batchDelay = 2000;
        discardThreshold = 10000;
        discardAction = DiscardAction.oldest;
        rotationMode = RotationMode.none;
        rotationInterval = -1;
        sequence = new AtomicInteger();
    }

//----------------------------------------------------------------------------
//  Overrides of AppenderSkeleton
//----------------------------------------------------------------------------

    @Override
    public void setName(String name)
    {
        super.setName(name);
        logger.setAppenderName(name);
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
        {
            writer.setBatchDelay(value);
        }
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
     *  Sets the number of unsent messages that will trigger message discard. A high
     *  value is useful when network connectivity is intermittent and/or overall AWS
     *  communication is causing throttling. However, a value that is too high may
     *  cause out-of-memory errors.
     *  <p>
     *  The default, 10,000, is based on the assumptions that (1) each message will be
     *  1k or less, and (2) any app that uses remote logging can afford 10MB.
     *  <p>
     *  Note: at present, discard threshold cannot be changed after creating a log
     *  writer. For appenders that rotate logs, a new configuration value will be
     *  recognized at the time of rotation.
     */
    public void setDiscardThreshold(int value)
    {
        this.discardThreshold = value;
        if (writer != null)
        {
            writer.setDiscardThreshold(value);
        }
    }


    /**
     *  Returns the configured discard threshold.
     */
    public int getDiscardThreshold()
    {
        return discardThreshold;
    }


    /**
     *  Sets the action to take when the number of unsent messages exceeds the discard
     *  threshold. Values are "none" (retain all messages), "oldest" (discard oldest
     *  messages), and "newest" (discard most recent messages).
     *  <p>
     *  The default is "oldest". Attempting to set an incorrect value will throw a
     *  configuration error.
     *  <p>
     *  Note: at present, discard action cannot be changed after creating a log
     *  writer. For appenders that rotate logs, a new configuration value will be
     *  recognized at the time of rotation.
     */
    public void setDiscardAction(String value)
    {
        DiscardAction tmpDiscardAction = DiscardAction.lookup(value);
        if (tmpDiscardAction == null)
        {
            logger.error("invalid discard action: " + value, null);
            return;
        }

        if (writer != null)
        {
            writer.setDiscardAction(tmpDiscardAction);
        }

        discardAction = tmpDiscardAction;
    }


    /**
     *  Returns the configured discard action.
     */
    public String getDiscardAction()
    {
        return discardAction.toString();
    }


    /**
     *  Sets the rule for log stream rotation, for those appenders that support rotation.
     *  See {@link com.kdgregory.logging.common.util.RotationMode} for values. Invalid
     *  values are logged and translated to "none".
     *  <p>
     *  Note: this method must be explicitly overridden by appenders that support rotation.
     */
    protected void setRotationMode(String value)
    {
        RotationMode newMode = RotationMode.lookup(value);
        if (newMode == null)
        {
            newMode = RotationMode.none;
            logger.error("invalid rotation mode: " + value + ", setting to " + newMode, null);
        }
        this.rotationMode = newMode;
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


    /**
     *  Sets a static AWS client factory method, which will be called instead of
     *  the writer's internal client factory. This may be useful if the default
     *  client is not appropriate (for example, to set a non-default region).
     *  <p>
     *  The passed string is of the form <code>com.example.Classname.methodName</code>.
     *  If this does not reference a class/method on the classpath then writer
     *  initialization will fail.
     *  <p>
     *  Calling this method after the writer has been initialized will have no
     *  effect (except for those appenders that rotate logs, in which case it
     *  will apply to the post-rotate writer).
     */
    public void setClientFactory(String value)
    {
        clientFactory = value;
    }


    /**
     *  Returns the current AWS client factory class/method name. Will be null
     *  if the factory hasn't been set.
     */
    public String getClientFactory()
    {
        return clientFactory;
    }


    /**
     *  Sets the service endpoint. This is intended for use with older AWS SDK
     *  versions that do not provide client factories and default to us-east-1,
     *  although it can be used for newer releases when you want to override the
     *  default region provider. The endpoint setting is ignored if you specify
     *  a client factory.
     */
    public void setClientEndpoint(String value)
    {
        this.clientEndpoint = value;
    }


    /**
     *  Returns the current service endpoint. Will be null if the endpoint has
     *  not been explicitly set.
     */
    public String getClientEndpoint()
    {
        return clientEndpoint;
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
     *  Returns whether or not the appender has been closed.
     */
    public boolean isClosed()
    {
        return closed;
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

        LogMessage logMessage;
        try
        {
            logMessage = Utils.convertToLogMessage(event, getLayout());
        }
        catch (Exception ex)
        {
            logger.error("unable to apply layout", ex);
            return;
        }

        try
        {
            internalAppend(logMessage);
        }
        catch (Exception ex)
        {
            logger.error("unable to append event", ex);
        }
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
            unregisterStatisticsBean();
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
            registerStatisticsBean();
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
                writer = writerFactory.newLogWriter(generateWriterConfig(), appenderStats, logger);
                threadFactory.startLoggingThread(writer, new UncaughtExceptionHandler()
                {
                    @Override
                    public void uncaughtException(Thread t, Throwable ex)
                    {
                        logger.error("unhandled exception in writer", ex);
                        appenderStats.setLastError(null, ex);
                        writer = null;
                    }
                });

                if (layout.getHeader() != null)
                {
                    internalAppend(new LogMessage(System.currentTimeMillis(), layout.getHeader()));
                }

                // note the header doesn't contribute to the message count

                lastRotationTimestamp = System.currentTimeMillis();
                messagesSinceLastRotation = 0;
            }
            catch (Exception ex)
            {
                logger.error("exception while initializing writer", ex);
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

                if (layout.getFooter() != null)
                {
                    internalAppend(new LogMessage(System.currentTimeMillis(), layout.getFooter()));
                }

                writer.stop();
            }
            catch (Exception ex)
            {
                logger.error("exception while shutting down writer", ex);
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
        JMXManager.getInstance().addStatsBean(getName(), appenderStats, appenderStatsMXBeanClass);
    }


    /**
     *  Unregisters the appender statistics from JMX. This is called when the appender
     *  is closed. Logs but otherwise ignores failure.
     *  <p>
     *  Note: this method is protected so that it can be avoided during unit tests.
     */
    protected void unregisterStatisticsBean()
    {
        JMXManager.getInstance().removeStatsBean(getName());
    }


    private void internalAppend(LogMessage message)
    {
        if (message == null)
        {
            logger.error("internal error: message was null", null);
            return;
        }

        synchronized (appendLock)
        {
            if (writer.isMessageTooLarge(message))
            {
                logger.warn("attempted to append a message > AWS batch size; ignored");
                return;
            }

            long now = System.currentTimeMillis();
            if (shouldRotate(now))
            {
                long secondsSinceLastRotation = (now - lastRotationTimestamp) / 1000;
                logger.debug("rotating: messagesSinceLastRotation = " + messagesSinceLastRotation + ", secondsSinceLastRotation = " + secondsSinceLastRotation);
                rotate();
            }

            if (writer == null)
            {
                logger.error("appender not properly configured: writer is null", null);
            }
            else
            {
                writer.addMessage(message);
                messagesSinceLastRotation++;
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
