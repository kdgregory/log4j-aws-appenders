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

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.LoggingEvent;

import com.kdgregory.logging.common.LogMessage;

import java.io.PrintWriter;
import java.io.StringWriter;


public class TestLogMessageConversion
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
    public void testAsciiMessageFromEventDefaultLayout() throws Exception
    {
        final long timestamp = System.currentTimeMillis();
        final String text = "test";

        LoggingEvent event = createLoggingEvent(timestamp, text, null);
        LogMessage message = Utils.convertToLogMessage(event, new PatternLayout());

        // the default pattern appends a newline
        String expectedText = text + "\n";

        assertEquals("timestmap",               timestamp,                      message.getTimestamp());
        assertEquals("message",                 expectedText,                   message.getMessage());
        assertArrayEquals("message as bytes",   expectedText.getBytes("UTF-8"), message.getBytes());
        assertEquals("size",                    5,                              message.size());
    }


    @Test
    public void testUnicodeMessageFromEventDefaultLayout() throws Exception
    {
        final long timestamp = System.currentTimeMillis();
        final String text = "\u0024\u00a2\u20ac";

        LoggingEvent event = createLoggingEvent(timestamp, text, null);
        LogMessage message = Utils.convertToLogMessage(event, new PatternLayout());

        // the default pattern appends a newline
        String expectedText = text + "\n";

        assertEquals("timestmap",               timestamp,                      message.getTimestamp());
        assertEquals("message",                 expectedText,                   message.getMessage());
        assertArrayEquals("message as bytes",   expectedText.getBytes("UTF-8"), message.getBytes());
        assertEquals("size",                    7,                              message.size());
    }


    @Test
    // at this point we'll assume that UTF-8 conversion works as expected
    public void testAsciiMessageFromEventDefaultLayoutWithException() throws Exception
    {
        final long timestamp = System.currentTimeMillis();
        final String text = "test";
        final Exception ex = new Exception();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println(text);
        ex.printStackTrace(pw);
        pw.close();
        String expectedText = sw.toString();

        LoggingEvent event = createLoggingEvent(timestamp, text, ex);
        LogMessage message = Utils.convertToLogMessage(event, new PatternLayout());

        assertEquals("explicit timestamp",  timestamp,      message.getTimestamp());
        assertEquals("message as string",   expectedText,   message.getMessage());
    }
}
