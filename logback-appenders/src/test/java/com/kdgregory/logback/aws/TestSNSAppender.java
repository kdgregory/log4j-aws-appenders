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

import com.kdgregory.logback.testhelpers.sns.TestableSNSAppender;
import com.kdgregory.logging.aws.sns.SNSWriterStatistics;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.testhelpers.TestingException;
import com.kdgregory.logging.testhelpers.sns.MockSNSWriter;
import com.kdgregory.logging.testhelpers.sns.MockSNSWriterFactory;

/**
 *  These tests exercise the high-level logic of the appender: configuration
 *  and interaction with the writer. To do so, it mocks the LogWriter.
 */
public class TestSNSAppender
{
    private Logger logger;
    private TestableSNSAppender appender;


    private void initialize(String propsName)
    throws Exception
    {
        URL config = ClassLoader.getSystemResource(propsName);
        assertNotNull("was able to retrieve config", config);

        LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
        context.reset();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(config);

        logger = context.getLogger(getClass());
        appender = (TestableSNSAppender)logger.getAppender("SNS");
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testConfigurationByName() throws Exception
    {
        // note: this also tests non-default configuration
        initialize("TestSNSAppender/testConfigurationByName.xml");

        assertEquals("topicName",           "example",                      appender.getTopicName());
        assertEquals("topicArn",            null,                           appender.getTopicArn());

        assertTrue("autoCreate",                                            appender.getAutoCreate());
        assertEquals("subject",             "This is a test",               appender.getSubject());
        assertEquals("batch delay",         1L,                             appender.getBatchDelay());
        assertEquals("discard threshold",   123,                            appender.getDiscardThreshold());
        assertEquals("discard action",      "newest",                       appender.getDiscardAction());
        assertEquals("client factory",      "com.example.Foo.bar",          appender.getClientFactory());
        assertEquals("client endpoint",     "sns.us-east-2.amazonaws.com",  appender.getClientEndpoint());
    }


    @Test
    public void testConfigurationByArn() throws Exception
    {
        // note: this also tests default configuration
        initialize("TestSNSAppender/testConfigurationByArn.xml");

        assertEquals("topicName",           null,                           appender.getTopicName());
        assertEquals("topicArn",            "arn-example",                  appender.getTopicArn());

        assertFalse("autoCreate",                                           appender.getAutoCreate());
        assertEquals("subject",             null,                           appender.getSubject());
        assertEquals("batch delay",         1L,                             appender.getBatchDelay());
        assertEquals("discard threshold",   1000,                           appender.getDiscardThreshold());
        assertEquals("discard action",      "oldest",                       appender.getDiscardAction());
        assertEquals("client factory",      null,                           appender.getClientFactory());
        assertEquals("client endpoint",     null,                           appender.getClientEndpoint());
    }


    @Test
    public void testLifecycle() throws Exception
    {
        initialize("TestSNSAppender/testLifecycle.xml");

        MockSNSWriterFactory writerFactory = (MockSNSWriterFactory)appender.getWriterFactory();
        MockSNSWriter writer = appender.getMockWriter();
        long initialTimestamp = System.currentTimeMillis();

        assertEquals("writer is initialized: calls to writer factory",  1,                  writerFactory.invocationCount);
        assertNotNull("writer is initialized: writer created",          writer);
        assertRegex("writer is initialized: actual topic name",         "name-[0-9]{8}",    writer.config.topicName);
        assertRegex("writer is initialized: actual topic arn",          "arn-[0-9]{8}",     writer.config.topicArn);

        logger.debug("first message");

        // throw in a sleep so that we can discern timestamps
        Thread.sleep(100);

        logger.error("test with exception", new Exception("this is a test"));

        long finalTimestamp = System.currentTimeMillis();

        assertEquals("after message 2, number of messages in writer",   2,                  writer.messages.size());

        LogMessage message1 = writer.messages.get(0);
        assertTrue("message 1 timestamp >= initial timestamp", message1.getTimestamp() >= initialTimestamp);
        assertTrue("message 1 timestamp <= batch timestamp",   message1.getTimestamp() <= finalTimestamp);

        assertRegex(
                "message 1 follows layout: " + message1.getMessage(),
                "20[12][0-9] TestSNSAppender first message",
                message1.getMessage());

        LogMessage message2 = writer.messages.get(1);
        assertTrue("message 2 includes exception",
                   message2.getMessage().indexOf("java.lang.Exception") > 0);
        assertTrue("message 2 includes exception",
                   message2.getMessage().indexOf("this is a test") > 0);


        // finish off the life-cycle

        assertTrue("appender not closed before shutdown", appender.isStarted());
        assertFalse("writer still running before shutdown", writer.stopped);


        LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
        context.stop();

        assertFalse("appender closed after shutdown", appender.isStarted());
        assertTrue("writer stopped after shutdown", writer.stopped);
    }


    @Test
    public void testThrowsIfAppenderClosed() throws Exception
    {
        initialize("TestSNSAppender/testLifecycle.xml");
        MockSNSWriter writer = appender.getMockWriter();

        appender.stop();

        logger.error("blah blah blah");

        assertEquals("nothing was written", 0, writer.messages.size());

        // TODO - once the InternalLogger is implemented, verify that this caused a warning
    }


    @Test
    public void testWriteHeaderAndFooter() throws Exception
    {
        initialize("TestSNSAppender/testWriteHeaderAndFooter.xml");
        MockSNSWriter writer = appender.getMockWriter();

        logger.debug("blah blah blah");

        appender.stop();

        assertEquals("number of messages",  3,                  writer.messages.size());
        assertEquals("header is first",     "File Header",      writer.getMessage(0));
        assertEquals("message is middle",   "blah blah blah",   writer.getMessage(1));
        assertEquals("footer is last",      "File Footer",      writer.getMessage(2));
    }


    @Test
    public void testMaximumMessageSize() throws Exception
    {
        final int snsMaximumMessageSize     = 262144;       // from http://docs.aws.amazon.com/sns/latest/api/API_Publish.html
        final int layoutOverhead            = 1;            // newline after message

        final String undersizeMessage       = StringUtil.repeat('A', snsMaximumMessageSize - 1 - layoutOverhead);
        final String okMessage              = undersizeMessage + "A";
        final String oversizeMessage        = undersizeMessage + "\u00A1";

        initialize("TestSNSAppender/testLifecycle.xml");

        logger.debug("this message triggers writer configuration");

        assertFalse("under max size",          appender.isMessageTooLarge(new LogMessage(0, undersizeMessage)));
        assertFalse("at max size",             appender.isMessageTooLarge(new LogMessage(0, okMessage)));
        assertFalse("over max size",           appender.isMessageTooLarge(new LogMessage(0, oversizeMessage)));
    }


    @Test
    public void testUncaughtExceptionHandling() throws Exception
    {
        initialize("TestSNSAppender/testUncaughtExceptionHandling.xml");

        SNSWriterStatistics appenderStats = appender.getAppenderStatistics();
        assertNull("writer has not yet thrown", appenderStats.getLastError());

        logger.debug("first message should be processed");
        logger.debug("this should trigger writer throwage");

        // without getting really clever, the best way to wait for the throw to be reported is to sit and spin
        for (int ii = 0 ; (ii < 10) && (appenderStats.getLastError() == null) ; ii++)
        {
            Thread.sleep(10);
        }

        assertNull("writer has been reset",         appender.getMockWriter());
        assertEquals("last writer exception class", TestingException.class, appenderStats.getLastError().getClass());
    }
}
