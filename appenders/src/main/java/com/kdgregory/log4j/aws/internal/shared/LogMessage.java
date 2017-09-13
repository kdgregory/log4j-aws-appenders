// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.shared;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

import org.apache.log4j.Layout;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;


/**
 *  Holder for an in-queue logging message. Instances hold the message bytes
 *  (encoded using UTF-8) as well as the timestamp (either provided by Log4J
 *  event or time of construction). Instances are comparable based on timestamp,
 *  but <code>compareTo()</code> is not  consistent with <code>equals()</code>.
 *  <p>
 *  Instances are normally constructed using one of the provided factory methods,
 *  rather than the constructor. These methods will log any exceptions using the
 *  Log4J internal logger, and return null if unable to construct an instance.
 *  Such exceptions are defined by the classes used, and therefore must be
 *  handled, but are not expected to ever occur.
 */
public class LogMessage
implements Comparable<LogMessage>
{
    /**
     *  Creates an instance from an arbitrary string.
     */
    public static LogMessage create(String message)
    {
        try
        {
            return new LogMessage(System.currentTimeMillis(), message.getBytes("UTF-8"));
        }
        catch (UnsupportedEncodingException ex)
        {
            LogLog.error("unsupported encoding: UTF-8 (should never happen!)");
            return null;
        }
    }


    /**
     *  Creates an instance from a Log4J LoggingEvent, applying the provided
     *  Log4J layout.
     */
    public static LogMessage create(LoggingEvent event, Layout layout)
    {
        try
        {

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

            return new LogMessage(event.getTimeStamp(), bos.toByteArray());
        }
        catch (Exception ex)
        {
            LogLog.error("error creating LogMessage (should never happen!)", ex);
            return null;
        }
    }


//----------------------------------------------------------------------------
//
//----------------------------------------------------------------------------

    private long timestamp;
    private byte[] messageBytes;


    public LogMessage(long timestamp, byte[] messageBytes)
    {
        this.timestamp = timestamp;
        this.messageBytes = messageBytes;
    }



    /**
     *  Returns the timestamp of the original logging event.
     */
    public long getTimestamp()
    {
        return timestamp;
    }


    /**
     *  Returns the size of the message, as it affects the CloudWatch batch.
     */
    public int size()
    {
        return messageBytes.length + 26;
    }
    
    
    /**
     *  Returns the UTF-8 message bytes.
     */
    public byte[] getBytes()
    {
        return messageBytes;
    }


    /**
     *  Returns the message content as a string (unfortunately, the CloudWatch
     *  API doesn't allow us to write raw bytes).
     */
    public String getMessage()
    {
        try
        {
            return new String(messageBytes, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("caught UnsupportedEncodingException for UTF-8; should never happen!");
        }
    }


    @Override
    public int compareTo(LogMessage that)
    {
        return (this.timestamp < that.timestamp) ? -1
             : (this.timestamp > that.timestamp) ? 1
             : 0;
    }
}
