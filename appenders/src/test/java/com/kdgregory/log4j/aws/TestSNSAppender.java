// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.net.URL;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.LogLog;

import com.kdgregory.log4j.testhelpers.InlineThreadFactory;
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
}
