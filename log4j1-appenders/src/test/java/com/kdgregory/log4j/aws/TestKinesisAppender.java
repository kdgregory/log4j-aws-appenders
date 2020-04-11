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

package com.kdgregory.log4j.aws;

import static net.sf.kdgcommons.test.StringAsserts.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.helpers.LogLog;

import com.kdgregory.log4j.testhelpers.kinesis.TestableKinesisAppender;
import com.kdgregory.logging.common.util.DiscardAction;
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
        super("TestKinesisAppender/", "kinesis");
    }


    @Before
    public void setUp()
    {
        LogManager.resetConfiguration();
        LogLog.setQuietMode(true);
    }


    @After
    public void tearDown()
    {
        appender.close();
        LogLog.setQuietMode(false);
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testConfiguration() throws Exception
    {
        initialize("testConfiguration");

        assertEquals("stream name",         "argle-{bargle}",                   appender.getStreamName());
        assertEquals("partition key",       "foo-{date}",                       appender.getPartitionKey());
        assertTrue("autoCreate",                                                appender.isAutoCreate());
        assertEquals("shard count",         7,                                  appender.getShardCount());
        assertEquals("retention period",    48,                                 appender.getRetentionPeriod());
        assertEquals("batch delay",         1234L,                              appender.getBatchDelay());
        assertEquals("discard threshold",   54321,                              appender.getDiscardThreshold());
        assertEquals("discard action",      "newest",                           appender.getDiscardAction());
        assertFalse("synchronous mode",                                         appender.getSynchronous());
        assertFalse("use shutdown hook",                                        appender.getUseShutdownHook());
        assertEquals("assumed role",        "AssumableRole",                    appender.getAssumedRole());
        assertEquals("client factory",      "com.example.Foo.bar",              appender.getClientFactory());
        assertEquals("client region",       "us-west-1",                        appender.getClientRegion());
        assertEquals("client endpoint",     "kinesis.us-west-2.amazonaws.com",  appender.getClientEndpoint());
    }


    @Test
    public void testDefaultConfiguration() throws Exception
    {
        initialize("testDefaultConfiguration");

        // don't test stream name because there's no default
        assertEquals("partition key",       "{startupTimestamp}",               appender.getPartitionKey());
        assertFalse("autoCreate",                                               appender.isAutoCreate());
        assertEquals("shard count",         1,                                  appender.getShardCount());
        assertEquals("retention period",    24,                                 appender.getRetentionPeriod());
        assertEquals("batch delay",         2000L,                              appender.getBatchDelay());
        assertEquals("discard threshold",   10000,                              appender.getDiscardThreshold());
        assertEquals("discard action",      "oldest",                           appender.getDiscardAction());
        assertFalse("synchronous mode",                                         appender.getSynchronous());
        assertTrue("use shutdown hook",                                         appender.getUseShutdownHook());
        assertEquals("assumed role",        null,                               appender.getAssumedRole());
        assertEquals("client factory",      null,                               appender.getClientFactory());
        assertEquals("client region",       null,                               appender.getClientRegion());
        assertEquals("client endpoint",     null,                               appender.getClientEndpoint());
    }


    @Test
    public void testSynchronousConfiguration() throws Exception
    {
        initialize("testSynchronousConfiguration");

        // all we care about is the interaction between synchronous and batchDelay

        assertTrue("synchronous mode",                                      appender.getSynchronous());
        assertEquals("batch delay",         0L,                             appender.getBatchDelay());
    }


    @Test
    public void testWriterInitialization() throws Exception
    {
        // property has to be set before initialization
        System.setProperty("TestKinesisAppender.testWriterInitialization", "example");

        initialize("testWriterInitialization");

        assertEquals("configured stream name",      "MyStream-{sysprop:TestKinesisAppender.testWriterInitialization}",  appender.getStreamName());
        assertEquals("configured partition key",    "{date}-{bogus}",                                                   appender.getPartitionKey());

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
