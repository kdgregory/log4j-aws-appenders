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
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.PropertyConfigurator;

import org.slf4j.LoggerFactory;

import net.sf.kdgcommons.lang.ClassUtil;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;

import com.kdgregory.log4j.aws.testhelpers.MessageWriter;
import com.kdgregory.logging.aws.kinesis.KinesisLogWriter;
import com.kdgregory.logging.aws.kinesis.KinesisWriterStatistics;
import com.kdgregory.logging.test.AbstractKinesisAppenderIntegrationTest;
import com.kdgregory.logging.testhelpers.KinesisTestHelper;
import com.kdgregory.logging.testhelpers.CommonTestHelper;


public class KinesisAppenderIntegrationTest
extends AbstractKinesisAppenderIntegrationTest
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
        public KinesisAppender appender;
        public KinesisWriterStatistics stats;

        public LoggerInfo(String loggerName, String appenderName)
        {
            logger = Logger.getLogger(loggerName);
            appender = (KinesisAppender)logger.getAppender(appenderName);
            stats = appender.getAppenderStatistics();
        }

        @Override
        public MessageWriter createMessageWriter(int numMessages)
        {
            return new MessageWriter(logger, numMessages);
        }

        @Override
        public KinesisLogWriter getWriter() throws Exception
        {
            return ClassUtil.getFieldValue(appender, "writer", KinesisLogWriter.class);
        }

        @Override
        public KinesisWriterStatistics getStats()
        {
            return stats;
        }

        @Override
        public boolean supportsConfigurationChanges()
        {
            return true;
        }

        @Override
        public String waitUntilWriterInitialized() throws Exception
        {
            CommonTestHelper.waitUntilWriterInitialized(appender, KinesisLogWriter.class, 10000);
            return stats.getLastErrorMessage();
        }
    }


    /**
     *  Loads the test-specific Log4J configuration and resets the environment.
     */
    public void init(String testName)
    throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        testHelper = new KinesisTestHelper(helperClient, testName);
        testHelper.deleteStreamIfExists();

        String propertiesName = "KinesisAppenderIntegrationTest/" + testName + ".properties";
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
        AbstractKinesisAppenderIntegrationTest.beforeClass();
    }


    @After
    @Override
    public void tearDown()
    {
        super.tearDown();
        MDC.clear();
    }

//----------------------------------------------------------------------------
//  Tests
//
//  Note: most tests create their streams, since we want to examine various
//        combinations of shards and partition keys
//----------------------------------------------------------------------------

    @Test
    public void smoketest() throws Exception
    {
        init("smoketest");
        super.smoketest(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testMultipleThreadsSingleAppender() throws Exception
    {
        init("testMultipleThreadsSingleAppender");
        super.testMultipleThreadsSingleAppender(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testMultipleThreadsMultipleAppendersDistinctPartitions() throws Exception
    {
        init("testMultipleThreadsMultipleAppendersDistinctPartitions");
        super.testMultipleThreadsMultipleAppendersDistinctPartitions(
            new LoggerInfo("TestLogger1", "test1"),
            new LoggerInfo("TestLogger2", "test2"),
            new LoggerInfo("TestLogger3", "test3"));
    }


    @Test
    public void testRandomPartitionKeys() throws Exception
    {
        init("testRandomPartitionKeys");
        super.testRandomPartitionKeys(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testFailsIfNoStreamPresent() throws Exception
    {
        init("testFailsIfNoStreamPresent");
        super.testFailsIfNoStreamPresent(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testFactoryMethod() throws Exception
    {
        init("testFactoryMethod");
        super.testFactoryMethod(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testAlternateRegion() throws Exception
    {
        // BEWARE: my default region is us-east-1, so I use us-east-2 as the alternate
        //         if that is your default, then the test will fail
        AmazonKinesis altClient = AmazonKinesisClientBuilder.standard().withRegion("us-east-2").build();
        KinesisTestHelper altTestHelper = new KinesisTestHelper(altClient, "testAlternateRegion");

        // deleting the alternate stream here for consistency with Logback test; it isn't necessary
        altTestHelper.deleteStreamIfExists();

        init("testAlternateRegion");
        super.testAlternateRegion(new LoggerInfo("TestLogger", "test"), altTestHelper);
    }
}
