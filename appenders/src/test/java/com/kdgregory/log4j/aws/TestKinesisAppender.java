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

import com.kdgregory.log4j.aws.internal.shared.DefaultThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;
import com.kdgregory.testhelpers.log4j.HeaderFooterLayout;
import com.kdgregory.testhelpers.log4j.NullThreadFactory;
import com.kdgregory.testhelpers.log4j.aws.kinesis.MockKinesisWriter;
import com.kdgregory.testhelpers.log4j.aws.kinesis.MockWriterFactory;
import com.kdgregory.testhelpers.log4j.aws.kinesis.TestableKinesisAppender;


public class TestKinesisAppender
{

//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

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
        appender.setWriterFactory(new MockWriterFactory(appender));
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

        assertEquals("stream name",         "argle",            appender.getStreamName());
        assertEquals("partition key",       "bargle",           appender.getPartitionKey());
        assertEquals("max delay",           1234L,              appender.getBatchDelay());
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
        MockWriterFactory writerFactory = appender.getMockWriterFactory();

        long initialTimestamp = System.currentTimeMillis();

        // this sleep is to make timestamps discernable
        Thread.sleep(100);

        assertNull("before messages, writer is null",                           appender.getWriter());

        logger.debug("first message");

        assertNotNull("after message 1, writer is initialized",                 appender.getWriter());
        assertEquals("after message 1, calls to writer factory",                1,              writerFactory.invocationCount);

        StringAsserts.assertRegex("stream name, with substitutions",            "argle-\\d+",   writerFactory.lastStreamName);
        StringAsserts.assertRegex("default partition key, after substitutions", "20\\d{12}",    writerFactory.lastPartitionKey);

        MockKinesisWriter mockWriter = appender.getMockWriter();

        assertEquals("after message 1, number of messages in writer",   1,          mockWriter.messages.size());

        logger.error("test with exception", new Exception("this is a test"));

        assertEquals("after message 2, calls to writer factory",        1,          writerFactory.invocationCount);
        assertEquals("after message 2, number of messages in writer",   2,          mockWriter.messages.size());

        long finalTimestamp = System.currentTimeMillis();

        LogMessage message1 = mockWriter.messages.get(0);
        assertTrue("message 1 timestamp >= initial timestamp", message1.getTimestamp() >= initialTimestamp);
        assertTrue("message 1 timestamp <= batch timestamp",   message1.getTimestamp() <= finalTimestamp);
        StringAsserts.assertRegex(
                "message 1 generally follows layout: " + message1.getMessage(),
                "20[12][0-9]-.* DEBUG .*TestKinesisAppender .*first message.*",
                message1.getMessage().trim());

        LogMessage message2 = mockWriter.messages.get(1);
        assertTrue("message 2 includes exception",
                   message2.getMessage().indexOf("java.lang.Exception") > 0);
        assertTrue("message 2 includes exception",
                   message2.getMessage().indexOf("this is a test") > 0);

        // since we have the writer, we can verify that setting the batch delay gets propagated

        appender.setBatchDelay(1234567);
        assertEquals("writer batch delay propagated", 1234567, mockWriter.batchDelay);
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
        MockKinesisWriter mockWriter = appender.getMockWriter();
        LogManager.shutdown();

        assertEquals("number of messages written to log", 3, mockWriter.messages.size());
        assertEquals("header is first", HeaderFooterLayout.HEADER, mockWriter.getMessage(0));
        assertEquals("footer is last",  HeaderFooterLayout.FOOTER, mockWriter.getMessage(2));
    }


    @Test
    public void testWriterErrorHandling() throws Exception
    {
        initialize("TestKinesisAppender.testWriterErrorHandling.properties");

        // note that we will be running the writer on a separate thread

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(new WriterFactory()
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
        });

        logger.debug("this should trigger writer creation");
        assertNotNull("writer has been initialized",    appender.getWriter());
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
