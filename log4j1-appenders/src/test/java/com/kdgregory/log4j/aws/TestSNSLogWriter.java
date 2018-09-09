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
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.LogLog;

import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.lang.StringUtil;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;

import com.kdgregory.log4j.aws.internal.shared.AbstractLogWriter;
import com.kdgregory.log4j.aws.internal.shared.DefaultThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.MessageQueue;
import com.kdgregory.log4j.aws.internal.shared.MessageQueue.DiscardAction;
import com.kdgregory.log4j.aws.internal.sns.SNSAppenderStatistics;
import com.kdgregory.log4j.aws.internal.sns.SNSWriterFactory;
import com.kdgregory.log4j.testhelpers.InlineThreadFactory;
import com.kdgregory.log4j.testhelpers.NullThreadFactory;
import com.kdgregory.log4j.testhelpers.TestingException;
import com.kdgregory.log4j.testhelpers.aws.sns.MockSNSClient;
import com.kdgregory.log4j.testhelpers.aws.sns.MockSNSWriterFactory;
import com.kdgregory.log4j.testhelpers.aws.sns.TestableSNSAppender;


/**
 *  These tests exercise the interaction between the appender and CloudWatch, via
 *  an actual writer. To do that, they mock out the CloudWatch client. Most of
 *  these tests spin up an actual writer thread, so must coordinate interaction
 *  between that thread and the test (main) thread.
 */
public class TestSNSLogWriter
{
    // the same topic is used for all tests other than substitutions
    private final static String EXPECTED_NAME = "example";
    private final static String EXPECTED_ARN = "arn:aws:sns:us-east-1:123456789012:example";


    private Logger logger;
    private TestableSNSAppender appender;


    private void initialize(String propsName)
    throws Exception
    {
        URL config = ClassLoader.getSystemResource(propsName);
        assertNotNull("was able to retrieve config", config);
        PropertyConfigurator.configure(config);

        logger = Logger.getLogger(getClass());

        Logger rootLogger = Logger.getRootLogger();
        appender = (TestableSNSAppender)rootLogger.getAppender("default");

        appender.setThreadFactory(new InlineThreadFactory());
        appender.setWriterFactory(new MockSNSWriterFactory());
    }


    /**
     *  A spin loop that waits for an writer running in another thread to
     *  finish initialization, either successfully or with error.
     */
    private void waitForInitialization() throws Exception
    {
        for (int ii = 0 ; ii < 50 ; ii++)
        {
            AbstractLogWriter writer = (AbstractLogWriter)appender.getWriter();
            if ((writer != null) && writer.isInitializationComplete())
                return;
            else if (appender.getAppenderStatistics().getLastErrorMessage() != null)
                return;
            else
                Thread.sleep(100);
        }
        fail("timed out waiting for initialization");
    }


    // the following variable and function are used by testStaticClientFactory

    private static MockSNSClient staticFactoryMock = null;

    public static AmazonSNS createMockClient()
    {
        staticFactoryMock = new MockSNSClient("example", Arrays.asList("argle", "example", "bargle"));
        return staticFactoryMock.createClient();
    }

//----------------------------------------------------------------------------
//  JUnit stuff
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        LogManager.resetConfiguration();
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
    public void testWriterOperationByName() throws Exception
    {
        initialize("TestSNSAppender/testWriterOperationByName.properties");

        MockSNSClient mockClient = new MockSNSClient("example", Arrays.asList("argle", "example", "bargle"));
        appender.setWriterFactory(mockClient.newWriterFactory());
        appender.setThreadFactory(new DefaultThreadFactory());

        logger.info("message one");
        mockClient.allowWriterThread();

        logger.info("message two");
        mockClient.allowWriterThread();

        assertNull("no initialization error",                               appender.getAppenderStatistics().getLastError());
        assertEquals("invocations of listTopics",       1,                  mockClient.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",      0,                  mockClient.createTopicInvocationCount);
        assertEquals("invocations of publish",          2,                  mockClient.publishInvocationCount);

        assertEquals("last message published, arn",     EXPECTED_ARN,       mockClient.lastPublishArn);
        assertEquals("last message published, subject", null,               mockClient.lastPublishSubject);
        assertEquals("last message published, body",    "message two",      mockClient.lastPublishMessage);

        assertEquals("topic name, from statistics",         EXPECTED_NAME,  appender.getAppenderStatistics().getActualTopicName());
        assertEquals("topic ARN, from statistics",          EXPECTED_ARN,   appender.getAppenderStatistics().getActualTopicArn());
        assertEquals("sent message count, from statistics", 2,              appender.getAppenderStatistics().getMessagesSent());
    }


    @Test
    public void testWriterOperationByNameMultipleTopicLists() throws Exception
    {
        initialize("TestSNSAppender/testWriterOperationByName.properties");

        MockSNSClient mockClient = new MockSNSClient("example", Arrays.asList("argle", "example", "bargle"), 2);
        appender.setWriterFactory(mockClient.newWriterFactory());
        appender.setThreadFactory(new DefaultThreadFactory());

        logger.info("message one");
        mockClient.allowWriterThread();

        assertNull("no initialization error",                           appender.getAppenderStatistics().getLastError());
        assertEquals("invocations of listTopics",       2,              mockClient.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",      0,              mockClient.createTopicInvocationCount);
        assertEquals("invocations of publish",          1,              mockClient.publishInvocationCount);

        assertEquals("last message published, arn",     EXPECTED_ARN,   mockClient.lastPublishArn);
        assertEquals("last message published, subject", null,           mockClient.lastPublishSubject);
        assertEquals("last message published, body",    "message one",  mockClient.lastPublishMessage);

        assertEquals("topic name, from statistics",         EXPECTED_NAME,  appender.getAppenderStatistics().getActualTopicName());
        assertEquals("topic ARN, from statistics",          EXPECTED_ARN,   appender.getAppenderStatistics().getActualTopicArn());
        assertEquals("sent message count, from statistics", 1,              appender.getAppenderStatistics().getMessagesSent());
    }


    @Test
    public void testWriterOperationByNameNoExistingTopic() throws Exception
    {
        initialize("TestSNSAppender/testWriterOperationByName.properties");

        MockSNSClient mockClient = new MockSNSClient("example", Arrays.asList("argle", "bargle"));
        appender.setWriterFactory(mockClient.newWriterFactory());
        appender.setThreadFactory(new DefaultThreadFactory());

        logger.info("message one");

        waitForInitialization();
        String initializationMessage = appender.getAppenderStatistics().getLastErrorMessage();

        assertTrue("initialization error mentions topic name (was: " + initializationMessage + ")",
                   initializationMessage.contains("example"));

        assertEquals("invocations of listTopics",           1,              mockClient.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",          0,              mockClient.createTopicInvocationCount);
        assertEquals("invocations of publish",              0,              mockClient.publishInvocationCount);


        assertEquals("topic name, from statistics",         null,           appender.getAppenderStatistics().getActualTopicName());
        assertEquals("topic ARN, from statistics",          null,           appender.getAppenderStatistics().getActualTopicArn());
        assertEquals("sent message count, from statistics", 0,              appender.getAppenderStatistics().getMessagesSent());
        assertEquals("sent message count, from statistics", 0,              appender.getAppenderStatistics().getMessagesSent());
    }


    @Test
    public void testWriterOperationByNameNoExistingTopicAutoCreate() throws Exception
    {
        initialize("TestSNSAppender/testWriterOperationByNameAutoCreate.properties");

        MockSNSClient mockClient = new MockSNSClient("example", Arrays.asList("argle", "bargle"));
        appender.setWriterFactory(mockClient.newWriterFactory());
        appender.setThreadFactory(new DefaultThreadFactory());

        logger.info("message one");
        mockClient.allowWriterThread();

        assertTrue("no initialization error message",                       StringUtil.isEmpty(appender.getAppenderStatistics().getLastErrorMessage()));
        assertEquals("invocations of listTopics",           1,              mockClient.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",          1,              mockClient.createTopicInvocationCount);
        assertEquals("invocations of publish",              1,              mockClient.publishInvocationCount);

        assertEquals("last message published, arn",         EXPECTED_ARN,   mockClient.lastPublishArn);
        assertEquals("last message published, subject",     null,           mockClient.lastPublishSubject);
        assertEquals("last message published, body",        "message one",  mockClient.lastPublishMessage);

        assertEquals("topic name, from statistics",         EXPECTED_NAME,  appender.getAppenderStatistics().getActualTopicName());
        assertEquals("topic ARN, from statistics",          EXPECTED_ARN,   appender.getAppenderStatistics().getActualTopicArn());
        assertEquals("sent message count, from statistics", 1,              appender.getAppenderStatistics().getMessagesSent());
    }


    @Test
    public void testWriterOperationByArn() throws Exception
    {
        initialize("TestSNSAppender/testWriterOperationByArn.properties");

        MockSNSClient mockClient = new MockSNSClient("example", Arrays.asList("argle", "example", "bargle"));
        appender.setWriterFactory(mockClient.newWriterFactory());
        appender.setThreadFactory(new DefaultThreadFactory());

        logger.info("message one");
        mockClient.allowWriterThread();

        logger.info("message two");
        mockClient.allowWriterThread();

        assertNull("no initialization error",                           appender.getAppenderStatistics().getLastError());
        assertEquals("invocations of listTopics",       1,              mockClient.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",      0,              mockClient.createTopicInvocationCount);
        assertEquals("invocations of publish",          2,              mockClient.publishInvocationCount);

        assertEquals("last message published, arn",     EXPECTED_ARN,   mockClient.lastPublishArn);
        assertEquals("last message published, subject", null,           mockClient.lastPublishSubject);
        assertEquals("last message published, body",    "message two",  mockClient.lastPublishMessage);

        assertEquals("topic name, from statistics",         EXPECTED_NAME,  appender.getAppenderStatistics().getActualTopicName());
        assertEquals("topic ARN, from statistics",          EXPECTED_ARN,   appender.getAppenderStatistics().getActualTopicArn());
        assertEquals("sent message count, from statistics", 2,              appender.getAppenderStatistics().getMessagesSent());
    }


    @Test
    public void testWriterOperationByArnMultipleTopicLists() throws Exception
    {
        initialize("TestSNSAppender/testWriterOperationByArn.properties");

        MockSNSClient mockClient = new MockSNSClient("example", Arrays.asList("argle", "example", "bargle"), 2);
        appender.setWriterFactory(mockClient.newWriterFactory());
        appender.setThreadFactory(new DefaultThreadFactory());

        logger.info("message one");
        mockClient.allowWriterThread();

        assertNull("no initialization error",                           appender.getAppenderStatistics().getLastError());
        assertEquals("invocations of listTopics",       2,              mockClient.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",      0,              mockClient.createTopicInvocationCount);
        assertEquals("invocations of publish",          1,              mockClient.publishInvocationCount);

        assertEquals("last message published, arn",     EXPECTED_ARN,   mockClient.lastPublishArn);
        assertEquals("last message published, subject", null,           mockClient.lastPublishSubject);
        assertEquals("last message published, body",    "message one",  mockClient.lastPublishMessage);

        assertEquals("topic name, from statistics",         EXPECTED_NAME,  appender.getAppenderStatistics().getActualTopicName());
        assertEquals("topic ARN, from statistics",          EXPECTED_ARN,   appender.getAppenderStatistics().getActualTopicArn());
        assertEquals("sent message count, from statistics", 1,              appender.getAppenderStatistics().getMessagesSent());
    }


    @Test
    public void testWriterOperationByArnWithNoExistingTopic() throws Exception
    {
        initialize("TestSNSAppender/testWriterOperationByArn.properties");

        MockSNSClient mockClient = new MockSNSClient("example", Arrays.asList("argle", "bargle"));
        appender.setWriterFactory(mockClient.newWriterFactory());
        appender.setThreadFactory(new DefaultThreadFactory());

        logger.info("message");

        waitForInitialization();
        String initializationMessage = appender.getAppenderStatistics().getLastErrorMessage();

        assertTrue("initialization error mentions topic name (was: " + initializationMessage + ")",
                   initializationMessage.contains("example"));

        assertEquals("invocations of listTopics",           1,          mockClient.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",          0,          mockClient.createTopicInvocationCount);
        assertEquals("invocations of publish",              0,          mockClient.publishInvocationCount);

        assertEquals("topic name, from statistics",         null,       appender.getAppenderStatistics().getActualTopicName());
        assertEquals("topic ARN, from statistics",          null,       appender.getAppenderStatistics().getActualTopicArn());
        assertEquals("sent message count, from statistics", 0,          appender.getAppenderStatistics().getMessagesSent());
    }


    @Test
    public void testWriterOperationWithSubject() throws Exception
    {
        initialize("TestSNSAppender/testWriterOperationWithSubject.properties");

        MockSNSClient mockClient = new MockSNSClient("example", Arrays.asList("argle", "example", "bargle"));
        appender.setWriterFactory(mockClient.newWriterFactory());
        appender.setThreadFactory(new DefaultThreadFactory());

        logger.info("message one");
        mockClient.allowWriterThread();

        assertNull("no initialization error",                           appender.getAppenderStatistics().getLastError());
        assertEquals("invocations of listTopics",       1,              mockClient.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",      0,              mockClient.createTopicInvocationCount);
        assertEquals("invocations of publish",          1,              mockClient.publishInvocationCount);

        assertEquals("last message published, arn",     EXPECTED_ARN,   mockClient.lastPublishArn);
        assertEquals("last message published, subject", "fribble-0",    mockClient.lastPublishSubject);
        assertEquals("last message published, body",    "message one",  mockClient.lastPublishMessage);
    }


    @Test
    public void testExceptionInInitializer() throws Exception
    {
        initialize("TestSNSAppender/testWriterOperationByNameAutoCreate.properties");

        MockSNSClient mockClient = new MockSNSClient("example", Arrays.asList("argle", "bargle"))
        {
            @Override
            protected CreateTopicResult createTopic(String name)
            {
                throw new TestingException("arbitrary failure");
            }
        };

        appender.setWriterFactory(mockClient.newWriterFactory());
        appender.setThreadFactory(new DefaultThreadFactory());

        // first message triggers writer creation

        logger.info("message one");

        waitForInitialization();
        String initializationMessage = appender.getAppenderStatistics().getLastErrorMessage();
        Throwable initializationError = appender.getAppenderStatistics().getLastError();

        AbstractLogWriter writer = (AbstractLogWriter)appender.getWriter();
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        assertNotNull("writer was retained",                                            writer);
        assertTrue("initialization message was non-blank",                              ! initializationMessage.equals(""));
        assertEquals("initialization exception retained",   TestingException.class,     initializationError.getClass());
        assertEquals("message queue set to discard all",    0,                          messageQueue.getDiscardThreshold());
        assertEquals("message queue set to discard all",    DiscardAction.oldest,       messageQueue.getDiscardAction());
        assertEquals("messages in queue (initial)",         1,                          messageQueue.toList().size());

        // trying to log another message should clear the queue

        logger.info("message two");
        assertEquals("messages in queue (second try)",      0,                          messageQueue.toList().size());
    }


    @Test
    public void testInvalidTopicName() throws Exception
    {
        initialize("TestSNSAppender/testInvalidTopicName.properties");

        MockSNSClient mockClient = new MockSNSClient("example", Arrays.asList("argle", "example", "bargle"));
        appender.setWriterFactory(mockClient.newWriterFactory());
        appender.setThreadFactory(new DefaultThreadFactory());

        // first message triggers writer creation

        logger.info("message one");

        waitForInitialization();
        String initializationMessage = appender.getAppenderStatistics().getLastErrorMessage();

        assertTrue("initialization message includes topic name (was: " + initializationMessage + ")",
                   initializationMessage.contains("x%$!"));

        assertEquals("invocations of listTopics",       0,              mockClient.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",      0,              mockClient.createTopicInvocationCount);
        assertEquals("invocations of publish",          0,              mockClient.publishInvocationCount);
    }


    @Test
    public void testBatchErrorHandling() throws Exception
    {
        initialize("TestSNSAppender/testBatchErrorHandling.properties");

        MockSNSClient mockClient = new MockSNSClient("example", Arrays.asList("argle", "example", "bargle"))
        {
            @Override
            protected PublishResult publish(PublishRequest request)
            {
                throw new TestingException("no notifications for you!");
            }
        };

        appender.setWriterFactory(mockClient.newWriterFactory());
        appender.setThreadFactory(new DefaultThreadFactory());

        logger.info("test message");
        mockClient.allowWriterThread();

        AbstractLogWriter writer = (AbstractLogWriter)appender.getWriter();
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);
        SNSAppenderStatistics appenderStats = appender.getAppenderStatistics();
        Throwable lastError = appenderStats.getLastError();

        assertEquals("number of calls to Publish",              1,                          mockClient.publishInvocationCount);
        assertNotNull("writer still exists",                                                writer);
        assertEquals("stats reports no messages sent",          0,                          appenderStats.getMessagesSent());
        assertTrue("error message was non-blank",                                           ! appenderStats.getLastErrorMessage().equals(""));
        assertEquals("exception retained",                      TestingException.class,     lastError.getClass());
        assertEquals("exception message",                       "no notifications for you!", lastError.getMessage());
        assertTrue("message queue still accepts messages",                                  messageQueue.getDiscardThreshold() > 0);

        // the background thread will try to assemble another batch right away, so we can't examine
        // the message queue; instead we'll wait for the writer to call Publish again

        mockClient.allowWriterThread();

        assertEquals("Publish called again",                 2,                             mockClient.publishInvocationCount);
    }


    @Test
    public void testDiscardOldest() throws Exception
    {
        initialize("TestSNSAppender/testDiscardOldest.properties");

        // this is a dummy client: never actually run the writer thread, but
        // need to test the real writer
        MockSNSClient mockClient = new MockSNSClient("example", Arrays.asList("example"))
        {
            @Override
            protected PublishResult publish(PublishRequest request)
            {
                throw new TestingException("this isn't going to work");
            }
        };

        appender.setThreadFactory(new NullThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        for (int ii = 0 ; ii < 20 ; ii++)
        {
            logger.debug("message " + ii);
        }

        AbstractLogWriter writer = (AbstractLogWriter)appender.getWriter();
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);
        List<LogMessage> messages = messageQueue.toList();

        assertEquals("number of messages in queue", 10, messages.size());
        assertEquals("oldest message", "message 10\n", messages.get(0).getMessage());
        assertEquals("newest message", "message 19\n", messages.get(9).getMessage());
    }


    @Test
    public void testDiscardNewest() throws Exception
    {
        initialize("TestSNSAppender/testDiscardNewest.properties");

        // this is a dummy client: never actually run the writer thread, but
        // need to test the real writer
        MockSNSClient mockClient = new MockSNSClient("example", Arrays.asList("example"))
        {
            @Override
            protected PublishResult publish(PublishRequest request)
            {
                throw new TestingException("this isn't going to work");
            }
        };

        appender.setThreadFactory(new NullThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        for (int ii = 0 ; ii < 20 ; ii++)
        {
            logger.debug("message " + ii);
        }

        AbstractLogWriter writer = (AbstractLogWriter)appender.getWriter();
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);
        List<LogMessage> messages = messageQueue.toList();

        assertEquals("number of messages in queue", 10, messages.size());
        assertEquals("oldest message", "message 0\n", messages.get(0).getMessage());
        assertEquals("newest message", "message 9\n", messages.get(9).getMessage());
    }


    @Test
    public void testDiscardNone() throws Exception
    {
        initialize("TestSNSAppender/testDiscardNone.properties");

        // this is a dummy client: we never actually run the writer thread, but
        // need to test the real writer
        MockSNSClient mockClient = new MockSNSClient("example", Arrays.asList("example"))
        {
            @Override
            protected PublishResult publish(PublishRequest request)
            {
                throw new TestingException("this isn't going to work");
            }
        };

        appender.setThreadFactory(new NullThreadFactory());
        appender.setWriterFactory(mockClient.newWriterFactory());

        for (int ii = 0 ; ii < 20 ; ii++)
        {
            logger.debug("message " + ii);
        }

        AbstractLogWriter writer = (AbstractLogWriter)appender.getWriter();
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);
        List<LogMessage> messages = messageQueue.toList();

        assertEquals("number of messages in queue", 20, messages.size());
        assertEquals("oldest message", "message 0\n", messages.get(0).getMessage());
        assertEquals("newest message", "message 19\n", messages.get(19).getMessage());
    }


    @Test
    public void testReconfigureDiscardProperties() throws Exception
    {
        initialize("TestSNSAppender/testReconfigureDiscardProperties.properties");

        // another test where we don't actually do anything but need to verify actual writer

        appender.setThreadFactory(new NullThreadFactory());
        appender.setWriterFactory(new MockSNSClient("example", Arrays.asList("example")).newWriterFactory());

        logger.debug("trigger writer creation");

        AbstractLogWriter writer = (AbstractLogWriter)appender.getWriter();
        MessageQueue messageQueue = ClassUtil.getFieldValue(writer, "messageQueue", MessageQueue.class);

        assertEquals("initial discard threshold, from appender",    12345,                              appender.getDiscardThreshold());
        assertEquals("initial discard action, from appender",       DiscardAction.newest.toString(),    appender.getDiscardAction());

        assertEquals("initial discard threshold, from queue",       12345,                              messageQueue.getDiscardThreshold());
        assertEquals("initial discard action, from queue",          DiscardAction.newest.toString(),    messageQueue.getDiscardAction().toString());

        appender.setDiscardThreshold(54321);
        appender.setDiscardAction(DiscardAction.oldest.toString());

        assertEquals("updated discard threshold, from appender",    54321,                              appender.getDiscardThreshold());
        assertEquals("updated discard action, from appender",       DiscardAction.oldest.toString(),    appender.getDiscardAction());

        assertEquals("updated discard threshold, from queue",       54321,                              messageQueue.getDiscardThreshold());
        assertEquals("updated discard action, from queue",          DiscardAction.oldest.toString(),    messageQueue.getDiscardAction().toString());
    }


    @Test
    public void testStaticClientFactory() throws Exception
    {
        initialize("TestSNSAppender/testStaticClientFactory.properties");

        appender.setThreadFactory(new DefaultThreadFactory());
        appender.setWriterFactory(new SNSWriterFactory());

        logger.info("message one");
        waitForInitialization();

        AbstractLogWriter writer = (AbstractLogWriter)appender.getWriter();

        assertNotNull("factory was called to create client",    staticFactoryMock);
        assertNull("no initialization message",                 appender.getAppenderStatistics().getLastErrorMessage());
        assertNull("no initialization error",                   appender.getAppenderStatistics().getLastError());
        assertEquals("called explicit client factory",          "com.kdgregory.log4j.aws.TestSNSLogWriter.createMockClient",
                                                                writer.getClientFactoryUsed());

        // this should be a sufficient assertion, but we'll go on and let the message get written

        staticFactoryMock.allowWriterThread();

        assertEquals("invocations of listTopics",       1,              staticFactoryMock.listTopicsInvocationCount);
        assertEquals("invocations of createTopic",      0,              staticFactoryMock.createTopicInvocationCount);
        assertEquals("invocations of publish",          1,              staticFactoryMock.publishInvocationCount);

        assertEquals("last message published, arn",     EXPECTED_ARN,   staticFactoryMock.lastPublishArn);
        assertEquals("last message published, subject", null,           staticFactoryMock.lastPublishSubject);
        assertEquals("last message published, body",    "message one",  staticFactoryMock.lastPublishMessage);
    }
}
