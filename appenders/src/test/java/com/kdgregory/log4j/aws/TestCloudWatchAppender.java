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

import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.log4j.aws.internal.shared.DefaultThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.testhelpers.log4j.HeaderFooterLayout;
import com.kdgregory.testhelpers.log4j.NullThreadFactory;
import com.kdgregory.testhelpers.log4j.aws.ThrowingWriterFactory;
import com.kdgregory.testhelpers.log4j.aws.cloudwatch.MockCloudWatchWriter;
import com.kdgregory.testhelpers.log4j.aws.cloudwatch.MockCloudWatchWriterFactory;
import com.kdgregory.testhelpers.log4j.aws.cloudwatch.TestableCloudWatchAppender;


public class TestCloudWatchAppender
{
    private Logger logger;
    private TestableCloudWatchAppender appender;


    private void initialize(String propsName)
    throws Exception
    {
        URL config = ClassLoader.getSystemResource(propsName);
        PropertyConfigurator.configure(config);

        logger = Logger.getLogger(getClass());

        Logger rootLogger = Logger.getRootLogger();
        appender = (TestableCloudWatchAppender)rootLogger.getAppender("default");

        appender.setThreadFactory(new NullThreadFactory());
        appender.setWriterFactory(new MockCloudWatchWriterFactory(appender));
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

        assertEquals("log group name",      "argle",              appender.getLogGroup());
        assertEquals("log stream name",     "bargle",             appender.getLogStream());
        assertEquals("max delay",           1234L,                appender.getBatchDelay());
        assertEquals("sequence",            2,                    appender.getSequence());
        assertEquals("rotation mode",       "interval",           appender.getRotationMode());
        assertEquals("rotation interval",   86400000L,            appender.getRotationInterval());
    }


    @Test
    public void testDefaultConfiguration() throws Exception
    {
        initialize("TestCloudWatchAppender.testDefaultConfiguration.properties");

        // note: this is allowed at time of configuration, would disable logger if we try to append
        assertNull("log group name",    appender.getLogGroup());

        assertEquals("log stream name",     "{startupTimestamp}", appender.getLogStream());
        assertEquals("max delay",           2000L,                appender.getBatchDelay());
        assertEquals("sequence",            0,                    appender.getSequence());
        assertEquals("rotation mode",       "none",               appender.getRotationMode());
        assertEquals("rotation interval",   -1,                   appender.getRotationInterval());
    }


    @Test
    public void testAppend() throws Exception
    {
        initialize("TestCloudWatchAppender.testAppend.properties");
        MockCloudWatchWriterFactory writerFactory = appender.getWriterFactory();

        long initialTimestamp = System.currentTimeMillis();

        assertNull("before messages, writer is null",                   appender.getWriter());

        logger.debug("first message");

        MockCloudWatchWriter writer = appender.getWriter();

        assertNotNull("after message 1, writer is initialized",         writer);
        assertEquals("after message 1, calls to writer factory",        1,              writerFactory.invocationCount);
        assertEquals("actual log-group name",                           "argle",        writer.logGroup);
        assertRegex("actual log-stream name",                           "20\\d{12}",    writer.logStream);


        assertEquals("after message 1, number of messages in writer",   1,          writer.messages.size());

        // throw in a sleep so that we can discern timestamps
        Thread.sleep(100);

        logger.error("test with exception", new Exception("this is a test"));

        assertEquals("after message 2, calls to writer factory",        1,          writerFactory.invocationCount);
        assertEquals("after message 2, number of messages in writer",   2,          writer.messages.size());

        long finalTimestamp = System.currentTimeMillis();

        LogMessage message1 = writer.messages.get(0);
        assertTrue("message 1 timestamp >= initial timestamp", message1.getTimestamp() >= initialTimestamp);
        assertTrue("message 1 timestamp <= batch timestamp",   message1.getTimestamp() <= finalTimestamp);
        assertRegex(
                "message 1 generally follows layout: " + message1.getMessage(),
                "20[12][0-9]-.* DEBUG .*TestCloudWatchAppender .*first message.*",
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
        MockCloudWatchWriter mockWriter = appender.getWriter();
        LogManager.shutdown();

        assertEquals("number of messages written to log", 3, mockWriter.messages.size());
        assertEquals("header is first", HeaderFooterLayout.HEADER, mockWriter.getMessage(0));
        assertEquals("footer is last",  HeaderFooterLayout.FOOTER, mockWriter.getMessage(2));
    }


    @Test
    public void testSubstitution() throws Exception
    {
        // note that the property value includes invalid characters
        System.setProperty("TestCloudWatchAppender.testSubstitution", "foo/bar");

        initialize("TestCloudWatchAppender.testSubstitution.properties");

        // need to trigger append to apply substitutions
        logger.debug("doesn't matter what's written");

        MockCloudWatchWriter writer = appender.getWriter();

        assertEquals("appender's log group name",   "MyLog-{sysprop:TestCloudWatchAppender.testSubstitution}", appender.getLogGroup());
        assertEquals("appender's log stream name",  "MyStream-{timestamp}-{bogus}",                            appender.getLogStream());

        assertEquals("writers log group name",      "MyLog-foobar",             writer.logGroup);
        assertRegex("writers log stream name",      "MyStream-20\\d{12}-bogus", writer.logStream);
    }


    @Test
    public void testExplicitRotation() throws Exception
    {
        initialize("TestCloudWatchAppender.testExplicitRotation.properties");
        MockCloudWatchWriterFactory writerFactory = appender.getWriterFactory();

        logger.debug("first message");

        MockCloudWatchWriter writer0 = appender.getWriter();

        assertEquals("pre-rotate, writer factory calls",            1,          writerFactory.invocationCount);
        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.logStream);

        appender.rotate();

        MockCloudWatchWriter writer1 = appender.getWriter();

        assertEquals("post-rotate, writer factory calls",           2,          writerFactory.invocationCount);
        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.logStream);
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  0,          writer1.messages.size());
    }


    @Test
    public void testCountedRotation() throws Exception
    {
        initialize("TestCloudWatchAppender.testCountedRotation.properties");

        logger.debug("message 1");

        // writer gets created on first append; we want to hold onto it
        MockCloudWatchWriter writer0 = appender.getWriter();

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.logStream);

        // these messages should trigger rotation
        logger.debug("message 2");
        logger.debug("message 3");
        logger.debug("message 4");

        MockCloudWatchWriter writer1 = appender.getWriter();

        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.logStream);
        assertEquals("post-rotate, messages passed to old writer",  3,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  1,          writer1.messages.size());
    }


    @Test
    public void testTimedRotation() throws Exception
    {
        initialize("TestCloudWatchAppender.testTimedRotation.properties");

        logger.debug("first message");

        MockCloudWatchWriter writer0 = appender.getWriter();

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.logStream);

        appender.updateLastRotationTimestamp(-20000);

        logger.debug("second message");

        MockCloudWatchWriter writer1 = appender.getWriter();

        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.logStream);
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  1,          writer1.messages.size());
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

        logger.debug("first message");

        MockCloudWatchWriter writer0 = appender.getWriter();

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.logStream);

        appender.updateLastRotationTimestamp(-3600000);

        logger.debug("second message");

        MockCloudWatchWriter writer1 = appender.getWriter();

        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.logStream);
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  1,          writer1.messages.size());
    }


    @Test
    public void testDailyRotation() throws Exception
    {
        initialize("TestCloudWatchAppender.testDailyRotation.properties");

        logger.debug("first message");

        MockCloudWatchWriter writer0 = appender.getWriter();

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.logStream);

        appender.updateLastRotationTimestamp(-86400000);

        logger.debug("second message");

        MockCloudWatchWriter writer1 = appender.getWriter();

        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.logStream);
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  1,          writer1.messages.size());
    }


    @Test
    public void testUncaughtExceptionHandling() throws Exception
    {
        initialize("TestCloudWatchAppender.testUncaughtExceptionHandling.properties");

        // note that we will be running the writer on a separate thread

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(new ThrowingWriterFactory<CloudWatchWriterConfig>());

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
