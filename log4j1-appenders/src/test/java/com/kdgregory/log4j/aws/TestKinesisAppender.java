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

import java.net.URL;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import static net.sf.kdgcommons.test.StringAsserts.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.LogLog;

import com.kdgregory.log4j.testhelpers.kinesis.TestableKinesisAppender;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.testhelpers.kinesis.MockKinesisWriter;


/**
 *  These tests exercise appender logic specific to KinesisAppender, using a
 *  mock log-writer.
 */
public class TestKinesisAppender
{
    private Logger logger;
    private TestableKinesisAppender appender;


    private void initialize(String testName)
    throws Exception
    {
        String propsName = "TestKinesisAppender/" + testName + ".properties";
        URL config = ClassLoader.getSystemResource(propsName);
        assertNotNull("was able to retrieve config", config);
        PropertyConfigurator.configure(config);

        logger = Logger.getLogger(getClass());

        Logger rootLogger = Logger.getRootLogger();
        appender = (TestableKinesisAppender)rootLogger.getAppender("default");
    }

//----------------------------------------------------------------------------
//  JUnit stuff
//----------------------------------------------------------------------------

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
        assertEquals("max delay",           1234L,                              appender.getBatchDelay());
        assertEquals("discard threshold",   54321,                              appender.getDiscardThreshold());
        assertEquals("discard action",      "newest",                           appender.getDiscardAction());
        assertEquals("client factory",      "com.example.Foo.bar",              appender.getClientFactory());
        assertEquals("client endpoint",     "kinesis.us-west-1.amazonaws.com",  appender.getClientEndpoint());
        assertTrue("autoCreate",                                                appender.isAutoCreate());
        assertEquals("shard count",         7,                                  appender.getShardCount());
        assertEquals("retention period",    48,                                 appender.getRetentionPeriod());
    }


    @Test
    public void testDefaultConfiguration() throws Exception
    {
        initialize("testDefaultConfiguration");

        // don't test stream name because there's no default
        assertEquals("partition key",       "{startupTimestamp}",               appender.getPartitionKey());
        assertEquals("max delay",           2000L,                              appender.getBatchDelay());
        assertEquals("discard threshold",   10000,                              appender.getDiscardThreshold());
        assertEquals("discard action",      "oldest",                           appender.getDiscardAction());
        assertEquals("client factory",      null,                               appender.getClientFactory());
        assertEquals("client endpoint",     null,                               appender.getClientEndpoint());
        assertFalse("autoCreate",                                                appender.isAutoCreate());
        assertEquals("shard count",         1,                                  appender.getShardCount());
        assertEquals("retention period",    24,                                 appender.getRetentionPeriod());
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
