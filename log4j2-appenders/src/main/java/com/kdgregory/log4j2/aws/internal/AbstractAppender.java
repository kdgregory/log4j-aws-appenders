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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.StringLayout;

import com.kdgregory.logging.aws.internal.AbstractWriterConfig;
import com.kdgregory.logging.aws.internal.AbstractWriterStatistics;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.factories.ThreadFactory;
import com.kdgregory.logging.common.factories.WriterFactory;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  Common implementation code that's shared between appenders.
 *  <p>
 *  For the most part, appenders have the same behavior: they initialize, transform
 *  log messages, and shut down. Most of the code to do that lives here, with a few
 *  hooks that are implemented in the appender proper.
 *  <p>
 *  Most of the member variables defined by this class are protected. This is intended
 *  to support testing. If you decide to subclass and access those variables, well,
 *  this is an internal class: they may go away.
 *  <p>
 *  Log4J2, like Logback, explicitly initializes the appender before use. The only
 *  strangeness here is in stop(): there are two versions defined by the interface,
 *  but current versions of Log4J call only one of them.
 */
public abstract class AbstractAppender
    <
    WriterConfigType extends AbstractWriterConfig<WriterConfigType>,
    AppenderConfigType extends AbstractAppenderConfig,
    AppenderStatsType extends AbstractWriterStatistics,
    AppenderStatsMXBeanType
    >
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

    // the MX bean type for the appender stats object
    private Class<AppenderStatsMXBeanType> appenderStatsMXBeanClass;

    // this object is used for synchronization of initialization and writer change
    private Object initializationLock = new Object();

    // this is provided to us by subclass
    protected AppenderConfigType appenderConfig;

    // character set for handling header/footer; extracted from layout if possible
    protected Charset layoutCharset = StandardCharsets.UTF_8;

    // holding this separately because of Log4J's "setters? we don't need no stinkin' setters!"
    // approach to configuration
    protected DiscardAction discardAction;

    // the current writer
    protected volatile LogWriter writer;


    protected AbstractAppender(
        String name,
        ThreadFactory threadFactory,
        WriterFactory<WriterConfigType,AppenderStatsType> writerFactory,
        AppenderStatsType appenderStats,
        Class<AppenderStatsMXBeanType> appenderStatsMXBeanClass,
        AppenderConfigType config,
        InternalLogger providedInternalLogger)
    {
        super(name, config.getFilter(), config.getLayout());
        this.appenderConfig = config;
        this.threadFactory = threadFactory;
        this.writerFactory = writerFactory;
        this.appenderStats = appenderStats;
        this.appenderStatsMXBeanClass = appenderStatsMXBeanClass;
        this.internalLogger = (providedInternalLogger != null)
                            ? providedInternalLogger
                            : new Log4J2InternalLogger(this);

        discardAction = DiscardAction.lookup(config.getDiscardAction());
        if (discardAction == null)
        {
            internalLogger.error("invalid discard action: " + config.getDiscardAction(), null);
            discardAction = DiscardAction.oldest;
        }

        Layout<?> layout = config.getLayout();
        if (layout instanceof StringLayout)
        {
            layoutCharset = ((StringLayout)layout).getCharset();
        }
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
        return appenderConfig;
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
        // the framework should never call this code; this exception
        // is here to catch any copy-paste errors in tests, but will
        // also serve to identify an incorrect framework version
        throw new IllegalStateException("stop() called -- should never happen with Log4J > 2.8");
    }


    @Override
    public boolean stop(long timeout, TimeUnit timeUnit)
    {
        synchronized (initializationLock)
        {
            if (writer != null)
            {
                stopWriter(timeout, timeUnit);
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
     *  Called by {@link #initialize}, to start a new writer running. Does not
     *  close the old writer, if any.
     */
    private void startWriter()
    {
        WriterConfigType writerConfig = generateWriterConfig()
                .setTruncateOversizeMessages(appenderConfig.getTruncateOversizeMessages())
                .setSynchronousMode(appenderConfig.isSynchronous())
                .setBatchDelay(appenderConfig.getBatchDelay())
                .setDiscardThreshold(appenderConfig.getDiscardThreshold())
                .setDiscardAction(discardAction)
                .setClientFactoryMethod(appenderConfig.getClientFactory())
                .setAssumedRole(appenderConfig.getAssumedRole())
                .setClientRegion(appenderConfig.getClientRegion())
                .setClientEndpoint(appenderConfig.getClientEndpoint());

        synchronized (initializationLock)
        {
            try
            {
                writer = writerFactory.newLogWriter(writerConfig, appenderStats, internalLogger);
                threadFactory.startWriterThread(writer, false, new UncaughtExceptionHandler()
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

                if (getLayout().getHeader() != null)
                {
                    String header = new String(getLayout().getHeader(), layoutCharset);
                    if (header.length() > 0)
                    {
                        internalAppend(new LogMessage(System.currentTimeMillis(), header));
                    }
                }
            }
            catch (Exception ex)
            {
                internalLogger.error("exception while initializing writer", ex);
            }
        }
    }


    /**
     *  Closes the current writer and optionally waits for it to shut down.
     */
    private void stopWriter(long timeout, TimeUnit timeUnit)
    {
        synchronized (initializationLock)
        {
            try
            {
                if (writer == null)
                    return;

                if (getLayout().getFooter() != null)
                {
                    String header = new String(getLayout().getFooter(), layoutCharset);
                    if (header.length() > 0)
                    {
                        internalAppend(new LogMessage(System.currentTimeMillis(), header));
                    }
                }

                writer.stop();

                if (timeUnit != null)
                {
                    writer.waitUntilStopped(timeUnit.toMillis(timeout));
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
     */
    protected void registerStatisticsBean()
    {
        JMXManager.getInstance().addStatsBean(getName(), appenderStats, appenderStatsMXBeanClass);
    }


    /**
     *  Unregisters the appender statistics from JMX. This is called when the appender
     *  is closed. Logs but otherwise ignores failure.
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
