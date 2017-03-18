// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.cloudwatch;

import java.net.URL;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import net.sf.kdgcommons.test.StringAsserts;


public class TestCloudwatchAppender
{
    @Test
    public void testConfiguration() throws Exception
    {
        URL config = ClassLoader.getSystemResource("TestCloudwatchAppender.testConfiguration.properties");
        PropertyConfigurator.configure(config);

        Logger rootLogger = Logger.getRootLogger();
        CloudwatchAppender appender = (CloudwatchAppender)rootLogger.getAppender("default");

        assertEquals("log group name",  "argle",    appender.getLogGroup());
        assertEquals("log stream name", "bargle",   appender.getLogStream());
        assertEquals("batch size",      100,        appender.getBatchSize());
        assertEquals("max delay",       1234L,      appender.getMaxDelay());
    }


    @Test
    public void testDefaultConfiguration() throws Exception
    {
        URL config = ClassLoader.getSystemResource("TestCloudwatchAppender.testDefaultConfiguration.properties");
        PropertyConfigurator.configure(config);

        Logger rootLogger = Logger.getRootLogger();
        CloudwatchAppender appender = (CloudwatchAppender)rootLogger.getAppender("default");

        StringAsserts.assertRegex(
                     "log stream name", "2[0-9]+.[0-9]+",   appender.getLogStream());
        assertEquals("batch size",      10,                 appender.getBatchSize());
        assertEquals("max delay",       4000L,              appender.getMaxDelay());
    }

}
