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

package com.kdgregory.logging.common;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;
import static org.junit.Assert.*;


public class TestLogMessage
{
    @Test
    public void testAsciiMessageFromString() throws Exception
    {
        final long timestamp = System.currentTimeMillis();
        final String text = "test";

        LogMessage message = new LogMessage(timestamp, text);

        assertEquals("timestmap",               timestamp,                              message.getTimestamp());
        assertEquals("message",                 text,                                   message.getMessage());
        assertArrayEquals("message as bytes",   text.getBytes(StandardCharsets.UTF_8),  message.getBytes());
        assertEquals("size",                    4,                                      message.size());
    }


    @Test
    public void testUnicodeMessageFromString() throws Exception
    {
        final long timestamp = System.currentTimeMillis();
        final String text = "\u0024\u00a2\u20ac";

        LogMessage message = new LogMessage(timestamp, text);

        assertEquals("timestmap",               timestamp,                              message.getTimestamp());
        assertEquals("message",                 text,                                   message.getMessage());
        assertArrayEquals("message as bytes",   text.getBytes(StandardCharsets.UTF_8),  message.getBytes());
        assertEquals("size",                    6,                                      message.size());
    }


    @Test
    public void testTruncate() throws Exception
    {
        final String ascii = "abcdefg";
        final String utf8  = "\u00c1\u00c2\u2440";    // encodes as 2, 2, 3
        final String mixed = "\u00c1\u00c2X\u2440";   // encodes as 2, 2, 1, 3

        LogMessage e1 = new LogMessage(0, "");
        e1.truncate(Integer.MAX_VALUE);
        assertArrayEquals("empty array", new byte[0], e1.getBytes());

        LogMessage e2 = new LogMessage(0, "this is a test");
        e2.truncate(0);
        assertArrayEquals("truncate to empty", new byte[0], e1.getBytes());

        // the rest of the tests reconstitute the string, both because it's easier to read, and as a secondary check for valid truncation

        LogMessage a1 = new LogMessage(0, ascii);
        a1.truncate(7);
        assertEquals("truncate size == array size, USASCII",                        "abcdefg",              new String(a1.getBytes(), StandardCharsets.UTF_8));
        assertEquals("truncate size == array size, USASCII",                        "abcdefg",              a1.getMessage());

        LogMessage a2 = new LogMessage(0, ascii);
        a2.truncate(Integer.MAX_VALUE);
        assertEquals("truncate size > array size, USASCII",                         "abcdefg",              new String(a2.getBytes(), StandardCharsets.UTF_8));
        assertEquals("truncate size > array size, USASCII",                         "abcdefg",              a2.getMessage());

        LogMessage a3 = new LogMessage(0, ascii);
        a3.truncate(3);
        assertEquals("truncate size < array size, USASCII",                         "abc",                  new String(a3.getBytes(), StandardCharsets.UTF_8));
        assertEquals("truncate size < array size, USASCII",                         "abc",                  a3.getMessage());

        LogMessage a4 = new LogMessage(0, ascii);
        a4.truncate(6);
        assertEquals("truncate size == array size - 1, USASCII",                    "abcdef",               new String(a4.getBytes(), StandardCharsets.UTF_8));
        assertEquals("truncate size == array size - 1, USASCII",                    "abcdef",               a4.getMessage());

        LogMessage u1 = new LogMessage(0, utf8);
        u1.truncate(7);
        assertEquals("truncate size == array size, UTF-8",                          "\u00c1\u00c2\u2440",   new String(u1.getBytes(), StandardCharsets.UTF_8));
        assertEquals("truncate size == array size, UTF-8",                          "\u00c1\u00c2\u2440",   u1.getMessage());

        LogMessage u2 = new LogMessage(0, utf8);
        u2.truncate(Integer.MAX_VALUE);
        assertEquals("truncate size > array size, UTF-8",                           "\u00c1\u00c2\u2440",   new String(u2.getBytes(), StandardCharsets.UTF_8));
        assertEquals("truncate size > array size, UTF-8",                           "\u00c1\u00c2\u2440",   u2.getMessage());

        LogMessage u3 = new LogMessage(0, utf8);
        u3.truncate(4);
        assertEquals("truncate size < array size, UTF-8, at boundary",              "\u00c1\u00c2",         new String(u3.getBytes(), StandardCharsets.UTF_8));
        assertEquals("truncate size < array size, UTF-8, at boundary",              "\u00c1\u00c2",         u3.getMessage());

        LogMessage u4 = new LogMessage(0, utf8);
        u4.truncate(5);
        assertEquals("truncate size < array size, UTF-8, start-of-sequence",        "\u00c1\u00c2",         new String(u4.getBytes(), StandardCharsets.UTF_8));
        assertEquals("truncate size < array size, UTF-8, start-of-sequence",        "\u00c1\u00c2",         u4.getMessage());

        LogMessage u5 = new LogMessage(0, utf8);
        u5.truncate(6);
        assertEquals("truncate size < array size, UTF-8, mid-sequence",             "\u00c1\u00c2",         new String(u5.getBytes(), StandardCharsets.UTF_8));
        assertEquals("truncate size < array size, UTF-8, mid-sequence",             "\u00c1\u00c2",         u5.getMessage());

        LogMessage m1 = new LogMessage(0, mixed);
        m1.truncate(5);
        assertEquals("truncate size < array size, mixed, ASCII before boundary",    "\u00c1\u00c2X",        new String(m1.getBytes(), StandardCharsets.UTF_8));
        assertEquals("truncate size < array size, mixed, ASCII before boundary",    "\u00c1\u00c2X",        m1.getMessage());

        LogMessage m2 = new LogMessage(0, mixed);
        m2.truncate(4);
        assertEquals("truncate size < array size, mixed, ASCII after boundary",     "\u00c1\u00c2",         new String(m2.getBytes(), StandardCharsets.UTF_8));
        assertEquals("truncate size < array size, mixed, ASCII after boundary",     "\u00c1\u00c2",         m2.getMessage());
    }



    @Test
    public void testOrdering() throws Exception
    {
        final long timestamp = System.currentTimeMillis();

        LogMessage m1 = new LogMessage(timestamp - 1, "foo");
        LogMessage m2 = new LogMessage(timestamp, "foo");
        LogMessage m3 = new LogMessage(timestamp, "bar");
        LogMessage m4 = new LogMessage(timestamp, "fooble");
        LogMessage m5 = new LogMessage(timestamp + 1, "foo");

        LogMessage[] testArray = new LogMessage[] { m2, m5, m3, m1, m4 };
        Arrays.sort(testArray);

        assertEquals(Arrays.asList(m1, m2, m3, m4, m5),
                     Arrays.asList(testArray));
    }
}
