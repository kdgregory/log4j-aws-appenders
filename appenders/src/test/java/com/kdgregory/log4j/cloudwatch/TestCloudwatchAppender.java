// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.cloudwatch;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import net.sf.kdgcommons.test.StringAsserts;

import com.kdgregory.log4j.cloudwatch.helpers.HeaderFooterLayout;
import com.kdgregory.log4j.cloudwatch.helpers.MockCloudwatchWriter;
import com.kdgregory.log4j.shared.LogMessage;


public class TestCloudwatchAppender
{
    private RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
    private SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMdd");
    {
        dateFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }
    private SimpleDateFormat timestampFormatter = new SimpleDateFormat("yyyyMMddHHmmss");
    {
        timestampFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));
    }


    private CloudwatchAppender initialize(String propsName)
    throws Exception
    {
        URL config = ClassLoader.getSystemResource(propsName);
        PropertyConfigurator.configure(config);

        Logger rootLogger = Logger.getRootLogger();
        return (CloudwatchAppender)rootLogger.getAppender("default");
    }


    @Test
    public void testConfiguration() throws Exception
    {
        CloudwatchAppender appender = initialize("TestCloudwatchAppender.testConfiguration.properties");

        assertEquals("log group name",  "argle",    appender.getLogGroup());
        assertEquals("log stream name", "bargle",   appender.getLogStream());
        assertEquals("batch size",      100,        appender.getBatchSize());
        assertEquals("max delay",       1234L,      appender.getMaxDelay());
    }


    @Test
    public void testDefaultConfiguration() throws Exception
    {
        String startTimestamp = timestampFormatter.format(new Date(runtimeMxBean.getStartTime()));

        CloudwatchAppender appender = initialize("TestCloudwatchAppender.testDefaultConfiguration.properties");

        // note: this is allowed at time of configuration, would disable logger if we try to append
        assertNull("log group name",    appender.getLogGroup());

        assertEquals("log stream name", startTimestamp, appender.getLogStream());
        assertEquals("batch size",      16,             appender.getBatchSize());
        assertEquals("max delay",       4000L,          appender.getMaxDelay());
    }


    @Test
    public void testGroupAndStreamSubstitutions() throws Exception
    {
        String currentDate = dateFormatter.format(new Date());

        CloudwatchAppender appender = initialize("TestCloudwatchAppender.testGroupAndStreamSubstitutions.properties");

        assertEquals("log group name",  "MyGroup-" + currentDate + "-123",    appender.getLogGroup());
        assertEquals("log stream name", "MyStream-" + currentDate + "-456",   appender.getLogStream());
    }


    @Test
    public void testAppend() throws Exception
    {
        CloudwatchAppender appender = initialize("TestCloudwatchAppender.testAppend.properties");

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
        CloudwatchAppender appender = initialize("TestCloudwatchAppender.testAppend.properties");

        MockCloudwatchWriter mock = new MockCloudwatchWriter();
        appender.writer = mock;
        appender.close();

        Logger myLogger = Logger.getLogger(getClass());
        myLogger.error("blah blah blah");
    }


    @Test
    public void testWriteHeaderAndFooter() throws Exception
    {
        CloudwatchAppender appender = initialize("TestCloudwatchAppender.testWriteHeaderAndFooter.properties");

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
