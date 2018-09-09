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

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.LogLog;

import net.sf.kdgcommons.lang.StringUtil;
import net.sf.kdgcommons.test.StringAsserts;

import com.kdgregory.log4j.aws.internal.kinesis.KinesisAppenderStatistics;
import com.kdgregory.log4j.aws.internal.kinesis.KinesisWriterConfig;
import com.kdgregory.log4j.aws.internal.shared.DefaultThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.testhelpers.HeaderFooterLayout;
import com.kdgregory.log4j.testhelpers.InlineThreadFactory;
import com.kdgregory.log4j.testhelpers.TestingException;
import com.kdgregory.log4j.testhelpers.ThrowingWriterFactory;
import com.kdgregory.log4j.testhelpers.aws.kinesis.MockKinesisWriter;
import com.kdgregory.log4j.testhelpers.aws.kinesis.MockKinesisWriterFactory;
import com.kdgregory.log4j.testhelpers.aws.kinesis.TestableKinesisAppender;

/**
 *  These tests exercise the high-level logic of the appender: configuration
 *  and interaction with the writer. To do so, it mocks the LogWriter.
 */
public class TestKinesisAppender
{
    private Logger logger;
    private TestableKinesisAppender appender;


    private void initialize(String propsName)
    throws Exception
    {
        URL config = ClassLoader.getSystemResource(propsName);
        assertNotNull("was able to retrieve config", config);
        PropertyConfigurator.configure(config);

        logger = Logger.getLogger(getClass());

        Logger rootLogger = Logger.getRootLogger();
        appender = (TestableKinesisAppender)rootLogger.getAppender("default");

        appender.setThreadFactory(new InlineThreadFactory());
        appender.setWriterFactory(new MockKinesisWriterFactory(appender));
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
        initialize("TestKinesisAppender/testConfiguration.properties");

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
        initialize("TestKinesisAppender/testDefaultConfiguration.properties");

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
    public void testAppend() throws Exception
    {
        initialize("TestKinesisAppender/testAppend.properties");
        MockKinesisWriterFactory writerFactory = appender.getWriterFactory();

        long initialTimestamp = System.currentTimeMillis();

        // this sleep is to make timestamps discernable
        Thread.sleep(100);

        assertNull("before messages, writer is null",                           appender.getMockWriter());

        logger.debug("first message");

        MockKinesisWriter writer = appender.getMockWriter();

        assertNotNull("after message 1, writer is initialized",                 writer);
        assertEquals("after message 1, calls to writer factory",                1,              writerFactory.invocationCount);

        StringAsserts.assertRegex("stream name, with substitutions",            "argle-\\d+",   writer.streamName);
        StringAsserts.assertRegex("default partition key, after substitutions", "20\\d{12}",    writer.partitionKey);
        assertEquals("after message 1, number of messages in writer",           1,              writer.messages.size());

        logger.error("test with exception", new Exception("this is a test"));

        assertEquals("after message 2, calls to writer factory",                1,          writerFactory.invocationCount);
        assertEquals("after message 2, number of messages in writer",           2,          writer.messages.size());

        long finalTimestamp = System.currentTimeMillis();

        LogMessage message1 = writer.messages.get(0);
        assertTrue("message 1 timestamp >= initial timestamp", message1.getTimestamp() >= initialTimestamp);
        assertTrue("message 1 timestamp <= batch timestamp",   message1.getTimestamp() <= finalTimestamp);
        StringAsserts.assertRegex(
                "message 1 generally follows layout: " + message1.getMessage(),
                "20[12][0-9]-.* DEBUG .*TestKinesisAppender .*first message.*",
                message1.getMessage().trim());

        LogMessage message2 = writer.messages.get(1);
        assertTrue("message 2 includes exception",
                   message2.getMessage().indexOf("java.lang.Exception") > 0);
        assertTrue("message 2 includes exception",
                   message2.getMessage().indexOf("this is a test") > 0);

        // since we have the writer, we can verify that setting the batch delay gets propagated

        appender.setBatchDelay(1234567);
        assertEquals("writer batch delay propagated", 1234567, writer.batchDelay);
    }


    @Test(expected=IllegalStateException.class)
    public void testThrowsIfAppenderClosed() throws Exception
    {
        initialize("TestKinesisAppender/testAppend.properties");

        // write the first message to initialize the appender
        logger.debug("should not throw");

        appender.close();

        // second message should throw
        logger.error("blah blah blah");
    }


    @Test
    public void testWriteHeaderAndFooter() throws Exception
    {
        initialize("TestKinesisAppender/testWriteHeaderAndFooter.properties");

        logger.debug("blah blah blah");

        // must retrieve writer before we shut down
        MockKinesisWriter writer = appender.getMockWriter();
        LogManager.shutdown();

        assertEquals("number of messages written to log", 3, writer.messages.size());
        assertEquals("header is first", HeaderFooterLayout.HEADER, writer.getMessage(0));
        assertEquals("footer is last",  HeaderFooterLayout.FOOTER, writer.getMessage(2));
    }


    @Test
    public void testMaximumMessageSize() throws Exception
    {
        final int kinesisMaximumMessageSize = 1024 * 1024;      // 1 MB
        final int layoutOverhead            = 1;                // newline after message
        final int partitionKeySize          = 4;                // "test"

        final int maxMessageSize            =  kinesisMaximumMessageSize - (layoutOverhead + partitionKeySize);
        final String bigMessage             =  StringUtil.repeat('A', maxMessageSize);

        initialize("TestKinesisAppender/testMaximumMessageSize.properties");
        logger.debug("this message triggers writer configuration");

        assertFalse("max message size",             appender.isMessageTooLarge(new LogMessage(System.currentTimeMillis(), bigMessage)));
        assertTrue("bigger than max message size",  appender.isMessageTooLarge(new LogMessage(System.currentTimeMillis(), bigMessage + "1")));
    }


    @Test
    public void testUncaughtExceptionHandling() throws Exception
    {
        initialize("TestKinesisAppender/testUncaughtExceptionHandling.properties");

        // note that we will be running the writer on a separate thread

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(new ThrowingWriterFactory<KinesisWriterConfig,KinesisAppenderStatistics>());

        KinesisAppenderStatistics appenderStats = appender.getAppenderStatistics();

        logger.debug("this should trigger writer creation");

        assertNull("writer has not yet thrown", appenderStats.getLastError());

        logger.debug("this should trigger writer throwage");

        // without getting really clever, the best way to wait for the throw to be reported is to sit and spin
        for (int ii = 0 ; (ii < 10) && (appenderStats.getLastError() == null) ; ii++)
        {
            Thread.sleep(10);
        }

        assertNull("writer has been reset",         appender.getWriter());
        assertEquals("last writer exception class", TestingException.class, appenderStats.getLastError().getClass());
    }
}
