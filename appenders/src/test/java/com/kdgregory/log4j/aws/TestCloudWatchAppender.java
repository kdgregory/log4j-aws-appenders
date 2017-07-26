// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.net.URL;
import java.util.concurrent.CountDownLatch;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.LogLog;

import net.sf.kdgcommons.test.StringAsserts;

import com.kdgregory.log4j.aws.CloudWatchAppender;
import com.kdgregory.log4j.aws.internal.shared.DefaultThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;
import com.kdgregory.testhelpers.log4j.HeaderFooterLayout;
import com.kdgregory.testhelpers.log4j.NullThreadFactory;
import com.kdgregory.testhelpers.log4j.aws.cloudwatch.MockCloudWatchWriter;
import com.kdgregory.testhelpers.log4j.aws.cloudwatch.MockWriterFactory;


public class TestCloudWatchAppender
{

//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

    private Logger logger;
    private CloudWatchAppender appender;


    private void initialize(String propsName)
    throws Exception
    {
        URL config = ClassLoader.getSystemResource(propsName);
        PropertyConfigurator.configure(config);

        logger = Logger.getLogger(getClass());

        Logger rootLogger = Logger.getRootLogger();
        appender = (CloudWatchAppender)rootLogger.getAppender("default");

        appender.writerFactory = new MockWriterFactory(appender);
        appender.threadFactory = new NullThreadFactory();
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
        initialize("TestCloudWatchAppender.testConfiguration.properties");

        assertEquals("log group name",      "argle",            appender.getLogGroup());
        assertEquals("log stream name",     "bargle",           appender.getLogStream());
        assertEquals("max delay",           1234L,              appender.getBatchDelay());
        assertEquals("sequence",            2,                  appender.getSequence());
        assertEquals("rotation mode",       "interval",         appender.getRotationMode());
        assertEquals("rotation interval",   86400000L,          appender.getRotationInterval());
    }


    @Test
    public void testDefaultConfiguration() throws Exception
    {
        initialize("TestCloudWatchAppender.testDefaultConfiguration.properties");

        // note: this is allowed at time of configuration, would disable logger if we try to append
        assertNull("log group name",    appender.getLogGroup());

        assertEquals("log stream name",     "{startTimestamp}", appender.getLogStream());
        assertEquals("max delay",           2000L,              appender.getBatchDelay());
        assertEquals("sequence",            0,                  appender.getSequence());
        assertEquals("rotation mode",       "none",             appender.getRotationMode());
        assertEquals("rotation interval",   -1,                 appender.getRotationInterval());
    }


    @Test
    public void testAppend() throws Exception
    {
        initialize("TestCloudWatchAppender.testAppend.properties");
        MockWriterFactory writerFactory = (MockWriterFactory)appender.writerFactory;

        long initialTimestamp = System.currentTimeMillis();

        assertNull("before messages, writer is null",               appender.writer);

        logger.debug("first message");

        assertNotNull("after message 1, writer is initialized",         appender.writer);
        assertEquals("after message 1, calls to writer factory",        1,          writerFactory.invocationCount);
        assertEquals("actual log-group name",                           "argle",    writerFactory.lastLogGroupName);
        assertEquals("actual log-stream name",                          "bargle",   writerFactory.lastLogStreamName);

        MockCloudWatchWriter mock = (MockCloudWatchWriter)appender.writer;

        assertEquals("after message 1, number of messages in writer",   1,          mock.messages.size());

        // throw in a sleep so that we can discern timestamps
        Thread.sleep(100);

        logger.error("test with exception", new Exception("this is a test"));

        assertEquals("after message 2, calls to writer factory",        1,          writerFactory.invocationCount);
        assertEquals("after message 1, number of messages in writer",   2,          mock.messages.size());

        long finalTimestamp = System.currentTimeMillis();

        LogMessage message1 = mock.messages.get(0);
        assertTrue("message 1 timestamp >= initial timestamp", message1.getTimestamp() >= initialTimestamp);
        assertTrue("message 1 timestamp <= batch timestamp",   message1.getTimestamp() <= finalTimestamp);
        StringAsserts.assertRegex(
                "message 1 generally follows layout: " + message1.getMessage(),
                "20[12][0-9]-.* DEBUG .*TestCloudWatchAppender .*first message.*",
                message1.getMessage().trim());

        LogMessage message2 = mock.messages.get(1);
        assertTrue("message 2 includes exception",
                   message2.getMessage().indexOf("java.lang.Exception") > 0);
        assertTrue("message 2 includes exception",
                   message2.getMessage().indexOf("this is a test") > 0);

        // since we have the writer, we can verify that setting the batch delay gets propagated

        appender.setBatchDelay(1234567);
        assertEquals("writer batch delay propagated", 1234567, mock.batchDelay);
    }


    @Test(expected=IllegalStateException.class)
    public void testThrowsIfAppenderClosed() throws Exception
    {
        initialize("TestCloudWatchAppender.testAppend.properties");

        // write the first message to initialize the appender
        logger.debug("should not throw");

        appender.close();

        // second message should throw
        logger.error("blah blah blah");
    }


    @Test
    public void testWriteHeaderAndFooter() throws Exception
    {
        initialize("TestCloudWatchAppender.testWriteHeaderAndFooter.properties");

        logger.debug("blah blah blah");

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
        System.setProperty("TestCloudWatchAppender.testSubstitution", "foo/bar");

        initialize("TestCloudWatchAppender.testSubstitution.properties");
        assertNull("actual log group after construction", appender.getActualLogGroup());
        assertNull("actual log stream after construction", appender.getActualLogStream());

        // need to trigger append to apply substitutions
        logger.debug("doesn't matter what's written");

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
        initialize("TestCloudWatchAppender.testExplicitRotation.properties");
        MockWriterFactory writerFactory = (MockWriterFactory)appender.writerFactory;

        logger.debug("first message");

        MockCloudWatchWriter writer0 = (MockCloudWatchWriter)appender.writer;

        assertEquals("pre-rotate, writer factory calls",        1,          writerFactory.invocationCount);
        assertEquals("pre-rotate, logstream name",              "bargle-0", writerFactory.lastLogStreamName);

        appender.rotate();

        assertEquals("post-rotate, writer factory calls",       2,          writerFactory.invocationCount);
        assertEquals("post-rotate, logstream name",             "bargle-1", writerFactory.lastLogStreamName);
        assertNotSame("post-rotate, writer has been replaced",  writer0,   appender.writer);
    }


    @Test
    public void testCountedRotation() throws Exception
    {
        initialize("TestCloudWatchAppender.testCountedRotation.properties");
        MockWriterFactory writerFactory = (MockWriterFactory)appender.writerFactory;

        logger.debug("message 1");

        // writer gets created on first append; we want to hold onto it
        MockCloudWatchWriter writer0 = (MockCloudWatchWriter)appender.writer;

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writerFactory.lastLogStreamName);

        // these messages should trigger rotation
        logger.debug("message 2");
        logger.debug("message 3");
        logger.debug("message 4");

        assertEquals("post-rotate, logstream name",                 "bargle-1", writerFactory.lastLogStreamName);
        assertEquals("post-rotate, messages passed to old writer",  3,          writer0.messages.size());
        assertNotSame("post-rotate, writer has been replaced",      writer0,    appender.writer);
        assertEquals("post-rotate, messages passed to new writer",  1,          ((MockCloudWatchWriter)appender.writer).messages.size());
    }


    @Test
    public void testTimedRotation() throws Exception
    {
        initialize("TestCloudWatchAppender.testTimedRotation.properties");
        MockWriterFactory writerFactory = (MockWriterFactory)appender.writerFactory;

        logger.debug("first message");

        MockCloudWatchWriter writer0 = (MockCloudWatchWriter)appender.writer;

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writerFactory.lastLogStreamName);

        appender.lastRotationTimestamp -= 20000;

        logger.debug("second message");

        assertEquals("post-rotate, logstream name",                 "bargle-1", writerFactory.lastLogStreamName);
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertNotSame("post-rotate, writer has been replaced",      writer0,    appender.writer);
        assertEquals("post-rotate, messages passed to new writer",  1,          ((MockCloudWatchWriter)appender.writer).messages.size());
    }


    @Test
    public void testInvalidTimedRotationConfiguration() throws Exception
    {
        // note: this will generate a log message that we can't validate and don't want to see
        initialize("TestCloudWatchAppender.testInvalidTimedRotation.properties");
        assertEquals("rotation mode", "none", appender.getRotationMode());
    }


    @Test
    public void testHourlyRotation() throws Exception
    {
        initialize("TestCloudWatchAppender.testHourlyRotation.properties");
        MockWriterFactory writerFactory = (MockWriterFactory)appender.writerFactory;

        logger.debug("first message");

        MockCloudWatchWriter writer0 = (MockCloudWatchWriter)appender.writer;

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writerFactory.lastLogStreamName);

        appender.lastRotationTimestamp -= 3600000;

        logger.debug("second message");

        assertEquals("post-rotate, logstream name",                 "bargle-1", writerFactory.lastLogStreamName);
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertNotSame("post-rotate, writer has been replaced",      writer0,    appender.writer);
        assertEquals("post-rotate, messages passed to new writer",  1,          ((MockCloudWatchWriter)appender.writer).messages.size());
    }


    @Test
    public void testDailyRotation() throws Exception
    {
        initialize("TestCloudWatchAppender.testDailyRotation.properties");
        MockWriterFactory writerFactory = (MockWriterFactory)appender.writerFactory;

        logger.debug("first message");

        MockCloudWatchWriter writer0 = (MockCloudWatchWriter)appender.writer;

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writerFactory.lastLogStreamName);

        appender.lastRotationTimestamp -= 86400000;

        logger.debug("second message");

        assertEquals("post-rotate, logstream name",                 "bargle-1", writerFactory.lastLogStreamName);
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertNotSame("post-rotate, writer has been replaced",      writer0,    appender.writer);
        assertEquals("post-rotate, messages passed to new writer",  1,          ((MockCloudWatchWriter)appender.writer).messages.size());
    }


    @Test
    public void testWriterErrorHandling() throws Exception
    {
        initialize("TestCloudWatchAppender.testWriterErrorHandling.properties");

        // note that we will be running the writer on a separate thread

        appender.threadFactory = new DefaultThreadFactory();
        appender.writerFactory = new WriterFactory()
        {
            @Override
            public LogWriter newLogWriter()
            {
                return new LogWriter()
                {
                    private CountDownLatch appendLatch = new CountDownLatch(2);

                    @Override
                    public void run()
                    {
                        try
                        {
                            appendLatch.await();
                            throw new IllegalStateException("danger, danger Will Robinson!");
                        }
                        catch (InterruptedException ignored)
                        { /* nothing to do */ }
                    }

                    @Override
                    public void stop()
                    {
                        // not used
                    }

                    @Override
                    public void setBatchDelay(long value)
                    {
                        // not used
                    }

                    @Override
                    public void addMessage(LogMessage message)
                    {
                        appendLatch.countDown();
                    }
                };
            }
        };

        logger.debug("this should trigger writer creation");
        assertNotNull("writer has been initialized", appender.writer);
        assertNull("writer has not yet thrown",      appender.lastWriterException);

        logger.debug("this should trigger writer throwage");

        // without getting really clever, the best way to wait for the throw to be reported is to sit and spin
        for (int ii = 0 ; (ii < 10) && (appender.lastWriterException == null) ; ii++)
        {
            Thread.sleep(10);
        }

        assertNull("writer has been reset",         appender.writer);
        assertEquals("last writer exception class", IllegalStateException.class, appender.lastWriterException.getClass());
    }

}
