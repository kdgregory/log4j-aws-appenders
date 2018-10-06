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


import java.net.URL;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.LogLog;

import net.sf.kdgcommons.lang.StringUtil;
import net.sf.kdgcommons.test.StringAsserts;

import com.kdgregory.log4j.testhelpers.HeaderFooterLayout;
import com.kdgregory.log4j.testhelpers.aws.sns.MockSNSWriterFactory;
import com.kdgregory.log4j.testhelpers.aws.sns.TestableSNSAppender;
import com.kdgregory.logging.aws.common.DefaultThreadFactory;
import com.kdgregory.logging.aws.common.LogMessage;
import com.kdgregory.logging.aws.sns.SNSAppenderStatistics;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;
import com.kdgregory.logging.aws.testhelpers.InlineThreadFactory;
import com.kdgregory.logging.aws.testhelpers.TestingException;
import com.kdgregory.logging.aws.testhelpers.ThrowingWriterFactory;
import com.kdgregory.logging.aws.testhelpers.sns.MockSNSWriter;

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
        PropertyConfigurator.configure(config);

        logger = Logger.getLogger(getClass());

        Logger rootLogger = Logger.getRootLogger();
        appender = (TestableSNSAppender)rootLogger.getAppender("default");

        appender.setThreadFactory(new InlineThreadFactory());
        appender.setWriterFactory(new MockSNSWriterFactory());
    }

//----------------------------------------------------------------------------
//  JUnit stuff
//----------------------------------------------------------------------------

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

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testConfigurationByName() throws Exception
    {
        // note: this also tests non-default configuration
        initialize("TestSNSAppender/testConfigurationByName.properties");

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
        initialize("TestSNSAppender/testConfigurationByArn.properties");

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
    public void testAppend() throws Exception
    {
        initialize("TestSNSAppender/testAppend.properties");
        MockSNSWriterFactory writerFactory = (MockSNSWriterFactory)appender.getWriterFactory();

        assertNull("before messages, writer is null",                   appender.getMockWriter());

        logger.debug("first message");

        MockSNSWriter writer = appender.getMockWriter();

        assertNotNull("after message 1, writer is initialized",         writer);
        assertEquals("after message 1, calls to writer factory",        1,                  writerFactory.invocationCount);
        StringAsserts.assertRegex("topic name",                         "name-[0-9]{8}",    writer.config.topicName);
        StringAsserts.assertRegex("topic ARN",                          "arn-[0-9]{8}",     writer.config.topicArn);
        assertEquals("last message appended",                           "first message",    writer.lastMessage.getMessage());
        assertEquals("number of messages in writer queue",              1,                  writer.messages.size());
        assertEquals("first message in queue",                          "first message",    writer.messages.get(0).getMessage());

        logger.debug("second message");

        assertEquals("last message appended",                           "second message",   writer.lastMessage.getMessage());
        assertEquals("number of messages in writer queue",              2,                  writer.messages.size());
        assertEquals("first message in queue",                          "first message",    writer.messages.get(0).getMessage());
        assertEquals("second message in queue",                         "second message",   writer.messages.get(1).getMessage());
    }


    @Test(expected=IllegalStateException.class)
    public void testThrowsIfAppenderClosed() throws Exception
    {
        initialize("TestSNSAppender/testAppend.properties");

        // write the first message to initialize the appender
        logger.debug("should not throw");

        appender.close();

        // second message should throw
        logger.error("blah blah blah");
    }


    @Test
    public void testWriteHeaderAndFooter() throws Exception
    {
        initialize("TestSNSAppender/testWriteHeaderAndFooter.properties");

        logger.debug("message");

        // must retrieve writer before we shut down
        MockSNSWriter writer = appender.getMockWriter();
        LogManager.shutdown();

        assertEquals("number of messages written to log",   3,                          writer.messages.size());
        assertEquals("header is first",                     HeaderFooterLayout.HEADER,  writer.getMessage(0));
        assertEquals("message is second",                   "message",                  writer.getMessage(1));
        assertEquals("footer is last",                      HeaderFooterLayout.FOOTER,  writer.getMessage(2));
    }


    @Test
    public void testMaximumMessageSize() throws Exception
    {
        final int snsMaximumMessageSize     = 262144;       // from http://docs.aws.amazon.com/sns/latest/api/API_Publish.html
        final int layoutOverhead            = 1;            // newline after message

        final String undersizeMessage       = StringUtil.repeat('A', snsMaximumMessageSize - 1 - layoutOverhead);
        final String okMessage              = undersizeMessage + "A";
        final String oversizeMessage        = undersizeMessage + "\u00A1";

        initialize("TestSNSAppender/testAppend.properties");

        logger.debug("this message triggers writer configuration");

        assertFalse("under max size",          appender.isMessageTooLarge(new LogMessage(0, undersizeMessage)));
        assertFalse("at max size",             appender.isMessageTooLarge(new LogMessage(0, okMessage)));
        assertFalse("over max size",           appender.isMessageTooLarge(new LogMessage(0, oversizeMessage)));
    }


    @Test
    public void testUncaughtExceptionHandling() throws Exception
    {
        initialize("TestSNSAppender/testUncaughtExceptionHandling.properties");

        // note that we will be running the writer on a separate thread

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(new ThrowingWriterFactory<SNSWriterConfig,SNSAppenderStatistics>());

        SNSAppenderStatistics appenderStats = appender.getAppenderStatistics();

        logger.debug("this should trigger writer creation");

        assertNull("writer has not yet thrown", appenderStats.getLastError());

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
