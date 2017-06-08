// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.cloudwatch;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;

import com.kdgregory.log4j.shared.LogMessage;
import com.kdgregory.log4j.shared.LogWriter;
import com.kdgregory.log4j.shared.Substitutions;


/**
 *  Appender that writes to a Cloudwatch log stream.
 */
public class CloudwatchAppender extends AppenderSkeleton
{
    private final static int DEFAULT_BATCH_SIZE = 16;
    private final static long DEFAULT_BATCH_TIMEOUT = 4000L;
    private final static long DISABLED_ROLL_INTERVAL = -1;

    private final static int AWS_MAX_BATCH_COUNT = 10000;
    private final static int AWS_MAX_BATCH_BYTES = 1048576;

    private final static Pattern ALLOWED_NAME_REGEX = Pattern.compile("[^A-Za-z0-9-_]");

    // flag to indicate whether we need to run setup
    private volatile boolean ready = false;

    // flag to indicate whether we can keep writing; cannot be reset
    private volatile boolean closed = false;

    // this is used to synchronize access to the queue; queue updates are normally
    // very fast, so plain-old-synchronization should not cause undue contention

    private Object messageQueueLock = new Object();

    // the writer is created on first append; it's marked as protected so that tests
    // can replace with a mock implementation

    protected LogWriter writer;

    // the waiting-for-batch queue; also marked as protected for testing

    protected LinkedList<LogMessage> messageQueue = new LinkedList<LogMessage>();
    protected int messageQueueBytes = 0;
    protected long lastBatchTimestamp = System.currentTimeMillis();

    // we apply substitutions when creating a new LogWriter; these vars hold the
    // post-substitution names, and are accessible for testing

    private String  actualLogGroup;
    private String  actualLogStream;

    // all vars below this point are configuration

    private String  logGroup;
    private String  logStream;
    private int     batchSize;
    private long    maxDelay;
    private long    rollInterval;
    private AtomicInteger sequence = new AtomicInteger();


    /**
     *  Base constructor: assigns default values to configuration properties.
     */
    public CloudwatchAppender()
    {
        logStream = "{startTimestamp}";
        batchSize = DEFAULT_BATCH_SIZE;
        maxDelay = DEFAULT_BATCH_TIMEOUT;
        rollInterval = DISABLED_ROLL_INTERVAL;
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
     *  Sets the roll interval, in milliseconds. This enables switching to a new log stream
     *  after the specified interval has elapsed. To be make this useful, the log stream name
     *  should be timestamp-based.
     */
    public void setRollInterval(long value)
    {
        this.rollInterval = value;
    }
    
    
    /**
     *  Returns the roll interval. This value is -1 if log rolling is disabled (the default).
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

        try
        {
            if (! ready)
            {
                initialize();

            }
            internalAppend(new LogMessage(event, getLayout()));
        }
        catch (Exception ex)
        {
            LogLog.error("exception while appending (should never happen)", ex);
        }
    }


    @Override
    public synchronized void close()
    {
        if (closed)
        {
            // someone already called this
            return;
        }

        closed = true;

        try
        {
            if (layout.getFooter() != null)
            {
                internalAppend(new LogMessage(layout.getFooter()));
            }

            sendBatch();

            // writer should only be null during testing; we should have complained already in sendBatch(),
            // and by now it's too late so quietly ignore if null
            if (writer != null)
            {
                writer.stop();
            }
        }
        catch (Exception ex)
        {
            LogLog.error("exception while shutting down appender", ex);
        }

    }


    @Override
    public boolean requiresLayout()
    {
        return true;
    }


//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    /**
     *  Lazily initializes the writer thread, and otherwise verifies that the appender
     *  is able to do its thing. This should preface any operations in {@link #append}.
     */
    private synchronized void initialize()
    {
        if (ready)
        {
            // someone else already initialized us
            return;
        }

        try
        {
            Substitutions subs = new Substitutions(new Date(), 0);
            actualLogGroup  = ALLOWED_NAME_REGEX.matcher(subs.perform(logGroup)).replaceAll("");
            actualLogStream = ALLOWED_NAME_REGEX.matcher(subs.perform(logStream)).replaceAll("");

            // this check allows us to use a mock writer
            if (writer == null)
            {
                writer = new CloudWatchLogWriter(actualLogGroup, actualLogStream);
                Thread writerThread = new Thread((CloudWatchLogWriter)writer);
                writerThread.setPriority(Thread.NORM_PRIORITY);
                writerThread.start();
            }

            if (layout.getHeader() != null)
            {
                internalAppend(new LogMessage(layout.getHeader()));
            }

            ready = true;
        }
        catch (Exception ex)
        {
            LogLog.error("exception while lazily configuring appender", ex);
        }
    }


    /**
     *  Appends a message to the current batch, and decides whether or not to send the batch.
     */
    private void internalAppend(LogMessage message)
    {
        if (message.size() > AWS_MAX_BATCH_BYTES)
        {
            LogLog.warn("attempted to append a message > AWS batch size; ignored");
            return;
        }

        synchronized (messageQueueLock)
        {
            messageQueue.add(message);
            messageQueueBytes += message.size();
            long curDelay = System.currentTimeMillis() - lastBatchTimestamp;

            if ((messageQueue.size() >= batchSize) || (messageQueueBytes >= AWS_MAX_BATCH_BYTES) || (curDelay >= maxDelay))
            {
                sendBatch();
            }
        }
    }


    /**
     *  Creates a batch from as many messages as will fit.
     *  <p>
     *  Note: this is called from within a synchronized block.
     */
    private void sendBatch()
    {
        List<LogMessage> batch = new ArrayList<LogMessage>(messageQueue.size());
        long batchBytes = 0;
        Iterator<LogMessage> itx = messageQueue.iterator();

        while (itx.hasNext() && (batchBytes < AWS_MAX_BATCH_BYTES) && (batch.size() < AWS_MAX_BATCH_COUNT))
        {
            LogMessage message = itx.next();
            if ((batchBytes + message.size()) > AWS_MAX_BATCH_BYTES)
            {
                break;
            }
            batch.add(message);
            batchBytes += message.size();
            messageQueueBytes -= message.size();
            itx.remove();
        }

        lastBatchTimestamp = System.currentTimeMillis();

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
}
