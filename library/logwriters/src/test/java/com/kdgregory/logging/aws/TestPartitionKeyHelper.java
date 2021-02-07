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

package com.kdgregory.logging.aws;

import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.test.StringAsserts;

import com.kdgregory.logging.aws.kinesis.PartitionKeyHelper;


public class TestPartitionKeyHelper
{
    @Test
    public void testLiteral() throws Exception
    {
        PartitionKeyHelper helper = new PartitionKeyHelper("example");

        assertFalse("isGenerated()",                helper.isGenerated());
        assertEquals("getLength()",     7,          helper.getLength());
        assertEquals("getValue()",      "example",  helper.getValue());
    }


    @Test
    public void testLiteralNonAscii() throws Exception
    {
        String value = "f\u00f2\u00f3";
        PartitionKeyHelper helper = new PartitionKeyHelper(value);

        assertFalse("isGenerated()",            helper.isGenerated());
        assertEquals("getLength()",     5,      helper.getLength());
        assertEquals("getValue()",      value,  helper.getValue());
    }


    @Test
    public void testGenerated() throws Exception
    {
        PartitionKeyHelper helper = new PartitionKeyHelper(null);

        assertTrue("isGenerated()",             helper.isGenerated());
        assertEquals("getLength()",     6,      helper.getLength());

        Set<String> values = new HashSet<>();
        for (int ii = 0 ; ii < 10 ; ii++)
        {
            String value = helper.getValue();
            StringAsserts.assertRegex("value", "\\d{6}", value);
            values.add(value);
        }

        assertEquals("number of distinct values", 10, values.size());
    }


    @Test
    public void testGeneratedAltConfigs() throws Exception
    {
        PartitionKeyHelper h1 = new PartitionKeyHelper(null);
        assertTrue("null", h1.isGenerated());

        PartitionKeyHelper h2 = new PartitionKeyHelper("");
        assertTrue("empty string", h2.isGenerated());

        PartitionKeyHelper h3 = new PartitionKeyHelper("{random}");
        assertTrue("flag value", h3.isGenerated());
    }

}
