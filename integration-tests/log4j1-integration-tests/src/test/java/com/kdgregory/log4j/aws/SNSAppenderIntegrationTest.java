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
import java.util.List;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.PropertyConfigurator;

import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.lang.StringUtil;
import static net.sf.kdgcommons.test.StringAsserts.*;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;

import com.kdgregory.logging.aws.sns.SNSLogWriter;
import com.kdgregory.logging.aws.sns.SNSWriterStatistics;
import com.kdgregory.logging.testhelpers.SNSTestHelper;


public class SNSAppenderIntegrationTest
{
    // these clients are shared by all tests
    private static AmazonSNS snsClient;
    private static AmazonSQS sqsClient;

    private SNSTestHelper testHelper;

    // this will be set by init() after the logging framework has been initialized
    private Logger localLogger;

    // this is only set by smoketest
    private static boolean localFactoryUsed;

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    {
        snsClient = AmazonSNSClientBuilder.defaultClient();
        sqsClient = AmazonSQSClientBuilder.defaultClient();
    }


    @Before
    public void setUp()
    {
        MDC.clear();
        localFactoryUsed = false;
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void smoketestByArn() throws Exception
    {
        final int numMessages = 11;

        init("smoketestByArn", true);
        localLogger.info("starting");

        Logger testLogger = Logger.getLogger("TestLogger");
        SNSAppender appender = (SNSAppender)testLogger.getAppender("test");
        SNSWriterStatistics appenderStats = appender.getAppenderStatistics();

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("reading messages");
        List<String> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages);

        assertEquals("actual topic name, from statistics",  testHelper.getTopicName(),      appenderStats.getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   testHelper.getTopicARN(),       appenderStats.getActualTopicArn());
        assertEquals("messages written, from stats",        numMessages,                    appenderStats.getMessagesSent());

        assertTrue("client factory should have been invoked", localFactoryUsed);

        localLogger.info("finished");
    }


    @Test
    public void smoketestByName() throws Exception
    {
        final int numMessages = 11;

        init("smoketestByName", true);
        localLogger.info("starting");

        Logger testLogger = Logger.getLogger("TestLogger");
        SNSAppender appender = (SNSAppender)testLogger.getAppender("test");
        SNSWriterStatistics appenderStats = appender.getAppenderStatistics();

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("reading messages");
        List<String> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages, "Example");

        assertEquals("actual topic name, from statistics",  testHelper.getTopicName(),   appenderStats.getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   testHelper.getTopicARN(),       appenderStats.getActualTopicArn());
        assertEquals("messages written, from stats",        numMessages,                    appenderStats.getMessagesSent());

        assertFalse("client factory should not have been invoked", localFactoryUsed);

        localLogger.info("finished");
    }


    @Test
    public void testTopicMissingAutoCreate() throws Exception
    {
        final int numMessages = 11;

        init("testTopicMissingAutoCreate", false);
        localLogger.info("starting");

        Logger testLogger = Logger.getLogger("TestLogger");
        SNSAppender appender = (SNSAppender)testLogger.getAppender("test");
        SNSWriterStatistics appenderStats = appender.getAppenderStatistics();

        (new MessageWriter(testLogger, numMessages)).run();

        // we need to spin until both the appender and writer are initialized, both
        // triggered by writing messages

        SNSLogWriter writer = null;
        for (int ii = 0 ; ii < 60 ; ii++)
        {
            writer = ClassUtil.getFieldValue(appender, "writer", SNSLogWriter.class);
            if (writer != null)
                break;
            Thread.sleep(1000);
        }
        assertNotNull("writer was created", writer);

        for (int ii = 0 ; ii < 60 ; ii++)
        {
            if (writer.isInitializationComplete())
                break;
            Thread.sleep(1000);
        }
        assertTrue("writer initialization complete", writer.isInitializationComplete());

        assertNotEmpty("topic was created", testHelper.lookupTopic());

        assertEquals("actual topic name, from statistics",  testHelper.getTopicName(),      appenderStats.getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   testHelper.getTopicARN(),       appenderStats.getActualTopicArn());
        assertEquals("messages written, from stats",        numMessages,                    appenderStats.getMessagesSent());

        localLogger.info("finished");
    }


    @Test
    public void testTopicMissingNoAutoCreate() throws Exception
    {
        final int numMessages = 11;

        init("testTopicMissingNoAutoCreate", false);
        localLogger.info("starting");

        Logger testLogger = Logger.getLogger("TestLogger");
        SNSAppender appender = (SNSAppender)testLogger.getAppender("test");
        SNSWriterStatistics appenderStats = appender.getAppenderStatistics();

        (new MessageWriter(testLogger, numMessages)).run();

        // since we didn't hook a queue up to the topic we can't actually look at the messages
        // we also have to spin-loop on the appender to determine when it's been initialized

        SNSLogWriter writer = null;
        for (int ii = 0 ; ii < 60 ; ii++)
        {
            writer = ClassUtil.getFieldValue(appender, "writer", SNSLogWriter.class);
            if (writer != null)
                break;
            Thread.sleep(1000);
        }
        assertNotNull("writer was created", writer);

        // if we can't create the topic then initialization fails, so we'll spin looking for
        // an error to be reported via statistics

        String errorMessage = "";
        for (int ii = 0 ; ii < 60 ; ii++)
        {
            errorMessage = appenderStats.getLastErrorMessage();
            if (! StringUtil.isEmpty(errorMessage))
                break;
            Thread.sleep(1000);
        }
        assertNotEmpty("writer initialization failed", errorMessage);
        assertTrue("error message contains topic name (was: " + errorMessage + ")", errorMessage.contains(testHelper.getTopicName()));

        assertNull("topic was not created", testHelper.lookupTopic());

        // note: if we don't initialize, we don't update name/ARN in statistics
        assertEquals("actual topic name, from statistics",  null,           appenderStats.getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   null,           appenderStats.getActualTopicArn());
        assertEquals("messages written, from stats",        0,              appenderStats.getMessagesSent());

        localLogger.info("finished");
    }


    @Test
    public void testMultiThread() throws Exception
    {
        final int numMessages = 11;
        final int numThreads = 3;
        final int totalMessages = numMessages * numThreads;

        init("testMultiThread", true);
        localLogger.info("starting");

        Logger testLogger = Logger.getLogger("TestLogger");
        SNSAppender appender = (SNSAppender)testLogger.getAppender("test");
        SNSWriterStatistics appenderStats = appender.getAppenderStatistics();

        for (int ii = 0 ; ii < numThreads ; ii++)
        {
            new Thread(new MessageWriter(testLogger, numMessages)).start();
        }

        localLogger.info("reading messages");
        List<String> messages = testHelper.retrieveMessages(totalMessages);

        assertEquals("number of messages", totalMessages, messages.size());
        testHelper.assertMessageContent(messages, "Example");

        assertEquals("actual topic name, from statistics",  testHelper.getTopicName(),   appenderStats.getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   testHelper.getTopicARN(),       appenderStats.getActualTopicArn());
        assertEquals("messages written, from stats",        totalMessages,                  appenderStats.getMessagesSent());

        localLogger.info("finished");
    }


    @Test
    public void testMultiAppender() throws Exception
    {
        final int numMessages = 11;
        final int numAppenders = 2;
        final int totalMessages = numMessages * numAppenders;

        init("testMultiAppender", true);
        localLogger.info("starting");

        Logger testLogger = Logger.getLogger("TestLogger");

        SNSAppender appender1 = (SNSAppender)testLogger.getAppender("test1");
        SNSWriterStatistics stats1 = appender1.getAppenderStatistics();

        SNSAppender appender2 = (SNSAppender)testLogger.getAppender("test2");
        SNSWriterStatistics stats2 = appender2.getAppenderStatistics();

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("reading messages");
        List<String> messages = testHelper.retrieveMessages(totalMessages);

        assertEquals("number of messages", totalMessages, messages.size());
        testHelper.assertMessageContent(messages, "Example1", "Example2");

        assertEquals("actual topic name, appender1, from statistics",   testHelper.getTopicName(),   stats1.getActualTopicName());
        assertEquals("actual topic ARN, appender1, from statistics",    testHelper.getTopicARN(),       stats1.getActualTopicArn());
        assertEquals("messages written, appender1, from stats",         numMessages,                    stats1.getMessagesSent());

        assertEquals("actual topic name, appender2, from statistics",   testHelper.getTopicName(),   stats2.getActualTopicName());
        assertEquals("actual topic ARN, appender2, from statistics",    testHelper.getTopicARN(),       stats2.getActualTopicArn());
        assertEquals("messages written, appender2, from stats",         numMessages,                    stats2.getMessagesSent());

        localLogger.info("finished");
    }

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Logger-specific implementation of utility class.
     */
    private static class MessageWriter
    extends com.kdgregory.logging.testhelpers.MessageWriter
    {
        private Logger logger;

        public MessageWriter(Logger logger, int numMessages)
        {
            super(numMessages);
            this.logger = logger;
        }

        @Override
        protected void writeLogMessage(String message)
        {
            logger.debug(message);
        }
    }


    /**
     *  The static client factory used by smoketestByArn()
     */
    public static AmazonSNS createClient()
    {
        localFactoryUsed = true;
        return AmazonSNSClientBuilder.defaultClient();
    }


    /**
     *  Loads the test-specific Log4J configuration and resets the environment.
     */
    public void init(String testName, boolean createTopic)
    throws Exception
    {
        testHelper = new SNSTestHelper(snsClient, sqsClient);

        if (createTopic)
        {
            testHelper.createTopicAndQueue();
        }

        String propertiesName = "SNSAppenderIntegrationTest/" + testName + ".properties";
        URL config = ClassLoader.getSystemResource(propertiesName);
        assertNotNull("missing configuration: " + propertiesName, config);

        LogManager.resetConfiguration();
        PropertyConfigurator.configure(config);

        MDC.put("testName", testName);

        localLogger = Logger.getLogger(getClass());
    }
}
