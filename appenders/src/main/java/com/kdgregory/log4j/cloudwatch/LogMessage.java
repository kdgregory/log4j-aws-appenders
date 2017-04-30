// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.cloudwatch;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;


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
public class LogMessage
implements Comparable<LogMessage>
{
    private long timestamp;
    private byte[] messageBytes;

    public LogMessage(LoggingEvent event, Layout layout)
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