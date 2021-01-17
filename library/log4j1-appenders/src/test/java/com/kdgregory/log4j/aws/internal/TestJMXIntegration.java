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

package com.kdgregory.log4j.aws.internal;

import java.net.URL;
import java.util.Arrays;

import javax.management.ObjectName;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.LogLog;

import com.kdgregory.log4j.aws.StatisticsMBean;
import com.kdgregory.log4j.testhelpers.cloudwatch.TestableCloudWatchAppender;
import com.kdgregory.log4j.testhelpers.kinesis.TestableKinesisAppender;
import com.kdgregory.log4j.testhelpers.sns.TestableSNSAppender;
import com.kdgregory.logging.testhelpers.InlineThreadFactory;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchWriterFactory;
import com.kdgregory.logging.testhelpers.jmx.MockMBeanServer;
import com.kdgregory.logging.testhelpers.kinesis.MockKinesisWriterFactory;
import com.kdgregory.logging.testhelpers.sns.MockSNSWriterFactory;


/**
 *  A single testcase that verifies that all appenders will register themselves
 *  with JMXManager, and that StatisticsMBean properly interacts with an MBean
 *  server.
 */
public class TestJMXIntegration
{
    private Logger logger;
    private TestableCloudWatchAppender cloudwatchAppender;
    private TestableKinesisAppender kinesisAppender;
    private TestableSNSAppender snsAppender;

//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

    private void initializeLogging(String resourceFileName)
    {
        URL config = ClassLoader.getSystemResource(resourceFileName);
        assertNotNull("was able to retrieve config", config);
        PropertyConfigurator.configure(config);

        logger = Logger.getLogger("allDestinations");
        assertNotNull("configuration properly read, logger available", logger);

        cloudwatchAppender = (TestableCloudWatchAppender)logger.getAppender("cloudwatch");
        assertNotNull("could get CloudWatch appender", cloudwatchAppender);

        cloudwatchAppender.setThreadFactory(new InlineThreadFactory());
        cloudwatchAppender.setWriterFactory(new MockCloudWatchWriterFactory());

        kinesisAppender = (TestableKinesisAppender)logger.getAppender("kinesis");
        assertNotNull("could get CloudWatch appender", kinesisAppender);

        kinesisAppender.setThreadFactory(new InlineThreadFactory());
        kinesisAppender.setWriterFactory(new MockKinesisWriterFactory());

        snsAppender = (TestableSNSAppender)logger.getAppender("sns");
        assertNotNull("could get CloudWatch appender", snsAppender);

        snsAppender.setThreadFactory(new InlineThreadFactory());
        snsAppender.setWriterFactory(new MockSNSWriterFactory());
    }

//----------------------------------------------------------------------------
//  Setup/Teardown
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        LogLog.setQuietMode(true);
        JMXManager.reset();
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
    public void testJMXIntegration() throws Exception
    {
        initializeLogging("internal/TestJMXIntegration.properties");

        MockMBeanServer mock = new MockMBeanServer();

        mock.getInstance().createMBean(
            StatisticsMBean.class.getName(),
            new ObjectName("log4j:name=Statistics"));

        assertEquals("after marker registration, number of mbeans registered", 1, mock.registeredBeansByName.size());

        logger.debug("test message");

        assertEquals("after first message, number of mbeans registered", 4, mock.registeredBeansByName.size());

        for (String appenderName : Arrays.asList("cloudwatch", "kinesis", "sns"))
        {
            ObjectName expectedName = new ObjectName("log4j:name=Statistics,appender=" + appenderName);
            assertNotNull("able to retrieve stats bean for " + appenderName, mock.registeredBeansByName.get(expectedName));
        }

        LogManager.shutdown();

        assertEquals("after shutting down logging, number of mbeans registered", 1, mock.registeredBeansByName.size());
    }
}
