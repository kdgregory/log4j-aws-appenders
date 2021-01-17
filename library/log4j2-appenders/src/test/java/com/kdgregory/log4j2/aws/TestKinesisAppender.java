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

package com.kdgregory.log4j2.aws;

import org.junit.Test;
import static org.junit.Assert.*;

import static net.sf.kdgcommons.test.StringAsserts.*;

import com.kdgregory.log4j2.aws.KinesisAppender.KinesisAppenderBuilder;
import com.kdgregory.log4j2.testhelpers.TestableKinesisAppender;
import com.kdgregory.log4j2.testhelpers.TestableKinesisAppender.TestableKinesisAppenderBuilder;
import com.kdgregory.logging.common.util.MessageQueue.DiscardAction;
import com.kdgregory.logging.testhelpers.kinesis.MockKinesisWriter;


/**
 *  These tests exercise appender logic specific to KinesisAppender, using a
 *  mock log-writer.
 */
public class TestKinesisAppender
extends AbstractUnitTest<TestableKinesisAppender>
{
    public TestKinesisAppender()
    {
        super("TestKinesisAppender/", "TEST");
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testConfiguration() throws Exception
    {
        initialize("testConfiguration");

        assertEquals("stream name",         "argle-{bargle}",                   appender.getConfig().getStreamName());
        assertEquals("partition key",       "foo-{date}",                       appender.getConfig().getPartitionKey());
        assertTrue("autoCreate",                                                appender.getConfig().getAutoCreate());
        assertEquals("shard count",         7,                                  appender.getConfig().getShardCount());
        assertEquals("retention period",    Integer.valueOf(48),                appender.getConfig().getRetentionPeriod());
        assertEquals("max delay",           1234L,                              appender.getConfig().getBatchDelay());
        assertFalse("truncate oversize messages",                               appender.getConfig().getTruncateOversizeMessages());
        assertEquals("discard threshold",   54321,                              appender.getConfig().getDiscardThreshold());
        assertEquals("discard action",      "newest",                           appender.getConfig().getDiscardAction());
        assertFalse("synchronous mode",                                         appender.getConfig().isSynchronous());
        assertFalse("use shutdown hook",                                        appender.getConfig().isUseShutdownHook());
        assertEquals("assumed role",        "AssumableRole",                    appender.getConfig().getAssumedRole());
        assertEquals("client factory",      "com.example.Foo.bar",              appender.getConfig().getClientFactory());
        assertEquals("client region",       "us-west-1",                        appender.getConfig().getClientRegion());
        assertEquals("client endpoint",     "kinesis.us-west-2.amazonaws.com",  appender.getConfig().getClientEndpoint());
    }


    @Test
    public void testDefaultConfiguration() throws Exception
    {
        initialize("testDefaultConfiguration");

        // can't test stream name because there's no default
        assertEquals("partition key",       "{startupTimestamp}",               appender.getConfig().getPartitionKey());
        assertFalse("autoCreate",                                               appender.getConfig().getAutoCreate());
        assertEquals("shard count",         1,                                  appender.getConfig().getShardCount());
        assertEquals("retention period",    Integer.valueOf(24),                appender.getConfig().getRetentionPeriod());
        assertEquals("max delay",           2000L,                              appender.getConfig().getBatchDelay());
        assertTrue("truncate oversize messages",                                appender.getConfig().getTruncateOversizeMessages());
        assertEquals("discard threshold",   10000,                              appender.getConfig().getDiscardThreshold());
        assertEquals("discard action",      "oldest",                           appender.getConfig().getDiscardAction());
        assertFalse("synchronous mode",                                         appender.getConfig().isSynchronous());
        assertTrue("use shutdown hook",                                         appender.getConfig().isUseShutdownHook());
        assertEquals("assumed role",        null,                               appender.getConfig().getAssumedRole());
        assertEquals("client factory",      null,                               appender.getConfig().getClientFactory());
        assertEquals("client region",       null,                               appender.getConfig().getClientRegion());
        assertEquals("client endpoint",     null,                               appender.getConfig().getClientEndpoint());
    }


    @Test
    public void testSynchronousConfiguration() throws Exception
    {
        initialize("testSynchronousConfiguration");

        // all we care about is the interaction between synchronous and batchDelay

        assertTrue("synchronous mode",                                          appender.getConfig().isSynchronous());
        assertEquals("batch delay",         0L,                                 appender.getConfig().getBatchDelay());
    }


    @Test
    public void testManualConfiguration() throws Exception
    {
        KinesisAppenderBuilder builder = new TestableKinesisAppenderBuilder()
                                         .setName("test")
                                         .setStreamName("argle-{bargle}")
                                         .setPartitionKey("foo-{date}")
                                         .setAutoCreate(true)
                                         .setShardCount(7)
                                         .setRetentionPeriod(48)
                                         .setBatchDelay(1234)
                                         .setDiscardThreshold(54321)
                                         .setDiscardAction(DiscardAction.newest.name())
                                         .setClientFactory("com.example.Foo.bar")
                                         .setClientRegion("us-west-1")
                                         .setClientEndpoint("kinesis.us-west-2.amazonaws.com")
                                         .setSynchronous(false)
                                         .setUseShutdownHook(false);

        appender = (TestableKinesisAppender)builder.build();

        assertEquals("appender name",       "test",                             appender.getName());

        assertEquals("stream name",         "argle-{bargle}",                   appender.getConfig().getStreamName());
        assertEquals("partition key",       "foo-{date}",                       appender.getConfig().getPartitionKey());
        assertTrue("autoCreate",                                                appender.getConfig().getAutoCreate());
        assertEquals("shard count",         7,                                  appender.getConfig().getShardCount());
        assertEquals("retention period",    Integer.valueOf(48),                appender.getConfig().getRetentionPeriod());
        assertEquals("max delay",           1234L,                              appender.getConfig().getBatchDelay());
        assertEquals("discard threshold",   54321,                              appender.getConfig().getDiscardThreshold());
        assertEquals("discard action",      "newest",                           appender.getConfig().getDiscardAction());
        assertEquals("client factory",      "com.example.Foo.bar",              appender.getConfig().getClientFactory());
        assertEquals("client region",       "us-west-1",                        appender.getConfig().getClientRegion());
        assertEquals("client endpoint",     "kinesis.us-west-2.amazonaws.com",  appender.getConfig().getClientEndpoint());
        assertFalse("synchronous mode",                                         appender.getConfig().isSynchronous());
        assertFalse("use shutdown hook",                                        appender.getConfig().isUseShutdownHook());
    }


    @Test
    public void testWriterInitialization() throws Exception
    {
        // property has to be set before initialization
        System.setProperty("TestKinesisAppender.testWriterInitialization", "example");

        initialize("testWriterInitialization");

        assertEquals("configured stream name",      "MyStream-{sysprop:TestKinesisAppender.testWriterInitialization}",  appender.getConfig().getStreamName());
        assertEquals("configured partition key",    "{date}-{bogus}",                                                   appender.getConfig().getPartitionKey());

        logger.debug("this triggers writer creation");

        MockKinesisWriter writer = appender.getMockWriter();

        assertEquals("writer stream name",              "MyStream-example",                 writer.config.getStreamName());
        assertRegex("writer partition key",             "20\\d{6}-\\{bogus}",               writer.config.getPartitionKey());
        assertTrue("writer autoCreate",                                                     writer.config.getAutoCreate());
        assertEquals("writer shardCount",               7,                                  writer.config.getShardCount());
        assertEquals("writer retentionPeriod",          Integer.valueOf(48),                writer.config.getRetentionPeriod());
        assertEquals("writer batch delay",              1234L,                              writer.config.getBatchDelay());
        assertEquals("writer discard threshold",        54321,                              writer.config.getDiscardThreshold());
        assertEquals("writer discard action",           DiscardAction.newest,               writer.config.getDiscardAction());
        assertEquals("writer client factory method",    "com.example.Foo.bar",              writer.config.getClientFactoryMethod());
        assertEquals("writer client endpoint",          "kinesis.us-west-1.amazonaws.com",  writer.config.getClientEndpoint());
    }


    @Test
    public void testWriterInitializationWithLookups() throws Exception
    {
        // property has to be set before initialization
        System.setProperty("TestKinesisAppender.testWriterInitialization", "example");

        initialize("testWriterInitializationWithLookups");

        assertEquals("configured stream name",      "${sys:TestKinesisAppender.testWriterInitialization}",  appender.getConfig().getStreamName());
        assertEquals("configured partition key",    "${awslogs:pid}-{pid}",                                 appender.getConfig().getPartitionKey());

        logger.debug("this triggers writer creation");

        MockKinesisWriter writer = appender.getMockWriter();

        assertEquals("writer log group name",       "example",                  writer.config.getStreamName());
        assertRegex("writer log stream name",       "[0-9]{1,5}-[0-9]{1,5}",    writer.config.getPartitionKey());

        // we'll assume everything else was set as in above test
    }


    @Test
    public void testWriterInitializationSynchronousMode() throws Exception
    {
        initialize("testWriterInitializationSynchronousMode");

        logger.debug("this triggers writer creation");

        MockKinesisWriter writer = appender.getMockWriter();

        assertTrue("synchronous mode",                                                      writer.config.getSynchronousMode());
        assertEquals("batch delay",                     0L,                                 writer.config.getBatchDelay());
    }
}