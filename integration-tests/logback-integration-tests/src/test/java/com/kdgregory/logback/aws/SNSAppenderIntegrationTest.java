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

package com.kdgregory.logback.aws;

import static net.sf.kdgcommons.test.StringAsserts.*;

import java.net.URL;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import net.sf.kdgcommons.lang.StringUtil;

import com.kdgregory.logback.aws.testhelpers.MessageWriter;
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
    private Logger localLogger = LoggerFactory.getLogger(getClass());

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
        public ch.qos.logback.classic.Logger logger;
        public SNSAppender<ILoggingEvent> appender;
        public SNSWriterStatistics stats;

        public LoggerInfo(String loggerName, String appenderName)
        {
            logger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(loggerName);
            appender = (SNSAppender<ILoggingEvent>)logger.getAppender(appenderName);
            stats = appender.getAppenderStatistics();
        }
    }


    /**
     *  This function is used as a client factory by the smoketest.
     */
    public static AmazonSNS createClient()
    {
        localFactoryUsed = true;
        return AmazonSNSClientBuilder.defaultClient();
    }


    /**
     *  Loads the test-specific Log4J configuration and resets the environment.
     */
    public void init(String testName, boolean createTopic) throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        testHelper = new SNSTestHelper(snsClient, sqsClient);

        // if we're going to create the topic we must do it before initializing the logging system
        if (createTopic)
        {
            testHelper.createTopicAndQueue();
        }

        String propertiesName = "SNSAppenderIntegrationTest/" + testName + ".xml";
        URL config = ClassLoader.getSystemResource(propertiesName);
        assertNotNull("missing configuration: " + propertiesName, config);

        LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
        context.reset();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(config);

        localLogger = LoggerFactory.getLogger(getClass());
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
//  Tests
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

        localLogger.info("waiting for writer initialization to finish");
        CommonTestHelper.waitUntilWriterInitialized(loggerInfo.appender, SNSLogWriter.class, 30000);

        assertNotEmpty("topic was created", testHelper.lookupTopic());

        assertEquals("actual topic name, from statistics",  testHelper.getTopicName(),      loggerInfo.stats.getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   testHelper.getTopicARN(),       loggerInfo.stats.getActualTopicArn());

        // even though we trust initialization, we'll still write messages

        localLogger.info("writing messages");
        (new MessageWriter(loggerInfo.logger, numMessages)).run();

        // no queue attached to this topic so we can't read messages directly

        CommonTestHelper.waitUntilMessagesSent(loggerInfo.stats, numMessages, 30000);
    }


    @Test
    public void testTopicMissingNoAutoCreate() throws Exception
    {
        init("testTopicMissingNoAutoCreate", false);

        LoggerInfo loggerInfo = new LoggerInfo("TestLogger", "test");

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

        localLogger.info("waiting for writer initialization to finish");
        CommonTestHelper.waitUntilWriterInitialized(loggerInfo.appender, SNSLogWriter.class, 30000);

        assertNotEmpty("topic was created",                  altTestHelper.lookupTopic());
        assertNull("topic does not exist in default region", testHelper.lookupTopic());

        assertEquals("actual topic name, from statistics",  altTestHelper.getTopicName(),      loggerInfo.stats.getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   altTestHelper.getTopicARN(),       loggerInfo.stats.getActualTopicArn());

        localLogger.info("writing messages");
        (new MessageWriter(loggerInfo.logger, numMessages)).run();

        // no queue attached to this topic so we can't read messages directly

        CommonTestHelper.waitUntilMessagesSent(loggerInfo.stats, numMessages, 30000);
    }
}
