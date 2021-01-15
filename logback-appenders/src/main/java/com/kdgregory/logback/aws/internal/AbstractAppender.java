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

package com.kdgregory.logback.aws.internal;

import java.lang.Thread.UncaughtExceptionHandler;

import com.kdgregory.logging.aws.internal.AbstractWriterConfig;
import com.kdgregory.logging.aws.internal.AbstractWriterStatistics;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.factories.ThreadFactory;
import com.kdgregory.logging.common.factories.WriterFactory;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.common.util.InternalLogger;

import ch.qos.logback.access.spi.IAccessEvent;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;


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
 *  Note: this appender is built on <code>UnsynchronizedAppenderBase</code>, which is
 *  perhaps unnecessary (if you're worried about contention you shouldn't be doing a
 *  lot of logging). However, there are some critical sections within {@link #append},
 *  and these are protected by internal lock objects.
 */
public abstract class AbstractAppender
    <
    WriterConfigType extends AbstractWriterConfig<WriterConfigType>,
    AppenderStatsType extends AbstractWriterStatistics,
    AppenderStatsMXBeanType,
    LogbackEventType
    >
extends UnsynchronizedAppenderBase<LogbackEventType>
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

    // the MX bean type for the appender stats object

    private Class<AppenderStatsMXBeanType> appenderStatsMXBeanClass;

    // the current writer; initialized on first append, changed after rotation or error

    protected volatile LogWriter writer;

    // layout is managed by this class, not superclass

    protected Layout<LogbackEventType>  layout;

    // this object is used for synchronization of initialization and writer change

    private Object initializationLock = new Object();

    // all member vars below this point are shared configuration

    protected boolean                   synchronous;
    protected long                      batchDelay;
    protected boolean                   truncateOversizeMessages;
    protected int                       discardThreshold;
    protected DiscardAction             discardAction;
    protected String                    assumedRole;
    protected String                    clientFactory;
    protected String                    clientRegion;
    protected String                    clientEndpoint;
    protected boolean                   useShutdownHook;


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

        this.internalLogger = new LogbackInternalLogger(this);

        batchDelay = 2000;
        truncateOversizeMessages = true;
        discardThreshold = 10000;
        discardAction = DiscardAction.oldest;
        useShutdownHook = true;
    }

//----------------------------------------------------------------------------
//  Configuration
//----------------------------------------------------------------------------

    @Override
    public void setName(String name)
    {
        super.setName(name);
    }


    /**
     *  Sets the layout manager for this appender. Note that we do not use an
     *  <code>Encoder</code>; it is a knob that doesn't need to be turned.
     */
    public void setLayout(Layout<LogbackEventType> layout)
    {
        this.layout = layout;
    }


    /**
     *  Sets the <code>synchronous</code> configuration property. This can only
     *  be done at the time of configuration, not once the appender is operating.
     */
    public void setSynchronous(boolean value)
    {
        if (writer != null)
            throw new IllegalStateException("can not set synchronous mode once writer created");

        this.synchronous = value;

        if (this.synchronous)
        {
            this.batchDelay = 0;
        }
    }


    /**
     *  Returns the <code>synchronous</code> configuration property.
     */
    public boolean getSynchronous()
    {
        return this.synchronous;
    }


    /**
     *  Sets the <code>batchDelay</code> configuration property.
     */
    public void setBatchDelay(long value)
    {
        if (this.synchronous)
            return;

        this.batchDelay = value;
        if (writer != null)
        {
            writer.setBatchDelay(value);
        }
    }


    /**
     *  Returns the <code>batchDelay</code> configuration property.
     */
    public long getBatchDelay()
    {
        return batchDelay;
    }


    /**
     *  Sets the <code>truncateOversizeMessages</code> configuration property.
     */
    public void setTruncateOversizeMessages(boolean value)
    {
        this.truncateOversizeMessages = value;
    }


    /**
     *  Returns the <code>truncateOversizeMessages</code> configuration property.
     */
    public boolean getTruncateOversizeMessages()
    {
        return this.truncateOversizeMessages;
    }


    /**
     *  Sets the <code>discardThreshold</code> configuration property.
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
     *  Returns the <code>discardThreshold</code> configuration property.
     */
    public int getDiscardThreshold()
    {
        return discardThreshold;
    }


    /**
     *  Sets the <code>discardAction</code> configuration property.
     */
    public void setDiscardAction(String value)
    {
        DiscardAction tmpDiscardAction = DiscardAction.lookup(value);
        if (tmpDiscardAction == null)
        {
            internalLogger.error("invalid discard action: " + value, null);
            return;
        }

        if (writer != null)
        {
            writer.setDiscardAction(tmpDiscardAction);
        }

        discardAction = tmpDiscardAction;
    }


    /**
     *  Returns the <code>discardAction</code> configuration property.
     */
    public String getDiscardAction()
    {
        return discardAction.toString();
    }


    /**
     *  Sets the <code>assumedRole</code> configuration property.
     *  <p>
     *  Calling this method after the writer has been initialized will have no
     *  effect until the next log rotation.
     */
    public void setAssumedRole(String value)
    {
        assumedRole = value;
    }

    /**
     *  Returns the <code>assumedRole</code> configuration property.
     */
    public String getAssumedRole()
    {
        return assumedRole;
    }


    /**
     *  Sets the <code>discardAction</code> configuration property.
     *  <p>
     *  Calling this method after the writer has been initialized will have no
     *  effect until the next log rotation.
     */
    public void setClientFactory(String value)
    {
        clientFactory = value;
    }


    /**
     *  Returns the <code>clientFactory</code> configuration property.
     */
    public String getClientFactory()
    {
        return clientFactory;
    }


    /**
     *  Sets the <code>clientRegion</code> configuration property.
     */
    public void setClientRegion(String value)
    {
        this.clientRegion = value;
    }


    /**
     *  Returns the <code>clientRegion</code> configuration property.
     */
    public String getClientRegion()
    {
        return clientRegion;
    }


    /**
     *  Sets the <code>clientEndpoint</code> configuration property.
     */
    public void setClientEndpoint(String value)
    {
        this.clientEndpoint = value;
    }


    /**
     *  Returns the <code>clientEndpoint</code> configuration property.
     */
    public String getClientEndpoint()
    {
        return clientEndpoint;
    }


    /**
     *  Sets the <code>useShutdownHook</code> configuration property.
     */
    public void setUseShutdownHook(boolean value)
    {
        this.useShutdownHook = value;
    }


    /**
     *  Returns the <code>useShutdownHook</code> configuration property.
     */
    public boolean getUseShutdownHook()
    {
        return this.useShutdownHook;
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
        synchronized (initializationLock)
        {
            if (writer != null)
            {
                stopWriter();
            }

            unregisterStatisticsBean();
        }

        super.stop();
    }


    @Override
    protected void append(LogbackEventType event)
    {
        if (! isStarted())
        {
            internalLogger.warn("append called before appender was started");
            return;
        }

        // it would be nice if Logback events had a shared superinterface,
        // but they don't so we need to get ugly to get timestamp
        long timestamp = (event instanceof ILoggingEvent) ? ((ILoggingEvent)event).getTimeStamp()
                       : (event instanceof IAccessEvent)  ? ((IAccessEvent)event).getTimeStamp()
                       : System.currentTimeMillis();

        String message = null;
        try
        {
            message = layout.doLayout(event);
        }
        catch (Exception ex)
        {
            internalLogger.error("unable to apply layout", ex);
            return;
        }

        try
        {
            internalAppend(new LogMessage(timestamp, message));
        }
        catch (Exception ex)
        {
            internalLogger.error("unable to append event", ex);
        }
    }

//----------------------------------------------------------------------------
//  Subclass hooks
//----------------------------------------------------------------------------

    /**
     *  Called as part of initialization. Subclass should provide a config
     *  object that is populated with everything the subclass controls, and
     *  with all substitutions applied. The abstract class will populate
     *  with everything that it controls (eg, connection info).
     */
    protected abstract WriterConfigType generateWriterConfig();

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    /**
     *  Called by {@link #initialize} to create the writer.
     */
    private void startWriter()
    {
        WriterConfigType config = generateWriterConfig()
                                  .setTruncateOversizeMessages(truncateOversizeMessages)
                                  .setSynchronousMode(synchronous)
                                  .setBatchDelay(batchDelay)
                                  .setDiscardThreshold(discardThreshold)
                                  .setDiscardAction(discardAction)
                                  .setClientFactoryMethod(clientFactory)
                                  .setAssumedRole(assumedRole)
                                  .setClientRegion(clientRegion)
                                  .setClientEndpoint(clientEndpoint);

        synchronized (initializationLock)
        {
            try
            {
                writer = writerFactory.newLogWriter(config, appenderStats, internalLogger);
                threadFactory.startWriterThread(writer, useShutdownHook, new UncaughtExceptionHandler()
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
                    return;
                }

                if (layout.getFileHeader() != null)
                {
                    internalAppend(new LogMessage(System.currentTimeMillis(), layout.getFileHeader()));
                }
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

                if (layout.getFileFooter() != null)
                {
                    internalAppend(new LogMessage(System.currentTimeMillis(), layout.getFileFooter()));
                }

                writer.stop();
                writer.waitUntilStopped(batchDelay * 2);
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
            internalLogger.error("internal error: message was null", null);
            return;
        }

        writer.addMessage(message);
    }
}
