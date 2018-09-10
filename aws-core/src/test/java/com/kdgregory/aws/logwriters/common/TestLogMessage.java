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

package com.kdgregory.aws.logwriters.common;

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

        assertEquals("timestmap",               timestamp,              message.getTimestamp());
        assertEquals("message",                 text,                   message.getMessage());
        assertArrayEquals("message as bytes",   text.getBytes("UTF-8"), message.getBytes());
        assertEquals("size",                    4,                      message.size());
    }


    @Test
    public void testUnicodeMessageFromString() throws Exception
    {
        final long timestamp = System.currentTimeMillis();
        final String text = "\u0024\u00a2\u20ac";

        LogMessage message = new LogMessage(timestamp, text);

        assertEquals("timestmap",               timestamp,              message.getTimestamp());
        assertEquals("message",                 text,                   message.getMessage());
        assertArrayEquals("message as bytes",   text.getBytes("UTF-8"), message.getBytes());
        assertEquals("size",                    6,                      message.size());
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
