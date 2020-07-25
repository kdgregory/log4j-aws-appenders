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

package com.kdgregory.log4j.aws;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import static net.sf.kdgcommons.test.StringAsserts.*;
import static net.sf.kdgcommons.test.NumericAsserts.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.helpers.LogLog;

import com.kdgregory.log4j.testhelpers.HeaderFooterLayout;
import com.kdgregory.log4j.testhelpers.cloudwatch.TestableCloudWatchAppender;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.factories.DefaultThreadFactory;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.testhelpers.TestingException;
import com.kdgregory.logging.testhelpers.ThrowingWriterFactory;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchWriter;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchWriterFactory;


/**
 *  These tests exercise appender logic that's implemented in AbstractAppender,
 *  using CloudWatchAppender as a concrete implementation class with a mock
 *  log-writer.
 */
public class TestAbstractAppender
extends AbstractUnitTest<TestableCloudWatchAppender>
{
    public TestAbstractAppender()
    {
        super("TestAbstractAppender/", "test");
    }


    @Before
    public void setUp()
    {
        LogManager.resetConfiguration();
        LogLog.setQuietMode(true);
    }


    @After
    public void tearDown()
    {
        LogLog.setQuietMode(false);
    }


    @Test
    public void testLifecycle() throws Exception
    {
        initialize("testLifecycle");

        assertEquals("internal logger configured with name",            appender.getName(), appenderInternalLogger.appenderName);

        long initialTimestamp = System.currentTimeMillis();

        assertNull("before messages, writer is null",                   appender.getWriter());

        logger.debug("first message");

        MockCloudWatchWriterFactory writerFactory = appender.getWriterFactory();
        MockCloudWatchWriter writer = appender.getMockWriter();

        // note: we can't assert initialization and batch processing because the mock doesn't support that
        // but as long as we can verify that the writer thread was running, we can assume writer tests
        // have covered that functionality

        assertEquals("after message 1, calls to writer factory",        1,              writerFactory.invocationCount);
        assertNotNull("after message 1, writer is initialized",                         writer);
        assertNotNull("writer was started on background thread",                        writer.writerThread);
        assertEquals("actual log-group name",                           "argle",        writer.config.logGroupName);
        assertRegex("actual log-stream name",                           "20\\d{12}",    writer.config.logStreamName);

        assertEquals("after message 1, number of messages in writer",   1,              writer.messages.size());

        // throw in a sleep so that we can discern timestamps
        Thread.sleep(50);

        logger.error("test with exception", new Exception("this is a test"));

        assertEquals("after message 2, calls to writer factory",        1,              writerFactory.invocationCount);
        assertEquals("after message 2, number of messages in writer",   2,              writer.messages.size());

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

        // since we have the writer, we can verify that setting the batch delay gets propagated

        appender.setBatchDelay(1234567);
        assertEquals("writer batch delay propagated", 1234567, writer.config.batchDelay);

        // finish off the life-cycle

        assertFalse("appender not closed before shutdown",  appender.isClosed());
        assertFalse("writer still running before shutdown", writer.stopped);

        LogManager.shutdown();

        assertTrue("appender closed after shutdown",        appender.isClosed());
        assertTrue("writer stopped after shutdown",         writer.stopped);
    }


    @Test
    public void testWriteHeaderAndFooter() throws Exception
    {
        initialize("testWriteHeaderAndFooter");

        logger.debug("blah blah blah");

        // must retrieve writer before we shut down
        MockCloudWatchWriter mockWriter = appender.getMockWriter();
        LogManager.shutdown();

        assertEquals("number of messages",  3,                          mockWriter.messages.size());
        assertEquals("header is first",     HeaderFooterLayout.HEADER,  mockWriter.getMessage(0));
        assertEquals("message is middle",   "blah blah blah",           mockWriter.getMessage(1));
        assertEquals("footer is last",      HeaderFooterLayout.FOOTER,  mockWriter.getMessage(2));
    }


    @Test
    public void testSynchronousMode() throws Exception
    {
        initialize("testSynchronousMode");

        long start = System.currentTimeMillis();

        logger.debug("a message to trigger writer creation");

        MockCloudWatchWriterFactory writerFactory = appender.getWriterFactory();
        MockCloudWatchWriter writer = appender.getMockWriter();

        assertEquals("calls to writer factory",                 1,                                      writerFactory.invocationCount);
        assertNotNull("writer was created",                                                             writer);
        assertNull("writer not started on thread",                                                      writer.writerThread);
        assertEquals("initialize() called",                     1,                                      writer.initializeInvocationCount);
        assertEquals("batch has been processed",                1,                                      writer.processBatchInvocationCount);
        assertInRange("batch processing time",                  start, System.currentTimeMillis(),      writer.processBatchLastTimeout);

        assertEquals("before stop, calls to cleanup()",         0,                                      writer.cleanupInvocationCount);

        appender.close();

        assertEquals("after stop, calls to cleanup()",          1,                                      writer.cleanupInvocationCount);
    }


    @Test(expected=IllegalStateException.class)
    public void testAppendAfterStop() throws Exception
    {
        initialize("testLifecycle");

        // write the first message to initialize the appender
        logger.debug("should not throw");

        // we close the appender explicitly because Log4J won't pass on messages
        // after LogManager.shutdown()
        appender.close();

        // second message should throw
        logger.error("blah blah blah");
    }


    @Test
    public void testShutdownHook() throws Exception
    {
        initialize("testShutdownHook");

        // for this test we need a real dispatch thread
        appender.setThreadFactory(new DefaultThreadFactory("test"));

        logger.debug("a message to trigger writer creation");

        // we'll need to spin until the writer thread has been created
        MockCloudWatchWriter writer = appender.getMockWriter();
        for (int ii = 0 ; ii < 30 ; ii++)
        {
            if (writer.writerThread == null)
                Thread.sleep(100);
        }
        assertNotNull("writer thread created", writer.writerThread);

        // the run() method should save the thread and exit immediately; if not the test will hang
        writer.writerThread.join();

        assertNotNull("writer has shutdown hook", writer.shutdownHook);
        assertFalse("writer has not yet been stopped", writer.stopped);
        assertEquals("cleanup has not yet been called", 0, writer.cleanupInvocationCount);

        writer.shutdownHook.start();
        writer.shutdownHook.join();

        assertTrue("writer has been stopped", writer.stopped);

        // a real LogWriter will call cleanup being stopped; we'll assume logwriter tests cover that
    }


    @Test
    public void testExceptionInLayout() throws Exception
    {
        initialize("testExceptionInLayout");

        logger.debug("this should trigger throwage");

        MockCloudWatchWriter writer = appender.getMockWriter();
        assertNotNull("writer was created", writer);

        assertEquals("no messages sent to writer", 0, writer.messages.size());

        appenderInternalLogger.assertDebugLog();
        appenderInternalLogger.assertErrorLog("unable to apply layout");
        appenderInternalLogger.assertErrorThrowables(TestingException.class);
    }


    @Test
    public void testUncaughtExceptionHandling() throws Exception
    {
        initialize("testUncaughtExceptionHandling");

        // note that we will be running the writer on a separate thread

        appender.setThreadFactory(new DefaultThreadFactory("test"));
        appender.setWriterFactory(new ThrowingWriterFactory<CloudWatchWriterConfig,CloudWatchWriterStatistics>());

        CloudWatchWriterStatistics appenderStats = appender.getAppenderStatistics();

        logger.debug("this should trigger writer creation");

        assertNull("writer has not yet thrown", appenderStats.getLastError());

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
        initialize("testReconfigureDiscardProperties");

        logger.debug("a message to trigger writer creation");

        MockCloudWatchWriter writer = appender.getMockWriter();

        assertEquals("initial discard threshold, from appender",    12345,                              appender.getDiscardThreshold());
        assertEquals("initial discard action, from appender",       DiscardAction.newest.toString(),    appender.getDiscardAction());

        assertEquals("initial discard threshold, from writer",      12345,                              writer.config.discardThreshold);
        assertEquals("initial discard action, from writer",         DiscardAction.newest,               writer.config.discardAction);

        appender.setDiscardThreshold(54321);
        appender.setDiscardAction(DiscardAction.oldest.toString());

        assertEquals("updated discard threshold, from appender",    54321,                              appender.getDiscardThreshold());
        assertEquals("updated discard action, from appender",       DiscardAction.oldest.toString(),    appender.getDiscardAction());

        assertEquals("updated discard threshold, from writer",      54321,                              writer.config.discardThreshold);
        assertEquals("updated discard action, from writer",         DiscardAction.oldest,               writer.config.discardAction);

        appenderInternalLogger.assertDebugLog();
        appenderInternalLogger.assertErrorLog();
    }


    @Test
    public void testInvalidDiscardAction() throws Exception
    {
        initialize("testInvalidDiscardAction");

        logger.debug("a message to trigger writer creation");

        MockCloudWatchWriter writer = appender.getMockWriter();

        assertEquals("discard action, from appender",    DiscardAction.oldest.toString(),    appender.getDiscardAction());
        assertEquals("discard action, from writer",      DiscardAction.oldest,               writer.config.discardAction);

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
}
