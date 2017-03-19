// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.cloudwatch;

import java.net.URL;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;



public class TestCloudwatchAppender
{
    @Test
    public void smoketest() throws Exception
    {
        URL config = ClassLoader.getSystemResource("TestCloudwatchAppender-smoketest.properties");
        PropertyConfigurator.configure(config);

        Logger logger = Logger.getLogger(getClass());
        logger.debug("can you hear me now?");

        Thread.sleep(5000);  // FIXME -- add a test in the appender for processing batches
    }

}
