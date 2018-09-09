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

package com.kdgregory.log4j.aws.internal.shared;

import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;


/**
 *  Holder for an in-queue logging message. Each instance has a timestamp and the
 *  message, stored as both a string and UTF-8 encoded bytes.
 */
public class LogMessage
implements Comparable<LogMessage>
{
    private long timestamp;
    private String message;
    private byte[] messageBytes;


    /**
     *  Constructs an instance from a simple string.
     *
     *  @throws RuntimeException if UTF-8 encoding is not supported by the JVM (which
     *          should never happen).
     */
    public LogMessage(long timestamp, String message)
    {
        this.timestamp = timestamp;
        this.message = message;
        try
        {
            this.messageBytes = message.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("UnsupportedEncodingException when converting to UTF-8");
        }
    }


    /**
     *  Constructs an instance from a Log4J event, using the specified layout.
     *
     *  @throws RuntimeException if any error occurred during formatting or conversion.
     *          Will include any root cause other than UnsupportedEncodingException.
     */
    public LogMessage(LoggingEvent event, Layout layout)
    {
        try
        {
            StringWriter out = new StringWriter(1024);
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

            this.timestamp = event.getTimeStamp();
            this.message = out.toString();
            this.messageBytes = this.message.getBytes("UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("UnsupportedEncodingException when converting to UTF-8");
        }
        catch (Exception ex)
        {
            throw new RuntimeException("error creating LogMessage", ex);
        }
    }


    /**
     *  Returns the timestamp of the original logging event.
     */
    public long getTimestamp()
    {
        return timestamp;
    }


    /**
     *  Returns the size of the message after conversion to UTF-8.
     */
    public int size()
    {
        return messageBytes.length;
    }


    /**
     *  Returns the original message string.
     */
    public String getMessage()
    {
        return message;
    }


    /**
     *  Returns the UTF-8 message bytes.
     */
    public byte[] getBytes()
    {
        return messageBytes;
    }


    /**
     *  Compares instances based on their timestamp.
     *  <p>
     *  Note that an "equal" comparison is not consistent with <code>equals()</code>,
     *  which uses default instance equality. This is intentional: one the one hand,
     *  there's no reason to test equality for two instances; they should be considered
     *  opaque. On the other, we don't want the comparision to do more than it should:
     *  we're using a stable sort, and rely on that fact to order two messages with the
     *  same timestamp in the order they were passed to <code>append()</code>.
     */
    @Override
    public int compareTo(LogMessage that)
    {
        return (this.timestamp < that.timestamp) ? -1
               : (this.timestamp > that.timestamp) ? 1
               : 0;
    }
}
