// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Semaphore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.LogLog;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.*;
import com.amazonaws.util.BinaryUtils;

import com.kdgregory.log4j.aws.internal.kinesis.KinesisLogWriter;
import com.kdgregory.log4j.aws.internal.kinesis.KinesisWriterConfig;
import com.kdgregory.log4j.aws.internal.shared.DefaultThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;
import com.kdgregory.testhelpers.log4j.*;
import com.kdgregory.testhelpers.log4j.aws.ThrowingWriterFactory;
import com.kdgregory.testhelpers.log4j.aws.kinesis.*;

import static org.junit.Assert.*;
import static net.sf.kdgcommons.test.StringAsserts.*;


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
    public void testUncaughtExceptionHandling() throws Exception
    {
        initialize("TestKinesisAppender.testUncaughtExceptionHandling.properties");

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


    @Test
    public void testMessageErrorHandling() throws Exception
    {
        // WARNING: this test may break if the internal implementation changes

        initialize("TestKinesisAppender.testMessageErrorHandling.properties");

        // we will be running the writer on a separate thread, so will require
        // some form of wait to verify the writer's activity
        appender.setThreadFactory(new DefaultThreadFactory());

        // these semaphotes coordinate the calls to PutRecords with the assertions
        // that we make in the main thread; note that both start unacquired
        final Semaphore allowPutRecords = new Semaphore(0);
        final Semaphore allowMainThread = new Semaphore(0);

        // the lists of records that succeeded/failed in last call to putRecords
        final List<PutRecordsRequestEntry> successRecords = new ArrayList<PutRecordsRequestEntry>();
        final List<PutRecordsRequestEntry> failRecords = new ArrayList<PutRecordsRequestEntry>();

        // our mock for the Kinesis client; when sending, it will reject 1/3 of the records
        final InvocationHandler invocationHandler = new InvocationHandler()
        {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
            {
                if (method.getName().equals("describeStream"))
                {
                    return new DescribeStreamResult()
                           .withStreamDescription(
                               new StreamDescription()
                               .withStreamStatus(StreamStatus.ACTIVE));
                }
                else if (method.getName().equals("putRecords"))
                {
                    allowPutRecords.acquire();
                    PutRecordsRequest request = (PutRecordsRequest)args[0];
                    List<PutRecordsResultEntry> resultRecords = new ArrayList<PutRecordsResultEntry>();
                    int ii = 0;
                    for (PutRecordsRequestEntry entry : request.getRecords())
                    {
                        PutRecordsResultEntry recordResult = new PutRecordsResultEntry();
                        if ((ii % 3) == 1)
                        {
                            failRecords.add(entry);
                            recordResult.setErrorCode("anything, really");
                        }
                        else
                        {
                            successRecords.add(entry);
                        }

                        resultRecords.add(recordResult);
                        ii++;
                    }

                    allowMainThread.release();
                    return new PutRecordsResult().withRecords(resultRecords);
                }

                System.err.println("invocation handler called unexpectedly: " + method.getName());
                return null;
            }
        };

        appender.setWriterFactory(new WriterFactory<KinesisWriterConfig>()
        {
            @Override
            public LogWriter newLogWriter(KinesisWriterConfig config)
            {
                return new KinesisLogWriter(config)
                {
                    @Override
                    protected AmazonKinesis createClient()
                    {
                        return (AmazonKinesis)Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] { AmazonKinesis.class },
                                    invocationHandler);
                    }
                };
            }
        });

        for (int ii = 0 ; ii < 10 ; ii++)
        {
            logger.debug("message " + ii);
        }

        allowPutRecords.release();
        allowMainThread.acquire();

        assertEquals("first batch, number of successful messages", 7, successRecords.size());
        assertEquals("first batch, number of failed messages",     3, failRecords.size());

        PutRecordsRequestEntry savedFailure1 = failRecords.get(0);
        PutRecordsRequestEntry savedFailure2 = failRecords.get(1);
        PutRecordsRequestEntry savedFailure3 = failRecords.get(2);

        successRecords.clear();
        failRecords.clear();

        allowPutRecords.release();
        allowMainThread.acquire();

        assertEquals("second batch, number of successful messages", 2, successRecords.size());
        assertEquals("second batch, number of failed messages",     1, failRecords.size());

        assertTrue("first failure is now first success",
                   Arrays.equals(
                       BinaryUtils.copyAllBytesFrom(savedFailure1.getData()),
                       BinaryUtils.copyAllBytesFrom(successRecords.get(0).getData())));
        assertTrue("third failure is now second success (second failure failed again)",
                   Arrays.equals(
                       BinaryUtils.copyAllBytesFrom(savedFailure3.getData()),
                       BinaryUtils.copyAllBytesFrom(successRecords.get(1).getData())));

        successRecords.clear();
        failRecords.clear();

        allowPutRecords.release();
        allowMainThread.acquire();

        assertEquals("third batch, number of successful messages", 1, successRecords.size());
        assertEquals("third batch, number of failed messages",     0, failRecords.size());

        assertTrue("second original failure is now a success",
                   Arrays.equals(
                       BinaryUtils.copyAllBytesFrom(savedFailure2.getData()),
                       BinaryUtils.copyAllBytesFrom(successRecords.get(0).getData())));
    }

}
