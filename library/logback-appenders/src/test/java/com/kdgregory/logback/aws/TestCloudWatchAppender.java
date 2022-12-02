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

package com.kdgregory.logback.aws;

import static net.sf.kdgcommons.test.StringAsserts.*;

import org.junit.Test;
import static org.junit.Assert.*;

import com.kdgregory.logback.testhelpers.cloudwatch.TestableCloudWatchAppender;
import com.kdgregory.logging.common.util.MessageQueue.DiscardAction;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchWriter;


/**
 *  These tests exercise appender logic specific to CloudWatchAppender, using a
 *  mock log-writer.
 */
public class TestCloudWatchAppender
extends AbstractUnitTest<TestableCloudWatchAppender>
{
    public TestCloudWatchAppender()
    {
        super("TestCloudWatchAppender/", "TEST");
    }


    @Test
    public void testConfiguration() throws Exception
    {
        initialize("testConfiguration");

        assertEquals("log group name",          "argle",                        appender.getLogGroup());
        assertEquals("log stream name",         "bargle",                       appender.getLogStream());
        assertEquals("retention period",        Integer.valueOf(7),             appender.getRetentionPeriod());
        assertEquals("dedicated writer",        false,                          appender.getDedicatedWriter());
        assertEquals("batch delay",             9876L,                          appender.getBatchDelay());
        assertEquals("truncate oversize messages", false,                       appender.getTruncateOversizeMessages());
        assertEquals("discard threshold",       12345,                          appender.getDiscardThreshold());
        assertEquals("discard action",          "newest",                       appender.getDiscardAction());
        assertEquals("synchronous mode",        false,                          appender.getSynchronous());
        assertEquals("use shutdown hook",       false,                          appender.getUseShutdownHook());
        assertEquals("assumed role",            "AssumableRole",                appender.getAssumedRole());
        assertEquals("client factory",          "com.example.Foo.bar",          appender.getClientFactory());
        assertEquals("client region",           "us-west-1",                    appender.getClientRegion());
        assertEquals("client endpoint",         "logs.us-west-2.amazonaws.com", appender.getClientEndpoint());
        assertEquals("initialization timeout",  20000,                          appender.getInitializationTimeout());
        assertEquals("enable batch logging",    true,                           appender.getEnableBatchLogging());
    }


    @Test
    public void testDefaultConfiguration() throws Exception
    {
        initialize("testDefaultConfiguration");

        // note: this is allowed at time of configuration, would disable logger if we try to append
        assertNull("log group name",                                            appender.getLogGroup());

        assertEquals("log stream name",         "{startupTimestamp}",           appender.getLogStream());
        assertEquals("retention period",        null,                           appender.getRetentionPeriod());
        assertEquals("dedicated writer",        true,                           appender.getDedicatedWriter());
        assertEquals("batch delay",             2000L,                          appender.getBatchDelay());
        assertEquals("truncate oversize messages", true,                        appender.getTruncateOversizeMessages());
        assertEquals("discard threshold",       10000,                          appender.getDiscardThreshold());
        assertEquals("discard action",          "oldest",                       appender.getDiscardAction());
        assertEquals("synchronous mode",        false,                          appender.getSynchronous());
        assertEquals("use shutdown hook",       true,                           appender.getUseShutdownHook());
        assertEquals("assumed role",            null,                           appender.getAssumedRole());
        assertEquals("client factory",          null,                           appender.getClientFactory());
        assertEquals("client region",           null,                           appender.getClientRegion());
        assertEquals("client endpoint",         null,                           appender.getClientEndpoint());
        assertEquals("initialization timeout",  60000,                          appender.getInitializationTimeout());
        assertEquals("enable batch logging",    false,                          appender.getEnableBatchLogging());
    }


    @Test
    public void testSynchronousConfiguration() throws Exception
    {
        initialize("testSynchronousConfiguration");

        // all we care about is the interaction between synchronous and batchDelay

        assertEquals("synchronous mode",    true,                           appender.getSynchronous());
        assertEquals("batch delay",         0L,                             appender.getBatchDelay());
    }


    @Test
    public void testInvalidRetentionPeriod() throws Exception
    {
        initialize("testInvalidRetentionPeriod");

        // retention period should retain its default value

        assertEquals("retention period",    null,                           appender.getRetentionPeriod());

        // everything else should be properly configured

        assertEquals("log group name",      "argle",                        appender.getLogGroup());
        assertEquals("log stream name",     "bargle",                       appender.getLogStream());
        assertEquals("dedicated writer",    true,                           appender.getDedicatedWriter());
        assertEquals("batch delay",         9876L,                          appender.getBatchDelay());
        assertEquals("discard threshold",   12345,                          appender.getDiscardThreshold());
        assertEquals("discard action",      "newest",                       appender.getDiscardAction());
        assertEquals("client factory",      "com.example.Foo.bar",          appender.getClientFactory());
        assertEquals("client region",       "us-west-1",                    appender.getClientRegion());
        assertEquals("client endpoint",     "logs.us-west-2.amazonaws.com", appender.getClientEndpoint());
        assertEquals("synchronous mode",    false,                          appender.getSynchronous());
        assertEquals("use shutdown hook",   false,                          appender.getUseShutdownHook());
    }


    @Test
    public void testWriterInitialization() throws Exception
    {
        // property has to be set before initialization
        System.setProperty("TestCloudWatchAppender.testWriterInitialization", "example");

        initialize("testWriterInitialization");

        assertEquals("configured log group name",   "MyLog-{sysprop:TestCloudWatchAppender.testWriterInitialization}",  appender.getLogGroup());
        assertEquals("configured log stream name",  "MyStream-{date}-{bogus}",                                          appender.getLogStream());

        MockCloudWatchWriter writer = appender.getMockWriter();

        assertEquals("writer log group name",           "MyLog-example",                    writer.config.getLogGroupName());
        assertRegex("writer log stream name",           "MyStream-20\\d{6}-\\{bogus}",      writer.config.getLogStreamName());
        assertEquals("writer retention period",         Integer.valueOf(14),                writer.config.getRetentionPeriod());
        assertEquals("writer batch delay",              9876L,                              writer.config.getBatchDelay());
        assertEquals("writer discard threshold",        12345,                              writer.config.getDiscardThreshold());
        assertEquals("writer discard action",           DiscardAction.newest,               writer.config.getDiscardAction());
        assertEquals("writer client factory method",    "com.example.Foo.bar",              writer.config.getClientFactoryMethod());
        assertEquals("writer client endpoint",          "logs.us-west-2.amazonaws.com",     writer.config.getClientEndpoint());
    }
}