// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.cloudwatch;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;

import com.kdgregory.log4j.shared.DefaultThreadFactory;
import com.kdgregory.log4j.shared.LogMessage;
import com.kdgregory.log4j.shared.LogWriter;
import com.kdgregory.log4j.shared.Substitutions;
import com.kdgregory.log4j.shared.ThreadFactory;
import com.kdgregory.log4j.shared.WriterFactory;


/**
 *  Appender that writes to a Cloudwatch log stream.
 */
public class CloudwatchAppender extends AppenderSkeleton
{
    /**
     *  The different types of writer rolling that we support.
     */
    public enum RollMode
    {
        /** Rolling is disabled. */
        none,

        /** Rolling is controlled by the <code>rollInterval</code> parameter. */
        interval,

        /** Rolling happens with the first message after every hour. */
        hourly,

        /** Rolling happens with the first message after midnight UTC */
        daily
    }

    private final static int AWS_MAX_BATCH_COUNT = 10000;
    private final static int AWS_MAX_BATCH_BYTES = 1048576;

    private final static int DEFAULT_BATCH_SIZE = 16;
    private final static long DEFAULT_BATCH_TIMEOUT = 4000L;
    private final static long DISABLED_ROLL_INTERVAL = -1;

    private final static Pattern ALLOWED_NAME_REGEX = Pattern.compile("[^A-Za-z0-9-_]");

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
            return new CloudWatchLogWriter(actualLogGroup, actualLogStream);
        }
    };

    // the current writer; initialized on first append, changed after roll

    protected LogWriter writer;

    // the last time we rolled the writer

    protected volatile long lastRollTimestamp;

    // this object is used for synchronization of initialization and writer change

    private Object initializationLock = new Object();

    // this is used to synchronize access to the queue; queue updates are normally
    // very fast, so plain-old-synchronization should not cause undue contention

    private Object messageQueueLock = new Object();

    // the waiting-for-batch queue; replaced when we pass batch to writer

    protected LinkedList<LogMessage> messageQueue = new LinkedList<LogMessage>();
    protected int messageQueueBytes = 0;

    // the last time we wrote a batch, to enable delay-based batching; this is
    // initialized during construction, so the first batch might actually go out
    // earlier than expected (if there's a delay between construction and use)

    protected volatile long lastBatchTimestamp;

    // these variables hold the post-substitution log-group and log-stream names
    // (mostly useful for testing)

    private String  actualLogGroup;
    private String  actualLogStream;

    // all vars below this point are configuration-controlled

    private String          logGroup;
    private String          logStream;
    private int             batchSize;
    private long            maxDelay;
    private RollMode        rollMode;
    private long            rollInterval;
    private AtomicInteger   sequence;


    /**
     *  Base constructor: assigns default values to configuration properties.
     */
    public CloudwatchAppender()
    {
        logStream = "{startTimestamp}";
        batchSize = DEFAULT_BATCH_SIZE;
        maxDelay = DEFAULT_BATCH_TIMEOUT;
        rollMode = RollMode.none;
        rollInterval = DISABLED_ROLL_INTERVAL;
        sequence = new AtomicInteger();

        lastBatchTimestamp = System.currentTimeMillis();
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
     *  Sets the number of messages that will submitted per request. Smaller batch
     *  sizes mean more calls to AWS, larger batches mean a higher risk for losing
     *  messages if the program crashes.
     *  <p>
     *  <em>Note:</em> this value is used as a trigger for sending a batch; it
     *  does not guarantee the size of that batch as written to Cloudwatch. The
     *  actual write may contain more or fewer events than are in the batch,
     *  either due to reaching a physical request limit or because additional
     *  events were added to the batch while it was being processed.
     *  <p>
     *  The default value is 16, which should be a good tradeoff for non-chatty
     *  programs. The maximum size is 10,000, imposed by AWS.
     */
    public void setBatchSize(int batchSize)
    {
        if (batchSize > AWS_MAX_BATCH_COUNT)
        {
            // FIXME - log an error
            throw new IllegalArgumentException("AWS limits batch size to " + AWS_MAX_BATCH_COUNT + " messages");
        }
        this.batchSize = batchSize;
    }


    /**
     *  Returns the batch size; see {@link #setBatchSize}. Primarily used for testing.
     */
    public int getBatchSize()
    {
        return batchSize;
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
    public void setMaxDelay(long maxDelay)
    {
        this.maxDelay = maxDelay;
    }


    /**
     *  Returns the maximum batch delay; see {@link #setMaxDelay}. Primarily used
     *  for testing.
     */
    public long getMaxDelay()
    {
        return maxDelay;
    }


    /**
     *  Sets the rule for rolling to a new log stream. Acceptable values are "none", "interval",
     *  "hourly", and "daily"; see {@link #RollMode} for details. Note that you must also set
     *  <code>rollInterval</code> to make interval rolling useful.
     *  <p>
     *  Attempting to set an invalid mode is equivalent to "none", but will emit a warning to the
     *  Log4J internal log.
     */
    public void setRollMode(String rollMode)
    {
        try
        {
            this.rollMode = RollMode.valueOf(rollMode);
        }
        catch (IllegalArgumentException ex)
        {
            this.rollMode = RollMode.none;
            LogLog.error("invalid rollMode: " + rollMode);
        }
    }


    /**
     *  Returns the current rolling mode.
     */
    public String getRollMode()
    {
        return this.rollMode.name();
    }


    /**
     *  Sets the roll interval, in milliseconds. This parameter is used only when the
     *  <code>rollMode</code> parameter is "interval"; to be useful, the log stream name
     *  should be timestamp-based.
     */
    public void setRollInterval(long value)
    {
        this.rollInterval = value;
    }


    /**
     *  Returns the current roll interval.
     */
    public long getRollInterval()
    {
        return rollInterval;
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
     *  Rolls the log stream: flushes all outstanding messages to the current
     *  stream, and opens a new stream.
     */
    public void roll()
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
     *  Called by {@link #initialize} and also {@link #roll}, to switch to a new
     *  writer. Closes the old writer, if any.
     */
    private void startWriter()
    {
        synchronized (initializationLock)
        {
            try
            {
                Substitutions subs = new Substitutions(new Date(), sequence.get());
                actualLogGroup  = ALLOWED_NAME_REGEX.matcher(subs.perform(logGroup)).replaceAll("");
                actualLogStream = ALLOWED_NAME_REGEX.matcher(subs.perform(logStream)).replaceAll("");

                writer = writerFactory.newLogWriter();
                threadFactory.startLoggingThread(writer);

                if (layout.getHeader() != null)
                {
                    internalAppend(LogMessage.create(layout.getHeader()));
                }

                lastRollTimestamp = System.currentTimeMillis();
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
                sendBatch();
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

        if (message.size() > AWS_MAX_BATCH_BYTES)
        {
            LogLog.warn("attempted to append a message > AWS batch size; ignored");
            return;
        }

        long now = System.currentTimeMillis();
        rollIfNeeded(now);

        synchronized (messageQueueLock)
        {
            messageQueue.add(message);
            messageQueueBytes += message.size();

            if (shouldSendBatch(now))
            {
                sendBatch();
            }
        }
    }


    private boolean shouldSendBatch(long now)
    {
        return (messageQueue.size() >= batchSize)
            || (messageQueueBytes >= AWS_MAX_BATCH_BYTES)
            || ((now - lastBatchTimestamp) >= maxDelay);
    }


    private void sendBatch()
    {
        List<LogMessage> batch = null;
        synchronized (messageQueueLock)
        {
            batch = messageQueue;
            messageQueue = new LinkedList<LogMessage>();
            messageQueueBytes = 0;
            lastBatchTimestamp = System.currentTimeMillis();
        }

        // in normal use, writer will never be null; for testing, however, it might be
        if (writer == null)
        {
            LogLog.warn("appender not properly configured: writer is null");
        }
        else
        {
            writer.addBatch(batch);
        }
    }


    private boolean shouldRoll(long now)
    {
        switch (rollMode)
        {
            case none:
                return false;
            case interval:
                return (rollInterval > 0) && ((now - lastRollTimestamp) > rollInterval);
            case hourly:
                return (lastRollTimestamp / 3600000) < (now / 3600000);
            case daily:
                return (lastRollTimestamp / 86400000) < (now / 86400000);
            default:
                return false;
        }
    }


    /**
     *  Rolls the writer if needed. This includes flushing the current batch.
     */
    private void rollIfNeeded(long now)
    {
        // double-checked locking: avoid contention for first check, but make sure we don't do things twice
        if (shouldRoll(now))
        {
            synchronized (initializationLock)
            {
                if (shouldRoll(now))
                {
                    roll();
                }
            }
        }
    }
}
