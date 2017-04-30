// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.cloudwatch;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;


/**
 *  Appender that writes to a Cloudwatch log stream.
 */
public class CloudwatchAppender extends AppenderSkeleton
{
    private final static int DEFAULT_BATCH_SIZE = 16;
    private final static long DEFAULT_BATCH_TIMEOUT = 4000L;

    private final static int AWS_MAX_BATCH_SIZE = 10000;


    /**
     *  Base constructor: assigns default values to configuration properties.
     */
    public CloudwatchAppender()
    {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        RuntimeMXBean runtimeMx = ManagementFactory.getRuntimeMXBean();

        logStream = dateFormat.format(new Date(runtimeMx.getStartTime()));
        batchSize = DEFAULT_BATCH_SIZE;
        maxDelay = DEFAULT_BATCH_TIMEOUT;
    }


//----------------------------------------------------------------------------
//  Configuration
//----------------------------------------------------------------------------

    private boolean dryRun;
    private String  logGroup;
    private String  logStream;
    private int     batchSize;
    private long    maxDelay;


    /**
     *  Sets the "dry run" flag, which prevents writes to Cloudwatch. This
     *  is intended for testing.
     */
    public void setDryRun(boolean value)
    {
        dryRun = value;
    }


    /**
     *  Retrieves the "dry run" flag; see {@link #setDryRun}.
     */
    public boolean isDryRun()
    {
        return dryRun;
    }


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
        if (batchSize > AWS_MAX_BATCH_SIZE)
        {
            throw new IllegalArgumentException("AWS limits batch size to " + AWS_MAX_BATCH_SIZE + " messages");
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


//----------------------------------------------------------------------------
//  Appender overrides
//----------------------------------------------------------------------------

    @Override
    protected void append(LoggingEvent event)
    {
        if (! preparedToAppend()) return;

        try
        {
            LogMessage message = new LogMessage(event, getLayout());

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
        catch (Exception ex)
        {
            LogLog.error("exception while appending (should never happen)", ex);
        }
    }


    @Override
    public void close()
    {
        // TODO - get footer from layout, push last batch
        // TODO - shut down sender thread
        // TODO - mark appender as closed
    }


    @Override
    public boolean requiresLayout()
    {
        return true;
    }


//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    private static int AWS_MAX_BATCH_COUNT = 10000;
    private static int AWS_MAX_BATCH_BYTES = 1048576;

    // this is used to synchronize access to the queue; queue updates are normally
    // very fast, so plain-old-synchronization should not cause undue contention

    private Object messageQueueLock = new Object();

    // this will only be created if we're not in a dry run

    private CloudwatchWriter writer;

    // the waiting-for-batch queue; these are package-protected for testing

    LinkedList<LogMessage> messageQueue = new LinkedList<LogMessage>();
    int messageQueueBytes = 0;
    long lastBatchTimestamp = System.currentTimeMillis();

    // this variable is a test hook: each (immutable) batch is saved here when
    // it's ready for the sender; tests can use it to verify batch size, nobody
    // else should touch it

    List<LogMessage> lastBatch;


    /**
     *  Lazily initializes the writer thread, and otherwise verifies that the appender
     *  is able to do its thing. This should wrap any operations in {@link #append}.
     */
    private boolean preparedToAppend()
    {
        // TODO - check that layout is valid and appender isn't closed
        if ((writer == null) && (! isDryRun()))
        {
            writer = new CloudwatchWriter(logGroup, logStream);
            Thread writerThread = new Thread(writer);
            writerThread.setPriority(Thread.NORM_PRIORITY);
            writerThread.start();
        }
        return true;
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
        lastBatch = batch;
        if (! isDryRun())
        {
            writer.addBatch(batch);
        }
    }
}
