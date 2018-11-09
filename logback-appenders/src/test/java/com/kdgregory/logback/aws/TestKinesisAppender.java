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

import java.net.URL;

import org.junit.Test;
import static org.junit.Assert.*;

import org.slf4j.LoggerFactory;

import net.sf.kdgcommons.lang.StringUtil;
import net.sf.kdgcommons.test.StringAsserts;

import com.kdgregory.logging.aws.kinesis.KinesisWriterStatistics;
import com.kdgregory.logback.testhelpers.kinesis.TestableKinesisAppender;
import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.factories.DefaultThreadFactory;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.testhelpers.TestingException;
import com.kdgregory.logging.testhelpers.ThrowingWriterFactory;
import com.kdgregory.logging.testhelpers.kinesis.MockKinesisWriter;
import com.kdgregory.logging.testhelpers.kinesis.MockKinesisWriterFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;


/**
 *  These tests exercise the high-level logic of the appender.
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

        LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
        context.reset();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(config);

        logger = context.getLogger(getClass());
        appender = (TestableKinesisAppender)logger.getAppender("KINESIS");
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testConfiguration() throws Exception
    {
        initialize("TestKinesisAppender/testConfiguration.xml");

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
        initialize("TestKinesisAppender/testDefaultConfiguration.xml");

        // don't test stream name because there's no default
        assertEquals("partition key",       "{startupTimestamp}",               appender.getPartitionKey());
        assertEquals("max delay",           2000L,                              appender.getBatchDelay());
        assertEquals("discard threshold",   10000,                              appender.getDiscardThreshold());
        assertEquals("discard action",      "oldest",                           appender.getDiscardAction());
        assertEquals("client factory",      null,                               appender.getClientFactory());
        assertEquals("client endpoint",     null,                               appender.getClientEndpoint());
        assertFalse("autoCreate",                                               appender.isAutoCreate());
        assertEquals("shard count",         1,                                  appender.getShardCount());
        assertEquals("retention period",    24,                                 appender.getRetentionPeriod());
    }


    @Test
    public void testAppend() throws Exception
    {
        initialize("TestKinesisAppender/testAppend.xml");

        MockKinesisWriterFactory writerFactory = appender.getWriterFactory();
        MockKinesisWriter writer = appender.getMockWriter();

        assertNotNull("after initializaton, writer exists",                     writer);
        StringAsserts.assertRegex("stream name, with substitutions",            "argle-\\d+",   writer.streamName);
        StringAsserts.assertRegex("default partition key, after substitutions", "20\\d{12}",    writer.partitionKey);
        assertEquals("calls to writer factory",                                 1,              writerFactory.invocationCount);

        long initialTimestamp = System.currentTimeMillis();

        // this sleep is to make timestamps discernable
        Thread.sleep(100);

        logger.debug("first message");

        assertEquals("after message 1, number of messages in writer",           1,              writer.messages.size());

        logger.error("test with exception", new Exception("this is a test"));

        assertEquals("after message 2, number of messages in writer",           2,          writer.messages.size());

        long finalTimestamp = System.currentTimeMillis();

        LogMessage message1 = writer.messages.get(0);
        assertTrue("message 1 timestamp >= initial timestamp", message1.getTimestamp() >= initialTimestamp);
        assertTrue("message 1 timestamp <= batch timestamp",   message1.getTimestamp() <= finalTimestamp);

        StringAsserts.assertRegex(
                "message 1 follows layout: " + message1.getMessage(),
                "20[12][0-9] TestKinesisAppender first message",
                message1.getMessage());

        LogMessage message2 = writer.messages.get(1);
        assertTrue("message 2 includes exception",
                   message2.getMessage().indexOf("java.lang.Exception") > 0);
        assertTrue("message 2 includes exception",
                   message2.getMessage().indexOf("this is a test") > 0);

        // since we have the writer, we can verify that setting the batch delay gets propagated

        appender.setBatchDelay(1234567);
        assertEquals("writer batch delay propagated", 1234567, writer.batchDelay);
    }


    @Test
    public void testStopAppender() throws Exception
    {
        initialize("TestKinesisAppender/testAppend.xml");

        MockKinesisWriter writer = appender.getMockWriter();

        appender.stop();

        logger.error("blah blah blah");

        assertEquals("nothing was written", 0, writer.messages.size());

        // TODO - once the InternalLogger is implemented, verify that this caused a warning
    }


    @Test
    public void testWriteHeaderAndFooter() throws Exception
    {
        initialize("TestKinesisAppender/testWriteHeaderAndFooter.xml");

        MockKinesisWriter mockWriter = appender.getMockWriter();

        logger.debug("blah blah blah");

        appender.stop();

        assertEquals("number of messages",  3,                  mockWriter.messages.size());
        assertEquals("header is first",     "File Header",      mockWriter.getMessage(0));
        assertEquals("message is middle",   "blah blah blah",   mockWriter.getMessage(1));
        assertEquals("footer is last",      "File Footer",      mockWriter.getMessage(2));
    }


    @Test
    public void testMaximumMessageSize() throws Exception
    {
        final int kinesisMaximumMessageSize = 1024 * 1024;      // 1 MB
        final int layoutOverhead            = 1;                // newline after message
        final int partitionKeySize          = 4;                // "test"

        final int maxMessageSize            =  kinesisMaximumMessageSize - (layoutOverhead + partitionKeySize);
        final String bigMessage             =  StringUtil.repeat('A', maxMessageSize);

        initialize("TestKinesisAppender/testMaximumMessageSize.xml");

        assertFalse("max message size",             appender.isMessageTooLarge(new LogMessage(System.currentTimeMillis(), bigMessage)));
        assertTrue("bigger than max message size",  appender.isMessageTooLarge(new LogMessage(System.currentTimeMillis(), bigMessage + "1")));
    }


    @Test
    public void testUncaughtExceptionHandling() throws Exception
    {
        initialize("TestKinesisAppender/testUncaughtExceptionHandling.xml");

        // note that we will be running the writer on a separate thread

        appender.setThreadFactory(new DefaultThreadFactory("test"));
        appender.setWriterFactory(new ThrowingWriterFactory<KinesisWriterConfig,KinesisWriterStatistics>());

        KinesisWriterStatistics appenderStats = appender.getAppenderStatistics();

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


    @Test
    public void testReconfigureDiscardProperties() throws Exception
    {
        initialize("TestKinesisAppender/testReconfigureDiscardProperties.xml");

        MockKinesisWriter writer = appender.getMockWriter();

        assertEquals("initial discard threshold, from appender",    12345,                              appender.getDiscardThreshold());
        assertEquals("initial discard action, from appender",       DiscardAction.newest.toString(),    appender.getDiscardAction());

        assertEquals("initial discard threshold, from writer",      12345,                              writer.discardThreshold);
        assertEquals("initial discard action, from writer",         DiscardAction.newest,               writer.discardAction);

        appender.setDiscardThreshold(54321);
        appender.setDiscardAction(DiscardAction.oldest.toString());

        assertEquals("updated discard threshold, from appender",    54321,                              appender.getDiscardThreshold());
        assertEquals("updated discard action, from appender",       DiscardAction.oldest.toString(),    appender.getDiscardAction());

        assertEquals("updated discard threshold, from writer",      54321,                              writer.discardThreshold);
        assertEquals("updated discard action, from writer",         DiscardAction.oldest,               writer.discardAction);
    }
}
