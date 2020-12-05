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

import net.sf.kdgcommons.test.StringAsserts;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;


public class TestCloudWatchWriterConfig
{
    @Test
    public void testValidateConfigMinimal() throws Exception
    {
        CloudWatchWriterConfig config = new CloudWatchWriterConfig()
                                        .setLogGroupName("argle")
                                        .setLogStreamName("bargle");

        List<String> result = config.validate();
        assertEquals("config should be valid", 0, result.size());
    }


    @Test
    public void testValidateConfigMissingValues() throws Exception
    {
        CloudWatchWriterConfig config = new CloudWatchWriterConfig();

        List<String> result = config.validate();
        assertEquals("number of messages",  2,                          result.size());
        assertEquals("message 0",           "missing log group name",   result.get(0));
        assertEquals("message 1",           "missing log stream name",  result.get(1));
    }


    @Test
    public void testValidateConfigBlankNames() throws Exception
    {
        CloudWatchWriterConfig config = new CloudWatchWriterConfig()
                                        .setLogGroupName("")
                                        .setLogStreamName("");

        List<String> result = config.validate();
        assertEquals("number of messages",  2,                          result.size());
        assertEquals("message 0",           "blank log group name",     result.get(0));
        assertEquals("message 1",           "blank log stream name",    result.get(1));
    }


    @Test
    public void testValidateConfigInvalidNames() throws Exception
    {
        CloudWatchWriterConfig config = new CloudWatchWriterConfig()
                                        .setLogGroupName("I'm No Good!")
                                        .setLogStreamName("**Nor Am I**");

        List<String> result = config.validate();
        assertEquals("number of messages",  2,                                          result.size());
        assertEquals("message 0",           "invalid log group name: I'm No Good!",     result.get(0));
        assertEquals("message 1",           "invalid log stream name: **Nor Am I**",    result.get(1));
    }


    @Test
    public void testValidateConfigGoodRetentionPeriod() throws Exception
    {
        CloudWatchWriterConfig config = new CloudWatchWriterConfig()
                                        .setLogGroupName("argle")
                                        .setLogStreamName("bargle")
                                        .setRetentionPeriod(30);

        List<String> result = config.validate();
        assertEquals("config should be valid", 0, result.size());
    }


    @Test
    public void testValidateConfigBadRetentionPeriod() throws Exception
    {
        CloudWatchWriterConfig config = new CloudWatchWriterConfig()
                                        .setLogGroupName("argle")
                                        .setLogStreamName("bargle")
                                        .setRetentionPeriod(897);

        List<String> result = config.validate();
        assertEquals("number of messages",      1,                                   result.size());
        StringAsserts.assertRegex("message",    "invalid retention period: 897.*",   result.get(0));
    }

}
