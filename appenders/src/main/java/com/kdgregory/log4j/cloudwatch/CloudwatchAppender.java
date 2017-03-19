// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.cloudwatch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
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
import org.apache.log4j.Layout;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;


/**
 *  Appender that writes to a Cloudwatch log stream.
 */
public class CloudwatchAppender extends AppenderSkeleton
{
    /**
     *  Base constructor: assigns default values to configuration properties.
     */
    public CloudwatchAppender()
    {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        RuntimeMXBean runtimeMx = ManagementFactory.getRuntimeMXBean();

        logStream = dateFormat.format(new Date(runtimeMx.getStartTime()));
        batchSize = 10;
        maxDelay = 4000L;
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
     *  Sets the message batch size for this appender.
     *  <p>
     *  To improve performance, messages are sent to CloudWatchin batches. This
     *  parameter controls the size of the batch: smaller batches mean more calls,
     *  larger batches mean a higher risk for losing messages if the app crashes.
     *  <p>
     *  <em>Note:</em> this value is used as a trigger for sending a batch; it
     *  does not guarantee the size of that batch as written to Cloudwatch. The
     *  actual write may contain more or fewer events than are in the batch,
     *  either due to reaching a physical request limit or because additional
     *  events were added to the batch while the trigger was being processed.
     *  <p>
     *  The default value is 16, which should be a good tradeoff for non-chatty
     *  programs.
     */
    public void setBatchSize(int batchSize)
    {
        // TODO - ensure that we're < AWS allowed batch size
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
        if (! ableToAppend()) return;

        try
        {
            LogMessage message = new LogMessage(event);

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
    private boolean ableToAppend()
    {
        // TODO - check that layout is valid and appender isn't closed
        // TODO - create writer thread
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
        // TODO - pass batch to writer
    }


    /**
     *  Holder for an in-queue logging message. This class applies the layout
     *  to the logging event, storing the result as a byte array so that we
     *  can calculate total batch size.
     *  <p>
     *  Note: instances are <code>Comparable</code> because Cloudwatch requires
     *  all messages in a batch to be in timestamp order. However, comparison
     *  is not consistent with equality, as we don't expect instances to be
     *  use where equality matters.
     *  <p>
     *  Note: package protected so available for tests.
     */
    class LogMessage
    implements Comparable<LogMessage>
    {
        private long timestamp;
        private byte[] messageBytes;

        public LogMessage(LoggingEvent event)
        throws UnsupportedEncodingException, IOException
        {
            timestamp = event.getTimeStamp();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            OutputStreamWriter out = new OutputStreamWriter(bos, "UTF-8");
            out.write(layout.format(event));

            if ((event.getThrowableInformation() != null) && layout.ignoresThrowable())
            {
                for (String traceline : event.getThrowableStrRep())
                {
                    out.write(traceline);
                    out.write(Layout.LINE_SEP);
                }
            }

            out.close();
            messageBytes = bos.toByteArray();
        }


        /**
         *  Returns the timestamp of the original logging event.
         */
        public long getTimestamp()
        {
            return timestamp;
        }

        /**
         *  Returns the size of the message, as it affects the Cloudwatch batch.
         */
        public int size()
        {
            return messageBytes.length + 26;
        }

        /**
         *  Returns the message content as a string (unfortunately, the Cloudwatch
         *  API doesn't allow us to write raw bytes).
         */
        public String getMessage()
        throws UnsupportedEncodingException
        {
            return new String(messageBytes, "UTF-8");
        }


        @Override
        public int compareTo(LogMessage that)
        {
            return (this.timestamp < that.timestamp) ? -1
                 : (this.timestamp > that.timestamp) ? 1
                 : 0;
        }
    }
}
