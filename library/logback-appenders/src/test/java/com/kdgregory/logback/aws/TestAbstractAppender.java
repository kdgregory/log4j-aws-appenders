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

package com.kdgregory.logback.aws;

import static net.sf.kdgcommons.test.StringAsserts.*;

import org.junit.Test;
import static org.junit.Assert.*;

import org.slf4j.LoggerFactory;

import com.kdgregory.logback.testhelpers.cloudwatch.TestableCloudWatchAppender;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.util.MessageQueue.DiscardAction;
import com.kdgregory.logging.testhelpers.TestingException;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchWriter;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchWriterFactory;

import ch.qos.logback.classic.LoggerContext;


/**
 *  These tests exercise appender logic that's implemented in AbstractAppender,
 *  using CloudWatchAppender as a concrete implementation class.
 */
public class TestAbstractAppender
extends AbstractUnitTest<TestableCloudWatchAppender>
{
    public TestAbstractAppender()
    {
        super("TestAbstractAppender/", "TEST");
    }


    @Test
    public void testLifecycle() throws Exception
    {
        initialize("testLifecycle");

        MockCloudWatchWriterFactory writerFactory = appender.getWriterFactory();
        MockCloudWatchWriter writer = appender.getMockWriter();

        assertEquals("post-initialization: calls to writer factory",                1,                      writerFactory.invocationCount);
        assertNotNull("post-initialization: writer created",                                                writer);
        assertNotSame("post-initialization: writer running on background thread",   Thread.currentThread(), writer.writerThread);
        assertTrue("post-initialization: writer told to install shutdown hook",                             writer.config.getUseShutdownHook());

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

        // since we have the writer, we can verify that setting the batch delay gets propagated

        appender.setBatchDelay(1234567);
        assertEquals("writer batch delay propagated", 1234567, writer.config.getBatchDelay());

        // finish off the life-cycle

        assertTrue("appender not closed before shutdown", appender.isStarted());
        assertFalse("writer still running before shutdown", writer.stopped);

        LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
        context.stop();

        assertFalse("appender closed after shutdown", appender.isStarted());
        assertTrue("writer stopped after shutdown", writer.stopped);
    }


    @Test
    public void testSynchronousMode() throws Exception
    {
        initialize("testSynchronousMode");

        MockCloudWatchWriterFactory writerFactory = appender.getWriterFactory();
        MockCloudWatchWriter writer = appender.getMockWriter();

        assertEquals("post-initialization: calls to writer factory",                1,                      writerFactory.invocationCount);
        assertNotNull("post-initialization: writer created",                                                writer);
        assertSame("post-initialization: writer running on background thread",      Thread.currentThread(), writer.writerThread);
        assertTrue("post-initialization: writer told to install shutdown hook",                             writer.config.getUseShutdownHook());
    }


    @Test
    public void testWriteHeaderAndFooter() throws Exception
    {
        initialize("testWriteHeaderAndFooter");

        MockCloudWatchWriter mockWriter = appender.getMockWriter();

        logger.debug("blah blah blah");

        appender.stop();

        assertEquals("number of messages",  3,                  mockWriter.messages.size());
        assertEquals("header is first",     "File Header",      mockWriter.getMessage(0));
        assertEquals("message is middle",   "blah blah blah",   mockWriter.getMessage(1));
        assertEquals("footer is last",      "File Footer",      mockWriter.getMessage(2));
    }


    @Test
    public void testDisableShutdownHook() throws Exception
    {
        initialize("testDisableShutdownHook");

        MockCloudWatchWriter writer = appender.getMockWriter();

        assertFalse("writer was told not to install shutdown hook", writer.config.getUseShutdownHook());
    }


    @Test
    public void testAppenderWaitsOnStop() throws Exception
    {
        initialize("testAppenderWaitsOnStop");

        MockCloudWatchWriter writer = appender.getMockWriter();
        writer.waitUntilInitialized(10000);

        assertEquals("wait count, before stop()", 0, writer.waitUntilStoppedInvocationCount);

        appender.stop();

        // this is here for synchronization -- the writer thread should have finished immediately,
        // and stop() should have joined to it
        writer.writerThread.join();

        assertEquals("wait count, after stop()", 1, writer.waitUntilStoppedInvocationCount);
    }


    @Test
    public void testAppendAfterStop() throws Exception
    {
        initialize("testLifecycle");

        MockCloudWatchWriter writer = appender.getMockWriter();

        appender.stop();

        logger.error("blah blah blah");

        // AppenderBase shouldn't even call append() in this case, but we don't have
        // an invocation counter, so will just look for effects

        assertEquals("nothing was written", 0, writer.messages.size());

        appenderInternalLogger.assertWarningLog();
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
        initialize("testReconfigureDiscardProperties");

        MockCloudWatchWriter writer = appender.getMockWriter();

        assertEquals("initial discard threshold, from appender",    12345,                              appender.getDiscardThreshold());
        assertEquals("initial discard action, from appender",       DiscardAction.newest.toString(),    appender.getDiscardAction());

        assertEquals("initial discard threshold, from writer",      12345,                              writer.config.getDiscardThreshold());
        assertEquals("initial discard action, from writer",         DiscardAction.newest,               writer.config.getDiscardAction());

        appender.setDiscardThreshold(54321);
        appender.setDiscardAction(DiscardAction.oldest.toString());

        assertEquals("updated discard threshold, from appender",    54321,                              appender.getDiscardThreshold());
        assertEquals("updated discard action, from appender",       DiscardAction.oldest.toString(),    appender.getDiscardAction());

        assertEquals("updated discard threshold, from writer",      54321,                              writer.config.getDiscardThreshold());
        assertEquals("updated discard action, from writer",         DiscardAction.oldest,               writer.config.getDiscardAction());

        appenderInternalLogger.assertDebugLog();
        appenderInternalLogger.assertErrorLog();
    }


    @Test
    public void testInvalidDiscardAction() throws Exception
    {
        initialize("testInvalidDiscardAction");

        MockCloudWatchWriter writer = appender.getMockWriter();

        assertEquals("discard action, from appender",    DiscardAction.oldest.toString(),    appender.getDiscardAction());
        assertEquals("discard action, from writer",      DiscardAction.oldest,               writer.config.getDiscardAction());

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
