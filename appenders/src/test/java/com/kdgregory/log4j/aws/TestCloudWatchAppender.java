// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.net.URL;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import net.sf.kdgcommons.test.StringAsserts;

import com.kdgregory.log4j.aws.CloudWatchAppender;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.NullThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;
import com.kdgregory.testhelpers.log4j.HeaderFooterLayout;
import com.kdgregory.testhelpers.log4j.aws.cloudwatch.MockCloudWatchWriter;


public class TestCloudWatchAppender
{

//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

    private static class MockWriterFactory implements WriterFactory
    {
        public CloudWatchAppender appender;

        public int invocationCount = 0;
        public String lastLogGroupName;
        public String lastLogStreamName;
        public MockCloudWatchWriter writer;

        public MockWriterFactory(CloudWatchAppender appender)
        {
            this.appender = appender;
        }

        @Override
        public LogWriter newLogWriter()
        {
            invocationCount++;
            lastLogGroupName = appender.getActualLogGroup();
            lastLogStreamName = appender.getActualLogStream();
            writer = new MockCloudWatchWriter();
            return writer;
        }
    }


    private CloudWatchAppender initialize(String propsName)
    throws Exception
    {
        URL config = ClassLoader.getSystemResource(propsName);
        PropertyConfigurator.configure(config);

        Logger rootLogger = Logger.getRootLogger();
        CloudWatchAppender appender = (CloudWatchAppender)rootLogger.getAppender("default");

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
        CloudWatchAppender appender = initialize("TestCloudWatchAppender.testConfiguration.properties");

        assertEquals("log group name",      "argle",            appender.getLogGroup());
        assertEquals("log stream name",     "bargle",           appender.getLogStream());
        assertEquals("batch size",          100,                appender.getBatchSize());
        assertEquals("max delay",           1234L,              appender.getMaxDelay());
        assertEquals("sequence",            2,                  appender.getSequence());
        assertEquals("rotation mode",       "interval",         appender.getRotationMode());
        assertEquals("rotation interval",   86400000L,          appender.getRotationInterval());
    }


    @Test
    public void testDefaultConfiguration() throws Exception
    {
        CloudWatchAppender appender = initialize("TestCloudWatchAppender.testDefaultConfiguration.properties");

        // note: this is allowed at time of configuration, would disable logger if we try to append
        assertNull("log group name",    appender.getLogGroup());

        assertEquals("log stream name",     "{startTimestamp}", appender.getLogStream());
        assertEquals("batch size",          16,                 appender.getBatchSize());
        assertEquals("max delay",           4000L,              appender.getMaxDelay());
        assertEquals("sequence",            0,                  appender.getSequence());
        assertEquals("rotation mode",       "none",             appender.getRotationMode());
        assertEquals("rotation interval",   -1,                 appender.getRotationInterval());
    }


    @Test
    public void testAppend() throws Exception
    {
        CloudWatchAppender appender = initialize("TestCloudWatchAppender.testAppend.properties");
        MockWriterFactory writerFactory = (MockWriterFactory)appender.writerFactory;

        assertTrue("before messages, last batch timestamp > 0",    appender.lastBatchTimestamp > 0);
        assertTrue("before messages, last roll timestamp == 0",     appender.lastRotationTimestamp == 0);
        assertNull("before messages, writer is null",               appender.writer);

        Logger myLogger = Logger.getLogger(getClass());
        myLogger.debug("first message");

        assertTrue("after messages, last batch timestamp > 0",     appender.lastBatchTimestamp > 0);
        assertTrue("after messages, last roll timestamp > 0",      appender.lastRotationTimestamp > 0);
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

        MockCloudWatchWriter mock = (MockCloudWatchWriter)appender.writer;
        List<LogMessage> lastBatch = mock.lastBatch;
        assertEquals("messages in batch",  2, mock.lastBatch.size());

        LogMessage message1 = lastBatch.get(0);
        assertTrue("message 1 timestamp >= initial timestamp", message1.getTimestamp() >= initialTimestamp);
        assertTrue("message 1 timestamp <= batch timestamp",   message1.getTimestamp() <= appender.lastBatchTimestamp);
        StringAsserts.assertRegex(
                "message 1 generally follows layout: " + message1.getMessage(),
                "20[12][0-9]-.* DEBUG .*TestCloudWatchAppender .*first message.*",
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
        CloudWatchAppender appender = initialize("TestCloudWatchAppender.testAppend.properties");

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
        CloudWatchAppender appender = initialize("TestCloudWatchAppender.testWriteHeaderAndFooter.properties");

        Logger myLogger = Logger.getLogger(getClass());
        myLogger.debug("blah blah blah");

        // must retrieve writer before we shut down
        MockCloudWatchWriter mock = (MockCloudWatchWriter)appender.writer;
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

        CloudWatchAppender appender = initialize("TestCloudWatchAppender.testSubstitution.properties");
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
    public void testExplicitRotation() throws Exception
    {
        CloudWatchAppender appender = initialize("TestCloudWatchAppender.testExplicitRotation.properties");
        MockWriterFactory writerFactory = (MockWriterFactory)appender.writerFactory;

        Logger myLogger = Logger.getLogger(getClass());

        myLogger.debug("first message");

        MockCloudWatchWriter writer0 = (MockCloudWatchWriter)appender.writer;

        assertEquals("pre-rotate, writer factory calls",        1,          writerFactory.invocationCount);
        assertEquals("pre-rotate, logstream name",              "bargle-0", writerFactory.lastLogStreamName);
        assertEquals("pre-rotate, messages in queue",           1,          appender.messageQueue.size());

        appender.rotate();

        assertEquals("post-rotate, writer factory calls",       2,          writerFactory.invocationCount);
        assertEquals("post-rotate, logstream name",             "bargle-1", writerFactory.lastLogStreamName);
        assertEquals("post-rotate, messages in queue",          0,          appender.messageQueue.size());
        assertEquals("post-rotate, messages passed to writer",  1,         writer0.messages.size());
        assertNotSame("post-rotate, writer has been replaced",  writer0,   appender.writer);
    }


    @Test
    public void testCountedRotation() throws Exception
    {
        CloudWatchAppender appender = initialize("TestCloudWatchAppender.testCountedRotation.properties");
        MockWriterFactory writerFactory = (MockWriterFactory)appender.writerFactory;

        Logger myLogger = Logger.getLogger(getClass());

        myLogger.debug("message 1");

        // writer gets created on first append; we want to hold onto it
        MockCloudWatchWriter writer0 = (MockCloudWatchWriter)appender.writer;

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writerFactory.lastLogStreamName);
        assertEquals("pre-rotate, messages in queue",               1,          appender.messageQueue.size());

        // these messages should trigger rotation
        myLogger.debug("message 2");
        myLogger.debug("message 3");
        myLogger.debug("message 4");

        assertEquals("post-rotate, logstream name",                 "bargle-1", writerFactory.lastLogStreamName);
        assertEquals("post-rotate, messages in queue",              1,          appender.messageQueue.size());
        assertEquals("post-rotate, messages passed to old writer",  3,          writer0.messages.size());
        assertNotSame("post-rotate, writer has been replaced",      writer0,    appender.writer);
        assertEquals("post-rotate, no messages for new writer",     0,          ((MockCloudWatchWriter)appender.writer).messages.size());
    }


    @Test
    public void testTimedRotation() throws Exception
    {
        CloudWatchAppender appender = initialize("TestCloudWatchAppender.testTimedRotation.properties");
        MockWriterFactory writerFactory = (MockWriterFactory)appender.writerFactory;

        Logger myLogger = Logger.getLogger(getClass());

        myLogger.debug("first message");

        MockCloudWatchWriter writer0 = (MockCloudWatchWriter)appender.writer;

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writerFactory.lastLogStreamName);
        assertEquals("pre-rotate, messages in queue",               1,          appender.messageQueue.size());

        appender.lastRotationTimestamp -= 20000;

        myLogger.debug("second message");

        assertEquals("post-rotate, logstream name",                 "bargle-1", writerFactory.lastLogStreamName);
        assertEquals("post-rotate, messages in queue",              1,          appender.messageQueue.size());
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertNotSame("post-rotate, writer has been replaced",      writer0,    appender.writer);
        assertEquals("post-rotate, no messages for new writer",     0,          ((MockCloudWatchWriter)appender.writer).messages.size());
    }


    @Test
    public void testInvalidTimedRotationConfiguration() throws Exception
    {
        // note: this will generate a log message that we can't validate
        CloudWatchAppender appender = initialize("TestCloudWatchAppender.testInvalidTimedRotation.properties");
        assertEquals("rotation mode",       "none",             appender.getRotationMode());
    }


    @Test
    public void testHourlyRotation() throws Exception
    {
        CloudWatchAppender appender = initialize("TestCloudWatchAppender.testHourlyRotation.properties");
        MockWriterFactory writerFactory = (MockWriterFactory)appender.writerFactory;

        Logger myLogger = Logger.getLogger(getClass());

        myLogger.debug("first message");

        MockCloudWatchWriter writer0 = (MockCloudWatchWriter)appender.writer;

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writerFactory.lastLogStreamName);
        assertEquals("pre-rotate, messages in queue",               1,          appender.messageQueue.size());

        appender.lastRotationTimestamp -= 3600000;

        myLogger.debug("second message");

        assertEquals("post-rotate, logstream name",                 "bargle-1", writerFactory.lastLogStreamName);
        assertEquals("post-rotate, messages in queue",              1,          appender.messageQueue.size());
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertNotSame("post-rotate, writer has been replaced",      writer0,    appender.writer);
        assertEquals("post-rotate, no messages for new writer",     0,          ((MockCloudWatchWriter)appender.writer).messages.size());
    }


    @Test
    public void testDailyRotation() throws Exception
    {
        CloudWatchAppender appender = initialize("TestCloudWatchAppender.testDailyRotation.properties");
        MockWriterFactory writerFactory = (MockWriterFactory)appender.writerFactory;

        Logger myLogger = Logger.getLogger(getClass());

        myLogger.debug("first message");

        MockCloudWatchWriter writer0 = (MockCloudWatchWriter)appender.writer;

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writerFactory.lastLogStreamName);
        assertEquals("pre-rotate, messages in queue",               1,          appender.messageQueue.size());

        appender.lastRotationTimestamp -= 86400000;

        myLogger.debug("second message");

        assertEquals("post-rotate, logstream name",                 "bargle-1", writerFactory.lastLogStreamName);
        assertEquals("post-rotate, messages in queue",              1,          appender.messageQueue.size());
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertNotSame("post-rotate, writer has been replaced",      writer0,    appender.writer);
        assertEquals("post-rotate, no messages for new writer",     0,          ((MockCloudWatchWriter)appender.writer).messages.size());
    }
}
