// Copyright (c) Keith D Gregory, all rights reserved
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

import com.kdgregory.log4j.aws.internal.kinesis.KinesisWriterConfig;
import com.kdgregory.log4j.aws.internal.shared.DefaultThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.testhelpers.log4j.HeaderFooterLayout;
import com.kdgregory.testhelpers.log4j.NullThreadFactory;
import com.kdgregory.testhelpers.log4j.aws.ThrowingWriterFactory;
import com.kdgregory.testhelpers.log4j.aws.kinesis.MockKinesisWriter;
import com.kdgregory.testhelpers.log4j.aws.kinesis.MockKinesisWriterFactory;
import com.kdgregory.testhelpers.log4j.aws.kinesis.TestableKinesisAppender;


public class TestKinesisAppender
{
    private Logger logger;
    private TestableKinesisAppender appender;


    private void initialize(String propsName)
    throws Exception
    {
        URL config = ClassLoader.getSystemResource(propsName);
        PropertyConfigurator.configure(config);

        logger = Logger.getLogger(getClass());

        Logger rootLogger = Logger.getRootLogger();
        appender = (TestableKinesisAppender)rootLogger.getAppender("default");

        appender.setThreadFactory(new NullThreadFactory());
        appender.setWriterFactory(new MockKinesisWriterFactory(appender));
    }


    @Before
    public void setUp()
    {
        LogLog.setQuietMode(true);
    }


    @After
    public void tearDown()
    {
        LogLog.setQuietMode(false);
    }


//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testConfiguration() throws Exception
    {
        initialize("TestKinesisAppender.testConfiguration.properties");

        assertEquals("stream name",         "argle-{bargle}",       appender.getStreamName());
        assertEquals("partition key",       "foo-{date}",           appender.getPartitionKey());
        assertEquals("max delay",           1234L,                  appender.getBatchDelay());
    }


    @Test
    public void testDefaultConfiguration() throws Exception
    {
        initialize("TestKinesisAppender.testDefaultConfiguration.properties");

        // note: this is allowed at time of configuration, but would disable logger if we try to append
        assertNull("log group name",        appender.getStreamName());
        assertEquals("log stream name",     "{startupTimestamp}",   appender.getPartitionKey());
        assertEquals("max delay",           2000L,                  appender.getBatchDelay());
    }


    @Test
    public void testAppend() throws Exception
    {
        initialize("TestKinesisAppender.testAppend.properties");
        MockKinesisWriterFactory writerFactory = appender.getWriterFactory();

        long initialTimestamp = System.currentTimeMillis();

        // this sleep is to make timestamps discernable
        Thread.sleep(100);

        assertNull("before messages, writer is null",                           appender.getWriter());

        logger.debug("first message");

        MockKinesisWriter writer = appender.getWriter();

        assertNotNull("after message 1, writer is initialized",                 writer);
        assertEquals("after message 1, calls to writer factory",                1,              writerFactory.invocationCount);

        assertRegex("stream name, with substitutions",                          "argle-\\d+",   writer.streamName);
        assertRegex("default partition key, after substitutions",               "20\\d{12}",    writer.partitionKey);
        assertEquals("after message 1, number of messages in writer",           1,              writer.messages.size());

        logger.error("test with exception", new Exception("this is a test"));

        assertEquals("after message 2, calls to writer factory",                1,          writerFactory.invocationCount);
        assertEquals("after message 2, number of messages in writer",           2,          writer.messages.size());

        long finalTimestamp = System.currentTimeMillis();

        LogMessage message1 = writer.messages.get(0);
        assertTrue("message 1 timestamp >= initial timestamp", message1.getTimestamp() >= initialTimestamp);
        assertTrue("message 1 timestamp <= batch timestamp",   message1.getTimestamp() <= finalTimestamp);
        assertRegex(
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
        initialize("TestKinesisAppender.testAppend.properties");

        // write the first message to initialize the appender
        logger.debug("should not throw");

        appender.close();

        // second message should throw
        logger.error("blah blah blah");
    }


    @Test
    public void testWriteHeaderAndFooter() throws Exception
    {
        initialize("TestKinesisAppender.testWriteHeaderAndFooter.properties");

        logger.debug("blah blah blah");

        // must retrieve writer before we shut down
        MockKinesisWriter writer = appender.getWriter();
        LogManager.shutdown();

        assertEquals("number of messages written to log", 3, writer.messages.size());
        assertEquals("header is first", HeaderFooterLayout.HEADER, writer.getMessage(0));
        assertEquals("footer is last",  HeaderFooterLayout.FOOTER, writer.getMessage(2));
    }


    @Test
    public void testWriterErrorHandling() throws Exception
    {
        initialize("TestKinesisAppender.testWriterErrorHandling.properties");

        // note that we will be running the writer on a separate thread

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(new ThrowingWriterFactory<KinesisWriterConfig>());

        logger.debug("this should trigger writer creation");
        
        assertNull("writer has not yet thrown",         appender.getLastWriterException());

        logger.debug("this should trigger writer throwage");

        // without getting really clever, the best way to wait for the throw to be reported is to sit and spin
        for (int ii = 0 ; (ii < 10) && (appender.getLastWriterException() == null) ; ii++)
        {
            Thread.sleep(10);
        }

        assertNull("writer has been reset",         appender.getWriter());
        assertEquals("last writer exception class", IllegalStateException.class, appender.getLastWriterException().getClass());
    }

}
