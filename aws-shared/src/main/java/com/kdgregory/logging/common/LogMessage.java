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
     */
    public LogMessage(long timestamp, String message)
    {
        this.timestamp = timestamp;
        this.message = message;
        this.messageBytes = message.getBytes(StandardCharsets.UTF_8);
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
     *  Ensures that the message has no more than the specified number of bytes,
     *  truncating if necessary. This is a "semi-smart" truncate, in that it won't
     *  break UTF-8 character sequences, but it isn't smart enough to recognize
     *  Unicode code points that consist of multiple UTF-8 sequences. It also
     *  assumes that it will be given valid UTF-8, with indeterminate results if
     *  not.
     */
    public void truncate(int maxSize)
    {
        if (size() <= maxSize)
            return;

        // skip any UTF-8 continuation characters that cross the truncation boundary
        // (that means starting past the boundary)

        int idx = maxSize;
        int curFlag = 0;
        while (idx > 0)
        {
            curFlag = messageBytes[idx] & 0x00C0;
            if (curFlag != 0x0080)
                break;
            idx--;
        }

        // at this point we either have an ASCI character or a UTF-8 start character
        // need to adjust index if the latter

        if (curFlag == 0x00C0)
            idx--;

        // the actual truncation size will be index + 1; however, since we started past
        // the boundary, if there was an ASCII character at that point we're off-by-one
        // already, so need to compensate

        int newSize = Math.min(maxSize, idx + 1);
        byte[] newBytes = new byte[newSize];
        System.arraycopy(messageBytes, 0, newBytes, 0, newSize);
        messageBytes = newBytes;
        message = new String(messageBytes, StandardCharsets.UTF_8);
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
