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


import java.net.URL;

import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.StringUtil;
import static net.sf.kdgcommons.test.StringAsserts.*;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;

import com.kdgregory.logback.testhelpers.TestableLogbackInternalLogger;
import com.kdgregory.logback.testhelpers.cloudwatch.TestableCloudWatchAppender;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.testhelpers.TestingException;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchWriter;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchWriterFactory;


/**
 *  These tests exercise the high-level logic of the appender: configuration
 *  and interaction with the writer. To do so, it mocks the LogWriter.
 */
public class TestCloudWatchAppender
{
    private Logger logger;
    private TestableCloudWatchAppender appender;
    private TestableLogbackInternalLogger appenderInternalLogger;


    private void initialize(String testName)
    throws Exception
    {
        String propsName = "TestCloudWatchAppender/" + testName + ".xml";
        URL config = ClassLoader.getSystemResource(propsName);
        assertNotNull("was able to retrieve config", config);

        LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
        context.reset();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(config);

        logger = context.getLogger(getClass());
        appender = (TestableCloudWatchAppender)logger.getAppender("CLOUDWATCH");
        appenderInternalLogger = appender.getInternalLogger();
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testConfiguration() throws Exception
    {
        initialize("testConfiguration");

        assertEquals("log group name",      "argle",                        appender.getLogGroup());
        assertEquals("log stream name",     "bargle",                       appender.getLogStream());
        assertEquals("max delay",           1234L,                          appender.getBatchDelay());
        assertEquals("sequence",            2,                              appender.getSequence());
        assertEquals("rotation mode",       "interval",                     appender.getRotationMode());
        assertEquals("rotation interval",   86400000L,                      appender.getRotationInterval());
        assertEquals("discard threshold",   12345,                          appender.getDiscardThreshold());
        assertEquals("discard action",      "newest",                       appender.getDiscardAction());
        assertEquals("client factory",      "com.example.Foo.bar",          appender.getClientFactory());
        assertEquals("client endpoint",     "logs.us-west-2.amazonaws.com", appender.getClientEndpoint());
    }


    @Test
    public void testDefaultConfiguration() throws Exception
    {
        initialize("testDefaultConfiguration");

        // note: this is allowed at time of configuration, would disable logger if we try to append
        assertNull("log group name",                                        appender.getLogGroup());

        assertEquals("log stream name",     "{startupTimestamp}",           appender.getLogStream());
        assertEquals("max delay",           2000L,                          appender.getBatchDelay());
        assertEquals("sequence",            0,                              appender.getSequence());
        assertEquals("rotation mode",       "none",                         appender.getRotationMode());
        assertEquals("rotation interval",   -1,                             appender.getRotationInterval());
        assertEquals("discard threshold",   10000,                          appender.getDiscardThreshold());
        assertEquals("discard action",      "oldest",                       appender.getDiscardAction());
        assertEquals("client factory",      null,                           appender.getClientFactory());
        assertEquals("client endpoint",     null,                           appender.getClientEndpoint());
    }


    @Test
    public void testLifecycle() throws Exception
    {
        initialize("testLifecycle");

        MockCloudWatchWriterFactory writerFactory = appender.getWriterFactory();
        MockCloudWatchWriter writer = (MockCloudWatchWriter)appender.getWriter();

        assertEquals("writer is initialized: calls to writer factory",  1,              writerFactory.invocationCount);
        assertNotNull("writer is initialized: writer created",          writer);
        assertEquals("writer is initialized: actual log-group name",    "argle",        writer.logGroup);
        assertRegex("writer is initialized: actual log-stream name",    "20\\d{12}",    writer.logStream);

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
                "20[12][0-9] TestCloudWatchAppender first message",
                message1.getMessage());

        LogMessage message2 = writer.messages.get(1);
        assertTrue("message 2 includes exception",
                   message2.getMessage().indexOf("java.lang.Exception") > 0);
        assertTrue("message 2 includes exception",
                   message2.getMessage().indexOf("this is a test") > 0);

        // since we have the writer, we can verify that setting the batch delay gets propagated

        appender.setBatchDelay(1234567);
        assertEquals("writer batch delay propagated", 1234567, writer.batchDelay);

        // finish off the life-cycle

        assertTrue("appender not closed before shutdown", appender.isStarted());
        assertFalse("writer still running before shutdown", writer.stopped);

        LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
        context.stop();

        assertFalse("appender closed after shutdown", appender.isStarted());
        assertTrue("writer stopped after shutdown", writer.stopped);
    }


    @Test
    public void testStopAppender() throws Exception
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
    public void testSubstitution() throws Exception
    {
        // note that the property value includes invalid characters
        System.setProperty("TestCloudWatchAppender.testSubstitution", "foo/bar");

        initialize("testSubstitution");

        MockCloudWatchWriter writer = appender.getMockWriter();

        assertEquals("appender's log group name",   "MyLog-{sysprop:TestCloudWatchAppender.testSubstitution}",  appender.getLogGroup());
        assertEquals("appender's log stream name",  "MyStream-{timestamp}-{bogus}",                             appender.getLogStream());
        assertEquals("writers log group name",      "MyLog-foo/bar",                                            writer.logGroup);
        assertRegex("writers log stream name",      "MyStream-20\\d{12}-\\{bogus}",                             writer.logStream);
    }


    @Test
    public void testExplicitRotation() throws Exception
    {
        initialize("testExplicitRotation");

        MockCloudWatchWriterFactory writerFactory = appender.getWriterFactory();

        logger.debug("first message");

        MockCloudWatchWriter writer0 = appender.getMockWriter();

        assertEquals("pre-rotate, writer factory calls",            1,          writerFactory.invocationCount);
        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.logStream);

        appender.rotate();

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertEquals("post-rotate, writer factory calls",           2,          writerFactory.invocationCount);
        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.logStream);

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

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.logStream);

        // these messages should trigger rotation

        logger.debug("message 1");
        logger.debug("message 2");
        logger.debug("message 3");
        logger.debug("message 4");

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.logStream);
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

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.logStream);

        appender.updateLastRotationTimestamp(-20000);

        logger.debug("second message");

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.logStream);
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

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.logStream);

        appender.updateLastRotationTimestamp(-3600000);

        logger.debug("second message");

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.logStream);
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

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.logStream);

        appender.updateLastRotationTimestamp(-86400000);

        logger.debug("second message");

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.logStream);
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

        // TODO - check InternalLogger once implemented
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
    public void testExceptionInLayout() throws Exception
    {
        initialize("testExceptionInLayout");

        MockCloudWatchWriter writer = (MockCloudWatchWriter)appender.getWriter();

        logger.debug("this should trigger throwage");

        assertEquals("no messages sent to writer", 0, writer.messages.size());

        appenderInternalLogger.assertDebugLog();
        appenderInternalLogger.assertErrorLog("unable to apply layout");
        appenderInternalLogger.assertErrorThrowables(TestingException.class);
    }


    @Test
    public void testReconfigureDiscardProperties() throws Exception
    {
        initialize("testReconfigureDiscardProperties");

        MockCloudWatchWriter writer = (MockCloudWatchWriter)appender.getWriter();

        assertEquals("initial discard threshold, from appender",    12345,                              appender.getDiscardThreshold());
        assertEquals("initial discard action, from appender",       DiscardAction.newest.toString(),    appender.getDiscardAction());

        assertEquals("initial discard threshold, from writer",      12345,                              writer.discardThreshold);
        assertEquals("initial discard action, from writer",         DiscardAction.newest,               writer.discardAction);

        appender.setDiscardThreshold(54321);
        appender.setDiscardAction(DiscardAction.oldest.toString());

        assertEquals("updated discard threshold, from appender",    54321,                              appender.getDiscardThreshold());
        assertEquals("updated discard action, from appender",       DiscardAction.oldest.toString(),    appender.getDiscardAction());

        assertEquals("updated discard threshold, from writer",      54321,                              writer.discardThreshold);
        assertEquals("updated discard action, from writer",         DiscardAction.oldest,               writer.discardAction);
    }
}
