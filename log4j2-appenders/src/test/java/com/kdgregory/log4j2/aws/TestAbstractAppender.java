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

package com.kdgregory.log4j2.aws;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import static org.junit.Assert.*;

import static net.sf.kdgcommons.test.NumericAsserts.*;
import static net.sf.kdgcommons.test.StringAsserts.*;

import org.apache.logging.log4j.core.LoggerContext;

import net.sf.kdgcommons.lang.StringUtil;

import com.kdgregory.log4j2.testhelpers.TestableCloudWatchAppender;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.common.util.InternalLogger;
import com.kdgregory.logging.testhelpers.TestingException;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchWriter;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchWriterFactory;


public class TestAbstractAppender
extends AbstractUnitTest<TestableCloudWatchAppender>
{
    public TestAbstractAppender()
    {
        super("TestAbstractAppender/", "CLOUDWATCH");
    }


    @Test
    public void testLifecycle() throws Exception
    {
        initialize("testLifecycle");

        MockCloudWatchWriterFactory writerFactory = appender.getWriterFactory();
        MockCloudWatchWriter writer = appender.getMockWriter();

        assertEquals("post-initialization: calls to writer factory",                1,              writerFactory.invocationCount);
        assertNotNull("post-initialization: writer created",                                        writer);
        assertNotNull("post-initialization: writer running on background thread",                   writer.writerThread);
        assertEquals("post-initialization: actual log-group name",                  "argle",        writer.config.logGroupName);
        assertRegex("post-initialization: actual log-stream name",                  "20\\d{12}",    writer.config.logStreamName);

        long initialTimestamp = System.currentTimeMillis();
        logger.debug("first message");

        assertEquals("after message 1, number of messages in writer",   1,          writer.messages.size());

        // throw in a sleep so that we can discern timestamps
        Thread.sleep(50);

        logger.error("test with exception", new Exception("this is a test"));

        assertEquals("after message 2, calls to writer factory",        1,          writerFactory.invocationCount);
        assertEquals("after message 2, number of messages in writer",   2,          writer.messages.size());

        long finalTimestamp = System.currentTimeMillis();

        LogMessage message1 = writer.messages.get(0);
        assertTrue("message 1 timestamp >= initial timestamp", message1.getTimestamp() >= initialTimestamp);
        assertTrue("message 1 timestamp <= batch timestamp",   message1.getTimestamp() <= finalTimestamp);

        assertRegex(
                "message 1 follows layout: " + message1.getMessage(),
                "20[12][0-9] TestAbstractAppender first message",
                message1.getMessage());

        LogMessage message2 = writer.messages.get(1);
        assertTrue("message 2 includes exception",
                   message2.getMessage().indexOf("java.lang.Exception") > 0);
        assertTrue("message 2 includes exception",
                   message2.getMessage().indexOf("this is a test") > 0);

        // finish off the life-cycle

        assertTrue("appender not closed before shutdown", appender.isStarted());
        assertFalse("writer still running before shutdown", writer.stopped);

        LoggerContext context = LoggerContext.getContext();
        context.stop();

        assertFalse("appender closed after shutdown", appender.isStarted());
        assertTrue("writer stopped after shutdown", writer.stopped);
    }


    @Test
    public void testSynchronousMode() throws Exception
    {
        initialize("testSynchronousMode");

        long start = System.currentTimeMillis();

        MockCloudWatchWriterFactory writerFactory = appender.getWriterFactory();
        MockCloudWatchWriter writer = appender.getMockWriter();

        assertEquals("calls to writer factory",                 1,                                      writerFactory.invocationCount);
        assertNotNull("writer was created",                                                             writer);
        assertNull("writer not started on thread",                                                      writer.writerThread);
        assertEquals("initialize() called",                     1,                                      writer.initializeInvocationCount);

        logger.debug("message one");

        assertEquals("batch has been processed",                1,                                      writer.processBatchInvocationCount);
        assertInRange("batch processing time",                  start, System.currentTimeMillis(),      writer.processBatchLastTimeout);

        assertEquals("before stop, calls to cleanup()",         0,                                      writer.cleanupInvocationCount);

        appender.stop(0, TimeUnit.MILLISECONDS);

        assertEquals("after stop, calls to cleanup()",          1,                                      writer.cleanupInvocationCount);
    }


    @Test
    public void testAppendAfterStop() throws Exception
    {
        initialize("testLifecycle");

        MockCloudWatchWriter writer = appender.getMockWriter();

        appender.stop(0, TimeUnit.MILLISECONDS);

        logger.error("blah blah blah");

        // if the framework didn't stop the appender, it will still try to write to it;
        // I just want to verify that the appender doesn't try to do anything

        assertEquals("nothing was written", 0, writer.messages.size());

        appenderInternalLogger.assertWarningLog("append called before appender was started");
    }


    @Test
    public void testWriteHeaderAndFooter() throws Exception
    {
        // for Log4J2 this test also verifies layout character set handling

        initialize("testWriteHeaderAndFooter");

        MockCloudWatchWriter mockWriter = appender.getMockWriter();

        logger.debug("blah blah blah");

        appender.stop(0, TimeUnit.MILLISECONDS);

        assertEquals("number of messages",  3,                      mockWriter.messages.size());
        assertEquals("header is first",     "File Header\u00a9",    mockWriter.getMessage(0));
        assertEquals("message is middle",   "blah blah blah",       mockWriter.getMessage(1));
        assertEquals("footer is last",      "File Footer\u00a9",    mockWriter.getMessage(2));
    }


    @Test
    public void testExplicitRotation() throws Exception
    {
        initialize("testExplicitRotation");

        MockCloudWatchWriterFactory writerFactory = appender.getWriterFactory();

        logger.debug("first message");

        MockCloudWatchWriter writer0 = appender.getMockWriter();

        assertEquals("pre-rotate, writer factory calls",            1,          writerFactory.invocationCount);
        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.config.logStreamName);

        appender.rotate();

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertEquals("post-rotate, writer factory calls",           2,          writerFactory.invocationCount);
        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.config.logStreamName);

        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  0,          writer1.messages.size());

        // explicit rotation does not cause an internal log entry
        appenderInternalLogger.assertDebugLog();
    }


    @Test
    public void testCountedRotation() throws Exception
    {
        initialize("testCountedRotation");

        MockCloudWatchWriter writer0 = appender.getMockWriter();

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.config.logStreamName);

        // these messages should trigger rotation

        logger.debug("message 1");
        logger.debug("message 2");
        logger.debug("message 3");
        logger.debug("message 4");

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.config.logStreamName);
        assertEquals("post-rotate, messages passed to old writer",  3,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  1,          writer1.messages.size());

        // implicit rotation is logged internally
        appenderInternalLogger.assertDebugLog("rotating.*");
    }


    @Test
    public void testIntervalRotation() throws Exception
    {
        initialize("testIntervalRotation");

        MockCloudWatchWriter writer0 = appender.getMockWriter();

        logger.debug("first message");

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.config.logStreamName);

        appender.updateLastRotationTimestamp(-20000);

        logger.debug("second message");

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.config.logStreamName);
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  1,          writer1.messages.size());

        // implicit rotation is logged internally
        appenderInternalLogger.assertDebugLog("rotating.*");
    }


    @Test
    public void testHourlyRotation() throws Exception
    {
        initialize("testHourlyRotation");

        MockCloudWatchWriter writer0 = appender.getMockWriter();

        logger.debug("first message");

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.config.logStreamName);

        appender.updateLastRotationTimestamp(-3600000);

        logger.debug("second message");

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.config.logStreamName);
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  1,          writer1.messages.size());

        // implicit rotation is logged internally
        appenderInternalLogger.assertDebugLog("rotating.*");
    }


    @Test
    public void testDailyRotation() throws Exception
    {
        initialize("testDailyRotation");

        MockCloudWatchWriter writer0 = appender.getMockWriter();

        logger.debug("first message");

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.config.logStreamName);

        appender.updateLastRotationTimestamp(-86400000);

        logger.debug("second message");

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.config.logStreamName);
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  1,          writer1.messages.size());

        // implicit rotation is logged internally
        appenderInternalLogger.assertDebugLog("rotating.*");
    }


    @Test
    public void testInvalidRotationMode() throws Exception
    {
        initialize("testInvalidRotationMode");

        assertEquals("rotation mode", "none", appender.getRotationMode());
        appenderInternalLogger.assertErrorLog("invalid rotation mode.*bogus.*");
    }


    @Test
    public void testReconfigureRotation() throws Exception
    {
        // Log4J2 does not allow reconfigure of a running appender (it creates a new one)
        // this test remains as documentation
    }


    @Test
    public void testMaximumMessageSize() throws Exception
    {
        final int cloudwatchMaximumBatchSize    = 1048576;  // copied from http://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_PutLogEvents.html
        final int cloudwatchOverhead            = 26;       // ditto
        final int layoutOverhead                = 1;        // newline after message

        final int maxMessageSize                = cloudwatchMaximumBatchSize - (cloudwatchOverhead + layoutOverhead);
        final String bigMessage                 = StringUtil.repeat('A', maxMessageSize);
        final String biggerMessage              = bigMessage + "1";

        initialize("testMaximumMessageSize");

        logger.debug(biggerMessage);
        logger.debug(bigMessage);

        MockCloudWatchWriter writer = appender.getMockWriter();

        assertEquals("number of messages",  1,                  writer.messages.size());
        assertEquals("successful message",  bigMessage,         writer.getMessage(0));
    }


    @Test
    public void testExceptionInLayout() throws Exception
    {
        initialize("testExceptionInLayout");

        MockCloudWatchWriter writer = appender.getMockWriter();

        logger.debug("this should trigger throwage");

        assertEquals("no messages sent to writer", 0, writer.messages.size());

        appenderInternalLogger.assertDebugLog();
        appenderInternalLogger.assertErrorLog("unable to apply layout");
        appenderInternalLogger.assertErrorThrowables(TestingException.class);
    }


    @Test
    public void testUncaughtExceptionHandling() throws Exception
    {
        initialize("testUncaughtExceptionHandling");

        CloudWatchWriterStatistics appenderStats = appender.getAppenderStatistics();
        assertNull("writer has not yet thrown", appenderStats.getLastError());

        logger.debug("first message should be processed");
        logger.debug("this should trigger writer throwage");

        // spin-wait for error to appear
        for (int ii = 0 ; (ii < 10) && (appenderStats.getLastError() == null) ; ii++)
        {
            Thread.sleep(10);
        }

        assertNull("writer has been reset",         appender.getWriter());
        assertEquals("last writer exception class", TestingException.class, appenderStats.getLastError().getClass());

        appenderInternalLogger.assertDebugLog();
        appenderInternalLogger.assertErrorLog("unhandled exception in writer");
        appenderInternalLogger.assertErrorThrowables(TestingException.class);
    }


    @Test
    public void testReconfigureDiscardProperties() throws Exception
    {
        // Log4J2 does not allow reconfigure of a running appender (it creates a new one)
        // this test remains as documentation
    }


    @Test
    public void testInvalidDiscardAction() throws Exception
    {
        initialize("testInvalidDiscardAction");

        MockCloudWatchWriter writer = appender.getMockWriter();

        assertEquals("discard action, from appender",    DiscardAction.oldest,  appender.getDiscardAction());
        assertEquals("discard action, from writer",      DiscardAction.oldest,  writer.config.discardAction);

        appenderInternalLogger.assertDebugLog();
        appenderInternalLogger.assertErrorLog("invalid discard action.*bogus.*");
    }


    @Test
    public void testManyThreads() throws Exception
    {
        final int numThreads = 100;
        final int messagesPerThread = 1000;

        initialize("testLifecycle");

        runLoggingThreads(numThreads, messagesPerThread);

        MockCloudWatchWriterFactory writerFactory = appender.getWriterFactory();
        MockCloudWatchWriter writer = appender.getMockWriter();

        assertEquals("writer factory invocations",  1,                              writerFactory.invocationCount);
        assertEquals("total messages written",      numThreads * messagesPerThread, writer.messages.size());
    }


    @Test
    public void testManyThreadsWithRotation() throws Exception
    {
        final int numThreads = 100;
        final int messagesPerThread = 1000;
        final int expectedTotalMessages = numThreads * messagesPerThread;
        final int rotationInterval = 3000;  // from config

        initialize("testManyThreadsWithRotation");

        // we need to capture new writers as they're created because we can't find them later

        final ConcurrentLinkedQueue<MockCloudWatchWriter> writers = new ConcurrentLinkedQueue<MockCloudWatchWriter>();
        appender.setWriterFactory(new MockCloudWatchWriterFactory()
        {
            @Override
            public LogWriter newLogWriter(CloudWatchWriterConfig config, CloudWatchWriterStatistics stats, InternalLogger ignored)
            {
                MockCloudWatchWriter newWriter = (MockCloudWatchWriter)super.newLogWriter(config, stats, ignored);
                writers.add(newWriter);
                return newWriter;
            }
        });

        // with logback, the first writer was created during initialization, so we need to capture it or our counts will be wrong

        writers.add(appender.getMockWriter());

        runLoggingThreads(numThreads, messagesPerThread);

        assertEquals("calls to append()", expectedTotalMessages, appender.getAppendInvocationCount());
        appenderInternalLogger.assertErrorLog();

        // note that we didn't count the initial writer invocation

        assertEquals("writer factory invocations", expectedTotalMessages / rotationInterval, appender.getWriterFactory().invocationCount);

        int actualTotalMessages = 0;
        for (MockCloudWatchWriter writer : writers)
        {
            actualTotalMessages += writer.messages.size();
        }

        assertEquals("total messages written", expectedTotalMessages, actualTotalMessages);
    }
}
