// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.cloudwatch;

import java.net.URL;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import net.sf.kdgcommons.test.StringAsserts;

import com.kdgregory.log4j.cloudwatch.helpers.HeaderFooterLayout;
import com.kdgregory.log4j.cloudwatch.helpers.MockCloudwatchWriter;


public class TestCloudwatchAppender
{
    @Test
    public void testConfiguration() throws Exception
    {
        URL config = ClassLoader.getSystemResource("TestCloudwatchAppender.testConfiguration.properties");
        PropertyConfigurator.configure(config);

        Logger rootLogger = Logger.getRootLogger();
        CloudwatchAppender appender = (CloudwatchAppender)rootLogger.getAppender("default");

        assertEquals("log group name",  "argle",    appender.getLogGroup());
        assertEquals("log stream name", "bargle",   appender.getLogStream());
        assertEquals("batch size",      100,        appender.getBatchSize());
        assertEquals("max delay",       1234L,      appender.getMaxDelay());
    }


    @Test
    public void testDefaultConfiguration() throws Exception
    {
        URL config = ClassLoader.getSystemResource("TestCloudwatchAppender.testDefaultConfiguration.properties");
        PropertyConfigurator.configure(config);

        Logger rootLogger = Logger.getRootLogger();
        CloudwatchAppender appender = (CloudwatchAppender)rootLogger.getAppender("default");

        StringAsserts.assertRegex(
                     "log stream name", "2[0-9]+.[0-9]+",   appender.getLogStream());
        assertEquals("batch size",      16,                 appender.getBatchSize());
        assertEquals("max delay",       4000L,              appender.getMaxDelay());
    }


    @Test
    public void testAppend() throws Exception
    {
        URL config = ClassLoader.getSystemResource("TestCloudwatchAppender.testAppend.properties");
        PropertyConfigurator.configure(config);

        Logger rootLogger = Logger.getRootLogger();
        CloudwatchAppender appender = (CloudwatchAppender)rootLogger.getAppender("default");

        MockCloudwatchWriter mock = new MockCloudwatchWriter();
        appender.writer = mock;

        long initialTimestamp = appender.lastBatchTimestamp;
        assertTrue("initial timestamp > 0", initialTimestamp > 0);

        // this sleep ensures that the timestamp will be updated
        Thread.sleep(100);

        Logger myLogger = Logger.getLogger(getClass());
        myLogger.debug("test without exception");
        myLogger.error("test with exception", new Exception("this is a test"));

        assertEquals("after message 2, messages in queue",  0, appender.messageQueue.size());
        assertEquals("after message 2, bytes in queue",     0, appender.messageQueueBytes);
        assertTrue("after message 2, batch timestamp updated", appender.lastBatchTimestamp > initialTimestamp);

        List<LogMessage> lastBatch = mock.lastBatch;
        assertEquals("messages in batch",  2, mock.lastBatch.size());

        LogMessage message1 = lastBatch.get(0);
        assertTrue("message 1 timestamp >= initial timestamp", message1.getTimestamp() >= initialTimestamp);
        assertTrue("message 1 timestamp <= batch timestamp",   message1.getTimestamp() <= appender.lastBatchTimestamp);
        StringAsserts.assertRegex(
                "message 1 generally follows layout: " + message1.getMessage(),
                "20[12][0-9]-.* DEBUG .*TestCloudwatchAppender .*test without exception.*",
                message1.getMessage().trim());

        LogMessage message2 = lastBatch.get(1);
        assertTrue("message 2 includes exception",
                   message2.getMessage().indexOf("java.lang.Exception") > 0);
        assertTrue("message 2 includes exception",
                   message2.getMessage().indexOf("this is a test") > 0);

        myLogger.info("this is a third message");
        assertEquals("after message 3, messages in queue",  1, appender.messageQueue.size());
        assertSame("after message 3, last batch hasn't changed", lastBatch, mock.lastBatch);
    }


    @Test(expected=IllegalStateException.class)
    public void testThrowsIfAppenderClosed() throws Exception
    {
        URL config = ClassLoader.getSystemResource("TestCloudwatchAppender.testAppend.properties");
        PropertyConfigurator.configure(config);

        Logger rootLogger = Logger.getRootLogger();
        CloudwatchAppender appender = (CloudwatchAppender)rootLogger.getAppender("default");

        MockCloudwatchWriter mock = new MockCloudwatchWriter();
        appender.writer = mock;
        appender.close();

        Logger myLogger = Logger.getLogger(getClass());
        myLogger.error("blah blah blah");
    }


    @Test
    public void testWriteHeaderAndFooter() throws Exception
    {
        URL config = ClassLoader.getSystemResource("TestCloudwatchAppender.testWriteHeaderAndFooter.properties");
        PropertyConfigurator.configure(config);

        Logger rootLogger = Logger.getRootLogger();
        CloudwatchAppender appender = (CloudwatchAppender)rootLogger.getAppender("default");

        MockCloudwatchWriter mock = new MockCloudwatchWriter();
        appender.writer = mock;

        Logger myLogger = Logger.getLogger(getClass());
        myLogger.debug("blah blah blah");
        LogManager.shutdown();

        assertEquals("number of messages written to log", 3, mock.messages.size());
        assertEquals("header is first", HeaderFooterLayout.HEADER, mock.getMessage(0));
        assertEquals("footer is last",  HeaderFooterLayout.FOOTER, mock.getMessage(2));
    }

}
