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

import org.junit.After;
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

import com.kdgregory.log4j.aws.testhelpers.MessageWriter;
import com.kdgregory.logging.aws.sns.SNSLogWriter;
import com.kdgregory.logging.aws.sns.SNSWriterStatistics;
import com.kdgregory.logging.testhelpers.SNSTestHelper;
import com.kdgregory.logging.testhelpers.CommonTestHelper;


public class SNSAppenderIntegrationTest
{
    // these clients are shared by all tests
    private static AmazonSNS snsClient;
    private static AmazonSQS sqsClient;

    private SNSTestHelper testHelper;

    // initialized here, and again by init() after the logging framework has been initialized
    private Logger localLogger = LogManager.getLogger(getClass());

    // this is only set by smoketest
    private static boolean localFactoryUsed;

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Retrieves and holds a logger instance and related objects.
     */
    public static class LoggerInfo
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
        MDC.put("testName", testName);
        localLogger.info("starting");

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

        localLogger = Logger.getLogger(getClass());
    }

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
        // this won't be updated by most tests
        localFactoryUsed = false;
    }


    @After
    public void tearDown()
    {
        localLogger.info("finished");
        MDC.clear();
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void smoketestByArn() throws Exception
    {
        final int numMessages = 11;

        init("smoketestByArn", true);

        LoggerInfo loggerInfo = new LoggerInfo("TestLogger", "test");

        (new MessageWriter(loggerInfo.logger, numMessages)).run();

        localLogger.info("reading messages");
        List<String> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages);

        assertEquals("actual topic name, from statistics",  testHelper.getTopicName(),      loggerInfo.stats.getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   testHelper.getTopicARN(),       loggerInfo.stats.getActualTopicArn());
        assertEquals("messages written, from stats",        numMessages,                    loggerInfo.stats.getMessagesSent());

        assertTrue("client factory should have been invoked", localFactoryUsed);
    }


    @Test
    public void smoketestByName() throws Exception
    {
        final int numMessages = 11;

        init("smoketestByName", true);

        LoggerInfo loggerInfo = new LoggerInfo("TestLogger", "test");

        (new MessageWriter(loggerInfo.logger, numMessages)).run();

        localLogger.info("reading messages");
        List<String> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages, "Example");

        assertEquals("actual topic name, from statistics",  testHelper.getTopicName(),      loggerInfo.stats.getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   testHelper.getTopicARN(),       loggerInfo.stats.getActualTopicArn());
        assertEquals("messages written, from stats",        numMessages,                    loggerInfo.stats.getMessagesSent());

        assertFalse("client factory should not have been invoked", localFactoryUsed);
    }


    @Test
    public void testTopicMissingAutoCreate() throws Exception
    {
        final int numMessages = 11;

        init("testTopicMissingAutoCreate", false);

        LoggerInfo loggerInfo = new LoggerInfo("TestLogger", "test");

        localLogger.info("writing messages");
        (new MessageWriter(loggerInfo.logger, numMessages)).run();

        localLogger.info("waiting for writer initialization to finish");
        CommonTestHelper.waitUntilWriterInitialized(loggerInfo.appender, SNSLogWriter.class, 30000);

        assertNotEmpty("topic was created", testHelper.lookupTopic());

        assertEquals("actual topic name, from statistics",  testHelper.getTopicName(),      loggerInfo.stats.getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   testHelper.getTopicARN(),       loggerInfo.stats.getActualTopicArn());

        // no queue attached to this topic so we can't read messages directly

        CommonTestHelper.waitUntilMessagesSent(loggerInfo.stats, numMessages, 30000);
    }


    @Test
    public void testTopicMissingNoAutoCreate() throws Exception
    {
        final int numMessages = 11;

        init("testTopicMissingNoAutoCreate", false);

        LoggerInfo loggerInfo = new LoggerInfo("TestLogger", "test");

        (new MessageWriter(loggerInfo.logger, numMessages)).run();

        // since we didn't hook a queue up to the topic we can't actually look at the messages
        // we also have to spin-loop on the appender to determine when it's been initialized

        SNSLogWriter writer = null;
        for (int ii = 0 ; ii < 60 ; ii++)
        {
            writer = ClassUtil.getFieldValue(loggerInfo.appender, "writer", SNSLogWriter.class);
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
            errorMessage = loggerInfo.stats.getLastErrorMessage();
            if (! StringUtil.isEmpty(errorMessage))
                break;
            Thread.sleep(1000);
        }
        assertNotEmpty("writer initialization failed", errorMessage);
        assertTrue("error message contains topic name (was: " + errorMessage + ")", errorMessage.contains(testHelper.getTopicName()));

        assertNull("topic was not created", testHelper.lookupTopic());

        assertEquals("actual topic name, from statistics",  testHelper.getTopicName(),      loggerInfo.stats.getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   testHelper.getTopicARN(),       loggerInfo.stats.getActualTopicArn());
        assertEquals("messages written, from stats",        0,                              loggerInfo.stats.getMessagesSent());
    }


    @Test
    public void testMultiThread() throws Exception
    {
        final int numMessages = 11;
        final int numThreads = 3;
        final int totalMessages = numMessages * numThreads;

        init("testMultiThread", true);

        LoggerInfo loggerInfo = new LoggerInfo("TestLogger", "test");

        for (int ii = 0 ; ii < numThreads ; ii++)
        {
            new Thread(new MessageWriter(loggerInfo.logger, numMessages)).start();
        }

        localLogger.info("reading messages");
        List<String> messages = testHelper.retrieveMessages(totalMessages);

        assertEquals("number of messages", totalMessages, messages.size());
        testHelper.assertMessageContent(messages, "Example");

        assertEquals("actual topic name, from statistics",  testHelper.getTopicName(),      loggerInfo.stats.getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   testHelper.getTopicARN(),       loggerInfo.stats.getActualTopicArn());
        assertEquals("messages written, from stats",        totalMessages,                  loggerInfo.stats.getMessagesSent());
    }


    @Test
    public void testMultiAppender() throws Exception
    {
        final int numMessages = 11;
        final int numAppenders = 2;
        final int totalMessages = numMessages * numAppenders;

        init("testMultiAppender", true);

        LoggerInfo loggerInfo1 = new LoggerInfo("TestLogger", "test1");
        LoggerInfo loggerInfo2 = new LoggerInfo("TestLogger", "test2");

        // same logger regardless of which info object we use
        (new MessageWriter(loggerInfo1.logger, numMessages)).run();

        localLogger.info("reading messages");
        List<String> messages = testHelper.retrieveMessages(totalMessages);

        assertEquals("number of messages", totalMessages, messages.size());
        testHelper.assertMessageContent(messages, "Example1", "Example2");

        assertEquals("actual topic name, appender1, from statistics",   testHelper.getTopicName(),      loggerInfo1.stats.getActualTopicName());
        assertEquals("actual topic ARN, appender1, from statistics",    testHelper.getTopicARN(),       loggerInfo1.stats.getActualTopicArn());
        assertEquals("messages written, appender1, from stats",         numMessages,                    loggerInfo1.stats.getMessagesSent());

        assertEquals("actual topic name, appender2, from statistics",   testHelper.getTopicName(),      loggerInfo2.stats.getActualTopicName());
        assertEquals("actual topic ARN, appender2, from statistics",    testHelper.getTopicARN(),       loggerInfo2.stats.getActualTopicArn());
        assertEquals("messages written, appender2, from stats",         numMessages,                    loggerInfo2.stats.getMessagesSent());
    }


    @Test
    public void testAlternateRegion() throws Exception
    {
        final int numMessages = 11;

        init("testAlternateRegion", false);

        LoggerInfo loggerInfo = new LoggerInfo("TestLogger", "test");

        // BEWARE: my default region is us-east-1, so I use us-east-2 as the alternate
        //         if that is your default, then the test will fail
        AmazonSNS altSNSclient = AmazonSNSClientBuilder.standard().withRegion("us-east-2").build();
        AmazonSQS altSQSclient = AmazonSQSClientBuilder.standard().withRegion("us-east-2").build();
        SNSTestHelper altTestHelper = new SNSTestHelper(testHelper, altSNSclient, altSQSclient);

        localLogger.info("writing messages");
        (new MessageWriter(loggerInfo.logger, numMessages)).run();

        localLogger.info("waiting for writer initialization to finish");
        CommonTestHelper.waitUntilWriterInitialized(loggerInfo.appender, SNSLogWriter.class, 30000);

        assertNotEmpty("topic was created",                  altTestHelper.lookupTopic());
        assertNull("topic does not exist in default region", testHelper.lookupTopic());

        assertEquals("actual topic name, from statistics",  altTestHelper.getTopicName(),      loggerInfo.stats.getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   altTestHelper.getTopicARN(),       loggerInfo.stats.getActualTopicArn());

        // no queue attached to this topic so we can't read messages directly

        CommonTestHelper.waitUntilMessagesSent(loggerInfo.stats, numMessages, 30000);
    }
}
