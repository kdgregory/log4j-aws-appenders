// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.cloudwatch;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.spi.LoggingEvent;


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

    private String  logGroup;
    private String  logStream;
    private int     batchSize;
    private long    maxDelay;


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
     *  The default value is 16, which should be a good tradeoff for non-chatty
     *  programs.
     */
    public void setBatchSize(int batchSize)
    {
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
    protected void append(LoggingEvent arg0)
    {
        throw new UnsupportedOperationException("FIXME - implement");
    }


    @Override
    public void close()
    {
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
}
