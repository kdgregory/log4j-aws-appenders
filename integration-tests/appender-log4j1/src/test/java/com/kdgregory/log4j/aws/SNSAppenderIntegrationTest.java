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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.PropertyConfigurator;

import org.slf4j.LoggerFactory;

import net.sf.kdgcommons.lang.ClassUtil;

import com.kdgregory.log4j.aws.testhelpers.MessageWriter;
import com.kdgregory.logging.aws.sns.SNSLogWriter;
import com.kdgregory.logging.aws.sns.SNSWriterStatistics;
import com.kdgregory.logging.test.AbstractSNSAppenderIntegrationTest;
import com.kdgregory.logging.testhelpers.SNSTestHelper;
import com.kdgregory.logging.testhelpers.CommonTestHelper;


public class SNSAppenderIntegrationTest
extends AbstractSNSAppenderIntegrationTest
{

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Retrieves and holds a logger instance and related objects.
     */
    public static class LoggerInfo
    implements LoggerAccessor
    {
        public Logger logger;
        public SNSAppender appender;
        public SNSWriterStatistics stats;

        public LoggerInfo(String loggerName, String appenderName)
        {
            logger = Logger.getLogger(loggerName);
            appender = (SNSAppender)logger.getAppender(appenderName);
            stats = appender.getAppenderStatistics();
        }

        @Override
        public MessageWriter newMessageWriter(int numMessages)
        {
            return new MessageWriter(logger, numMessages);
        }

        @Override
        public String waitUntilWriterInitialized()
        throws Exception
        {
            CommonTestHelper.waitUntilWriterInitialized(appender, SNSLogWriter.class, 30000);
            return stats.getLastErrorMessage();
        }

        @Override
        public SNSLogWriter getWriter() throws Exception
        {
            return ClassUtil.getFieldValue(appender, "writer", SNSLogWriter.class);
        }

        @Override
        public SNSWriterStatistics getStats()
        {
            return stats;
        }
    }


    /**
     *  Loads the test-specific Log4J configuration and resets the environment.
     */
    public void init(String testName, boolean createTopic)
    throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        testHelper = new SNSTestHelper(helperSNSclient, helperSQSclient);

        if (createTopic)
        {
            testHelper.createTopicAndQueue();
        }

        String propertiesName = "SNSAppenderIntegrationTest/" + testName + ".properties";
        URL config = ClassLoader.getSystemResource(propertiesName);
        assertNotNull("missing configuration: " + propertiesName, config);

        LogManager.resetConfiguration();
        PropertyConfigurator.configure(config);

        localLogger = LoggerFactory.getLogger(getClass());
    }

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    {
        AbstractSNSAppenderIntegrationTest.beforeClass();
    }


    @After
    @Override
    public void tearDown()
    {
        super.tearDown();
        MDC.clear();
    }

    
    @AfterClass
    public static void afterClass()
    {
        AbstractSNSAppenderIntegrationTest.afterClass();
    }
    
//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void smoketestByArn() throws Exception
    {
        init("smoketestByArn", true);
        super.smoketestByArn(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void smoketestByName() throws Exception
    {
        init("smoketestByName", true);
        super.smoketestByName(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testTopicMissingAutoCreate() throws Exception
    {
        init("testTopicMissingAutoCreate", false);
        super.testTopicMissingAutoCreate(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testTopicMissingNoAutoCreate() throws Exception
    {
        init("testTopicMissingNoAutoCreate", false);
        LoggerInfo loggerInfo = new LoggerInfo("TestLogger", "test");

        // sending a single message triggers writer creation
        loggerInfo.newMessageWriter(1).run();

        super.testTopicMissingNoAutoCreate(loggerInfo);
    }


    @Test
    public void testMultiThread() throws Exception
    {
        init("testMultiThread", true);
        super.testMultiThread(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testMultiAppender() throws Exception
    {
        init("testMultiAppender", true);
        super.testMultiAppender(
            new LoggerInfo("TestLogger", "test1"),
            new LoggerInfo("TestLogger", "test2"));
    }


    @Test
    public void testFactoryMethod() throws Exception
    {
        init("testFactoryMethod", true);
        super.testFactoryMethod(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testAlternateRegion() throws Exception
    {
        init("testAlternateRegion", false);
        super.testAlternateRegion(new LoggerInfo("TestLogger", "test"));
    }
}
