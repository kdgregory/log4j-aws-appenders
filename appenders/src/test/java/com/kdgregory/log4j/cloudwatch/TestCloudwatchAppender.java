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
import com.kdgregory.log4j.shared.LogMessage;
import com.kdgregory.log4j.shared.LogWriter;
import com.kdgregory.log4j.shared.NullThreadFactory;
import com.kdgregory.log4j.shared.WriterFactory;


public class TestCloudwatchAppender
{

//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

    private static class MockWriterFactory implements WriterFactory
    {
        public CloudwatchAppender appender;

        public int invocationCount = 0;
        public String lastLogGroupName;
        public String lastLogStreamName;
        public MockCloudwatchWriter writer;

        public MockWriterFactory(CloudwatchAppender appender)
        {
            this.appender = appender;
        }

        @Override
        public LogWriter newLogWriter()
        {
            invocationCount++;
            lastLogGroupName = appender.getActualLogGroup();
            lastLogStreamName = appender.getActualLogStream();
            writer = new MockCloudwatchWriter();
            return writer;
        }
    }


    private CloudwatchAppender initialize(String propsName)
    throws Exception
    {
        URL config = ClassLoader.getSystemResource(propsName);
        PropertyConfigurator.configure(config);

        Logger rootLogger = Logger.getRootLogger();
        CloudwatchAppender appender = (CloudwatchAppender)rootLogger.getAppender("default");

        appender.writerFactory = new MockWriterFactory(appender);
        appender.threadFactory = new NullThreadFactory();
        return appender;
    }


//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testConfiguration() throws Exception
    {
        CloudwatchAppender appender = initialize("TestCloudwatchAppender.testConfiguration.properties");

        assertEquals("log group name",  "argle",    appender.getLogGroup());
        assertEquals("log stream name", "bargle",   appender.getLogStream());
        assertEquals("batch size",      100,        appender.getBatchSize());
        assertEquals("max delay",       1234L,      appender.getMaxDelay());
        assertEquals("sequence",        2,          appender.getSequence());
        assertEquals("roll mode",       "interval", appender.getRollMode());
        assertEquals("roll interval",   86400000L,  appender.getRollInterval());
    }


    @Test
    public void testDefaultConfiguration() throws Exception
    {
        CloudwatchAppender appender = initialize("TestCloudwatchAppender.testDefaultConfiguration.properties");

        // note: this is allowed at time of configuration, would disable logger if we try to append
        assertNull("log group name",    appender.getLogGroup());

        assertEquals("log stream name", "{startTimestamp}", appender.getLogStream());
        assertEquals("batch size",      16,                 appender.getBatchSize());
        assertEquals("max delay",       4000L,              appender.getMaxDelay());
        assertEquals("sequence",        0,                  appender.getSequence());
        assertEquals("roll mode",       "none",             appender.getRollMode());
        assertEquals("roll interval",   -1,                 appender.getRollInterval());
    }


    @Test
    public void testAppend() throws Exception
    {
        CloudwatchAppender appender = initialize("TestCloudwatchAppender.testAppend.properties");
        MockWriterFactory writerFactory = (MockWriterFactory)appender.writerFactory;

        assertTrue("before messages, last batch timestamp > 0",    appender.lastBatchTimestamp > 0);
        assertTrue("before messages, last roll timestamp == 0",     appender.lastRollTimestamp == 0);
        assertNull("before messages, writer is null",               appender.writer);

        Logger myLogger = Logger.getLogger(getClass());
        myLogger.debug("first message");

        assertTrue("after messages, last batch timestamp > 0",     appender.lastBatchTimestamp > 0);
        assertTrue("after messages, last roll timestamp > 0",      appender.lastRollTimestamp > 0);
        assertNotNull("after message 1, writer is initialized",     appender.writer);
        assertEquals("after message 1, writer factory called once", 1, writerFactory.invocationCount);
        assertEquals("after message 1, messages in queue",          1, appender.messageQueue.size());
        assertTrue("after message 1, bytes in queue > 0",           appender.messageQueueBytes > 0);

        // sleep so that we'll increment the batch timestamp
        long initialTimestamp = appender.lastBatchTimestamp;
        Thread.sleep(100);

        myLogger.error("test with exception", new Exception("this is a test"));

        assertEquals("after message 2, writer factory called once", 1, writerFactory.invocationCount);
        assertEquals("after message 2, messages in queue",          0, appender.messageQueue.size());
        assertEquals("after message 2, bytes in queue",             0, appender.messageQueueBytes);
        assertTrue("after message 2, batch timestamp updated",      appender.lastBatchTimestamp > initialTimestamp);

        assertEquals("actual log-group name", "argle", writerFactory.lastLogGroupName);
        assertEquals("actual log-stream name", "bargle", writerFactory.lastLogStreamName);

        MockCloudwatchWriter mock = (MockCloudwatchWriter)appender.writer;
        List<LogMessage> lastBatch = mock.lastBatch;
        assertEquals("messages in batch",  2, mock.lastBatch.size());

        LogMessage message1 = lastBatch.get(0);
        assertTrue("message 1 timestamp >= initial timestamp", message1.getTimestamp() >= initialTimestamp);
        assertTrue("message 1 timestamp <= batch timestamp",   message1.getTimestamp() <= appender.lastBatchTimestamp);
        StringAsserts.assertRegex(
                "message 1 generally follows layout: " + message1.getMessage(),
                "20[12][0-9]-.* DEBUG .*TestCloudwatchAppender .*first message.*",
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

        // write the first message to initialize the appender
        Logger myLogger = Logger.getLogger(getClass());
        myLogger.debug("should not throw");

        appender.close();

        // second message should throw
        myLogger.error("blah blah blah");
    }


    @Test
    public void testWriteHeaderAndFooter() throws Exception
    {
        CloudwatchAppender appender = initialize("TestCloudwatchAppender.testWriteHeaderAndFooter.properties");

        Logger myLogger = Logger.getLogger(getClass());
        myLogger.debug("blah blah blah");

        // must retrieve writer before we shut down
        MockCloudwatchWriter mock = (MockCloudwatchWriter)appender.writer;
        LogManager.shutdown();

        assertEquals("number of messages written to log", 3, mock.messages.size());
        assertEquals("header is first", HeaderFooterLayout.HEADER, mock.getMessage(0));
        assertEquals("footer is last",  HeaderFooterLayout.FOOTER, mock.getMessage(2));
    }


    @Test
    public void testSubstitution() throws Exception
    {
        // note that the property value includes invalid characters
        System.setProperty("TestCloudwatchAppender.testSubstitution", "foo/bar");

        CloudwatchAppender appender = initialize("TestCloudwatchAppender.testSubstitution.properties");
        assertNull("actual log group after construction", appender.getActualLogGroup());
        assertNull("actual log stream after construction", appender.getActualLogStream());

        // need to trigger append to apply substitutions
        Logger myLogger = Logger.getLogger(getClass());
        myLogger.debug("doesn't matter what's written");

        // it's easy to check actual value for log group name, but we'll use a regex for stream
        // so that we don't have to muck with timestamps
        assertEquals("actual log group after append",
                     "MyLog-foobar",
                     appender.getActualLogGroup());
        StringAsserts.assertRegex("actual log stream after append",
                                  "MyStream-20\\d{12}-bogus",
                                  appender.getActualLogStream());

        MockWriterFactory writerFactory = (MockWriterFactory)appender.writerFactory;
        assertEquals("factory saw same log-group name",  appender.getActualLogGroup(),  writerFactory.lastLogGroupName);
        assertEquals("factory saw same log-stream name", appender.getActualLogStream(), writerFactory.lastLogStreamName);
    }


    @Test
    public void testExplicitRoll() throws Exception
    {
        CloudwatchAppender appender = initialize("TestCloudwatchAppender.testExplicitRoll.properties");
        MockWriterFactory writerFactory = (MockWriterFactory)appender.writerFactory;

        Logger myLogger = Logger.getLogger(getClass());

        myLogger.debug("first message");

        MockCloudwatchWriter writer0 = (MockCloudwatchWriter)appender.writer;

        assertEquals("pre-roll, writer factory calls",      1,          writerFactory.invocationCount);
        assertEquals("pre-roll, logstream name",            "bargle-0", writerFactory.lastLogStreamName);
        assertEquals("pre-roll, messages in queue",         1,          appender.messageQueue.size());

        appender.roll();

        assertEquals("post-roll, writer factory calls",     2,          writerFactory.invocationCount);
        assertEquals("post-roll, logstream name",           "bargle-1", writerFactory.lastLogStreamName);
        assertEquals("post-roll, messages in queue",        0,          appender.messageQueue.size());
        assertEquals("post-roll, messages passed to writer", 1,         writer0.messages.size());
        assertNotSame("post-roll, writer has been replaced", writer0,   appender.writer);
    }


    @Test
    public void testTimedRoll() throws Exception
    {
        CloudwatchAppender appender = initialize("TestCloudwatchAppender.testTimedRoll.properties");
        MockWriterFactory writerFactory = (MockWriterFactory)appender.writerFactory;

        Logger myLogger = Logger.getLogger(getClass());

        myLogger.debug("first message");

        MockCloudwatchWriter writer0 = (MockCloudwatchWriter)appender.writer;

        assertEquals("pre-roll, logstream name",            "bargle-0", writerFactory.lastLogStreamName);
        assertEquals("pre-roll, messages in queue",         1,          appender.messageQueue.size());

        appender.lastRollTimestamp -= 20000;

        myLogger.debug("second message");

        assertEquals("post-autoroll, logstream name",               "bargle-1", writerFactory.lastLogStreamName);
        assertEquals("post-autoroll, messages in queue",            0,          appender.messageQueue.size());
        assertEquals("post-autoroll, messages passed to writer",    2,          writer0.messages.size());
        assertNotSame("post-autoroll, writer has been replaced",    writer0,    appender.writer);
        assertEquals("post-autoroll, no messages for new writer",   0,          ((MockCloudwatchWriter)appender.writer).messages.size());
    }


    @Test
    public void testInvalidTimedRollConfiguration() throws Exception
    {
        // note: this will generate a log message that we can't validate
        CloudwatchAppender appender = initialize("TestCloudwatchAppender.testInvalidTimedRoll.properties");
        assertEquals("roll mode",       "none",             appender.getRollMode());
    }
}
