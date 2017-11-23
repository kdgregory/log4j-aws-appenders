// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import static net.sf.kdgcommons.test.StringAsserts.*;

import java.net.URL;

import org.junit.Test;

import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.kdgregory.log4j.testhelpers.InlineThreadFactory;
import com.kdgregory.log4j.testhelpers.aws.sns.MockSNSWriter;
import com.kdgregory.log4j.testhelpers.aws.sns.MockSNSWriterFactory;
import com.kdgregory.log4j.testhelpers.aws.sns.TestableSNSAppender;


public class TestSNSAppender
{
    private Logger logger;
    private TestableSNSAppender appender;


    private void initialize(String propsName)
    throws Exception
    {
        URL config = ClassLoader.getSystemResource(propsName);
        PropertyConfigurator.configure(config);

        logger = Logger.getLogger(getClass());

        Logger rootLogger = Logger.getRootLogger();
        appender = (TestableSNSAppender)rootLogger.getAppender("default");

        appender.setThreadFactory(new InlineThreadFactory());
        appender.setWriterFactory(new MockSNSWriterFactory());
    }


//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testConfigurationByName() throws Exception
    {
        initialize("TestSNSAppender/testConfigurationByName.properties");

        assertEquals("topicName",     "example",    appender.getTopicName());
        assertEquals("topicArn",      null,         appender.getTopicArn());
        assertEquals("batch delay",   1L,           appender.getBatchDelay());
    }


    @Test
    public void testConfigurationByArn() throws Exception
    {
        initialize("TestSNSAppender/testConfigurationByArn.properties");

        assertEquals("topicName",     null,         appender.getTopicName());
        assertEquals("topicArn",      "example",    appender.getTopicArn());
        assertEquals("batch delay",   1L,           appender.getBatchDelay());
    }


    @Test
    public void testAppend() throws Exception
    {
        initialize("TestSNSAppender/testAppend.properties");
        MockSNSWriterFactory writerFactory = appender.getWriterFactory();

        assertNull("before messages, writer is null",                   appender.getWriter());

        logger.debug("first message");

        MockSNSWriter writer = appender.getWriter();

        assertNotNull("after message 1, writer is initialized",         writer);
        assertEquals("after message 1, calls to writer factory",        1,                  writerFactory.invocationCount);
        assertRegex("topic name",                                       "name-[0-9]{8}",    writer.config.topicName);
        assertRegex("topic ARN",                                        "arn-[0-9]{8}",     writer.config.topicArn);
        assertEquals("last message appended",                           "first message",    writer.lastMessage.getMessage());
        assertEquals("number of messages in writer queue",              1,                  writer.messages.size());
        assertEquals("first message in queue",                          "first message",    writer.messages.get(0).getMessage());

        logger.debug("second message");

        assertEquals("last message appended",                           "second message",   writer.lastMessage.getMessage());
        assertEquals("number of messages in writer queue",              2,                  writer.messages.size());
        assertEquals("first message in queue",                          "first message",    writer.messages.get(0).getMessage());
        assertEquals("second message in queue",                         "second message",   writer.messages.get(1).getMessage());
    }
}
