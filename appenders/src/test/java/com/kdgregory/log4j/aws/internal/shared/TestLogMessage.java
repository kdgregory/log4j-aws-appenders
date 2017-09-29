// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.shared;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.LoggingEvent;

import java.io.PrintWriter;
import java.io.StringWriter;


public class TestLogMessage
{
//----------------------------------------------------------------------------
//  Support code
//----------------------------------------------------------------------------

    // this logger isn't used for logging; we need one to generate LoggingEvents
    Logger logger = Logger.getLogger(getClass());


    @SuppressWarnings("deprecation")
    private LoggingEvent createLoggingEvent(long timestamp, String message, Throwable ex)
    {
        return new LoggingEvent(getClass().getName(), logger, timestamp, Priority.DEBUG, message, ex);
    }


//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testAsciiMessageFromString() throws Exception
    {
        final String text = "test";

        LogMessage message = LogMessage.create(text);

        assertEquals("size",                        4,                      message.size());
        assertArrayEquals("content of byte array",  text.getBytes("UTF-8"), message.getBytes());
        assertEquals("message as string",           text,                   message.getMessage());

        assertTrue("timestamp is recent",           message.getTimestamp() > System.currentTimeMillis() - 200);
        assertTrue("timestamp is not in future",    message.getTimestamp() < System.currentTimeMillis() + 200);
    }


    @Test
    public void testUnicodeMessageFromString() throws Exception
    {
        final String text = "\u0024\u00a2\u20ac";

        LogMessage message = LogMessage.create(text);

        assertEquals("size",                        6,          message.size());
        assertArrayEquals("content of byte array",  text.getBytes("UTF-8"), message.getBytes());
        assertEquals("message as string",           text,                   message.getMessage());
        assertTrue("timestamp is recent",           message.getTimestamp() > System.currentTimeMillis() - 200);
        assertTrue("timestamp is not in future",    message.getTimestamp() < System.currentTimeMillis() + 200);
    }


    @Test
    public void testAsciiMessageFromEventDefaultLayout() throws Exception
    {
        final long timestamp = System.currentTimeMillis() - 10000;
        final String text = "test";

        LoggingEvent event = createLoggingEvent(timestamp, text, null);
        LogMessage message = LogMessage.create(event, new PatternLayout());

        // note: the default pattern appends a newline

        assertEquals("size",                        5,                               message.size());
        assertArrayEquals("content of byte array",  (text + "\n").getBytes("UTF-8"), message.getBytes());
        assertEquals("message as string",           (text + "\n"),                   message.getMessage());
        assertEquals("explicit timestamp",          timestamp,                       message.getTimestamp());
    }


    @Test
    public void testUnicodeMessageFromEventDefaultLayout() throws Exception
    {
        final long timestamp = System.currentTimeMillis() - 10000;
        final String text = "\u0024\u00a2\u20ac";

        LoggingEvent event = createLoggingEvent(timestamp, text, null);
        LogMessage message = LogMessage.create(event, new PatternLayout());

        // note: the default pattern appends a newline

        assertEquals("size",                        7,                               message.size());
        assertArrayEquals("content of byte array",  (text + "\n").getBytes("UTF-8"), message.getBytes());
        assertEquals("message as string",           (text + "\n"),                   message.getMessage());
        assertEquals("explicit timestamp",          timestamp,                       message.getTimestamp());
    }


    @Test
    public void testAsciiMessageFromEventDefaultLayoutWithException() throws Exception
    {
        final long timestamp = System.currentTimeMillis() - 10000;
        final String text = "test";
        final Exception ex = new Exception();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println(text);
        ex.printStackTrace(pw);
        pw.close();
        String expectedText = sw.toString();

        LoggingEvent event = createLoggingEvent(timestamp, text, ex);
        LogMessage message = LogMessage.create(event, new PatternLayout());

        // we'll assume that the UTF-8 conversion works as expected
        assertEquals("message as string",   expectedText,   message.getMessage());
        assertEquals("explicit timestamp",  timestamp,      message.getTimestamp());
    }
}
