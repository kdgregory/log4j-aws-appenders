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

package com.kdgregory.logging.aws.kinesis;

import java.nio.charset.StandardCharsets;

/**
 *  Responsible for returning either a configured literal partition key or
 *  generating a random key.
 */
public class PartitionKeyHelper
{
    private String literalValue;    // null for generated
    private int length = 6;         // this is the length of a generated key


    public PartitionKeyHelper(String configuredValue)
    {
        if ((configuredValue != null) && !configuredValue.isEmpty() && !configuredValue.equals("{random}"))
        {
            literalValue = configuredValue;
            length = configuredValue.getBytes(StandardCharsets.UTF_8).length;
        }
    }


    /**
     *  Indicates whether the partition keys are literal or generated.
     */
    public boolean isGenerated()
    {
        return literalValue == null;
    }


    /**
     *  Returns the length of the partition key. This is either the number of bytes
     *  in the UTF-8 encoding of a literal key, or the fixed size of a generated key.
     */
    public int getLength()
    {
        return length;
    }


    /**
     *  Returns the next partition key, either literal or generated.
     */
    public String getValue()
    {
        if (literalValue != null)
            return literalValue;

        // this is technically arbitrary, not random
        int v = (int)(System.nanoTime() & 0x7FFFF);
        return String.format("%06d", v);
    }
}
