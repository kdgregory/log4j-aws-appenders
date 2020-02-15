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

import java.net.URI;

import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import com.kdgregory.log4j2.aws.KinesisAppender.KinesisAppenderBuilder;
import com.kdgregory.log4j2.testhelpers.TestableKinesisAppender;
import com.kdgregory.log4j2.testhelpers.TestableKinesisAppender.TestableKinesisAppenderBuilder;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.testhelpers.kinesis.MockKinesisWriter;


public class TestKinesisAppender
{
    private Logger logger;
    private TestableKinesisAppender appender;


    private void initialize(String testName)
    throws Exception
    {
        String propsName = "TestKinesisAppender/" + testName + ".xml";
        URI config = ClassLoader.getSystemResource(propsName).toURI();
        assertNotNull("was able to retrieve config", config);

        LoggerContext context = LoggerContext.getContext();
        context.setConfigLocation(config);

        logger = context.getLogger(getClass().getName());
        appender = (TestableKinesisAppender)logger.getAppenders().get("KINESIS");
        assertNotNull("was able to retrieve appender", appender);
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
        assertEquals("discard threshold",   54321,                              appender.getConfig().getDiscardThreshold());
        assertEquals("discard action",      "newest",                           appender.getConfig().getDiscardAction());
        assertEquals("client factory",      "com.example.Foo.bar",              appender.getConfig().getClientFactory());
        assertEquals("client region",       "us-west-1",                        appender.getConfig().getClientRegion());
        assertEquals("client endpoint",     "kinesis.us-west-2.amazonaws.com",  appender.getConfig().getClientEndpoint());
        assertFalse("synchronous mode",                                         appender.getConfig().isSynchronous());
        assertFalse("use shutdown hook",                                        appender.getConfig().isUseShutdownHook());
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
        assertEquals("discard threshold",   10000,                              appender.getConfig().getDiscardThreshold());
        assertEquals("discard action",      "oldest",                           appender.getConfig().getDiscardAction());
        assertEquals("client factory",      null,                               appender.getConfig().getClientFactory());
        assertEquals("client region",       null,                               appender.getConfig().getClientRegion());
        assertEquals("client endpoint",     null,                               appender.getConfig().getClientEndpoint());
        assertFalse("synchronous mode",                                         appender.getConfig().isSynchronous());
        assertTrue("use shutdown hook",                                         appender.getConfig().isUseShutdownHook());
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

        assertEquals("writer stream name",              "MyStream-example",                 writer.config.streamName);
        assertRegex("writer partition key",             "20\\d{6}-\\{bogus}",               writer.config.partitionKey);
        assertTrue("writer autoCreate",                                                     writer.config.autoCreate);
        assertEquals("writer shardCount",               7,                                  writer.config.shardCount);
        assertEquals("writer retentionPeriod",          Integer.valueOf(48),                writer.config.retentionPeriod);
        assertEquals("writer batch delay",              1234L,                              writer.config.batchDelay);
        assertEquals("writer discard threshold",        54321,                              writer.config.discardThreshold);
        assertEquals("writer discard action",           DiscardAction.newest,               writer.config.discardAction);
        assertEquals("writer client factory method",    "com.example.Foo.bar",              writer.config.clientFactoryMethod);
        assertEquals("writer client endpoint",          "kinesis.us-west-1.amazonaws.com",  writer.config.clientEndpoint);
    }
}
