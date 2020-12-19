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

import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.StringUtil;

import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;


public class TestKinesisWriterConfig
{
    @Test
    public void testValidateMinimal() throws Exception
    {
        KinesisWriterConfig config = new KinesisWriterConfig()
                                     .setStreamName("Valid_Stream.Name-")
                                     .setPartitionKey("something");

        List<String> result = config.validate();
        assertEquals("config should be valid", 0, result.size());
    }


    @Test
    public void testValidateMissingValues() throws Exception
    {
        KinesisWriterConfig config = new KinesisWriterConfig();

        List<String> result = config.validate();
        assertEquals("number of messages",  2,                          result.size());
        assertEquals("message 0",           "missing stream name",      result.get(0));
        assertEquals("message 1",           "missing partition key",    result.get(1));
    }


    @Test
    public void testValidateBlankValues() throws Exception
    {
        KinesisWriterConfig config = new KinesisWriterConfig()
                                     .setStreamName("")
                                     .setPartitionKey("");      // this enables random partition keys

        List<String> result = config.validate();
        assertEquals("number of messages",  1,                          result.size());
        assertEquals("message 0",           "blank stream name",        result.get(0));
    }


    @Test
    public void testValidateOverlongValues() throws Exception
    {
        KinesisWriterConfig config = new KinesisWriterConfig()
                                     .setStreamName(StringUtil.repeat('X', 129))
                                     .setPartitionKey(StringUtil.repeat('X', 257));

        List<String> result = config.validate();
        assertEquals("number of messages",  2,                          result.size());
        assertEquals("message 0",           "stream name too long",     result.get(0));
        assertEquals("message 1",           "partition key too long",   result.get(1));
    }


    @Test
    public void testValidateInvalidValues() throws Exception
    {
        KinesisWriterConfig config = new KinesisWriterConfig()
                                     .setStreamName("I'm Not Valid!")
                                     .setPartitionKey("This Is Valid!");    // no constraints on content

        List<String> result = config.validate();
        assertEquals("number of messages",  1,                                      result.size());
        assertEquals("message 0",           "invalid stream name: I'm Not Valid!",  result.get(0));
    }


    @Test
    public void testValidateInvalidRetentionPeriod() throws Exception
    {
        KinesisWriterConfig config = new KinesisWriterConfig()
                                     .setStreamName("foo")
                                     .setPartitionKey("bar")
                                     .setAutoCreate(true);

        config.setRetentionPeriod(7);
        List<String> result1 = config.validate();

        assertEquals("number of messages",  1,                                          result1.size());
        assertEquals("message 0",           "minimum retention period is 24 hours",     result1.get(0));

        config.setRetentionPeriod(8761);
        List<String> result2 = config.validate();

        assertEquals("number of messages",  1,                                          result2.size());
        assertEquals("message 0",           "maximum retention period is 8760 hours",   result2.get(0));
    }
}
