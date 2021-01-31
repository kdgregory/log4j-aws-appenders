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

package com.kdgregory.logging.aws;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import net.sf.kdgcommons.lang.ClassUtil;

import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.logs.model.OutputLogEvent;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;

import com.kdgregory.logging.aws.cloudwatch.*;
import com.kdgregory.logging.aws.kinesis.*;
import com.kdgregory.logging.aws.sns.*;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.util.DefaultThreadFactory;
import com.kdgregory.logging.common.util.MessageQueue.DiscardAction;
import com.kdgregory.logging.testhelpers.*;


// note: the only way to _really_ verify that we're using an assumed role is to look at CloudTrail
//       however, that information isn't for roughly 15 minutes and I don't want to wait that long
//       in the test (it would also require a lot of inference based on the available events)
//
//       as a work-around, I dig into the service client to verify that it's using an assumed-role
//       credentials provider ... not a foolproof test, but it backs up post-test observation

public class TestLogWriterAssumedRole
{
    // one assumable role that is used for all tests
    private static String roleName = "TestLogWriterAssumedRole";
    private static RoleTestHelper roleHelper;
    private static Role role;

    // this is for external logging within the tests
    private static Logger localLogger = LoggerFactory.getLogger(TestLogWriterAssumedRole.class);

    // this is for capturing logging within the writer
    private TestableInternalLogger internalLogger;

    // used in multiple places, set by init()
    private String testName;

    // records any uncaught exceptions
    List<Throwable> uncaughtExceptions = Collections.synchronizedList(new ArrayList<>());
    UncaughtExceptionHandler exceptionHandler = new UncaughtExceptionHandler()
    {
        @Override
        public void uncaughtException(Thread t, Throwable ex)
        {
            uncaughtExceptions.add(ex);
        }
    };

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Called at the beginning of all tests to perform common setup. Unlike
     *  other writer tests, does not create helpers or other clients.
     */
    @SuppressWarnings("hiding")
    private void init(String testName)
    throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        internalLogger = new TestableInternalLogger();
        this.testName = testName;
    }

//----------------------------------------------------------------------------
//  JUnit scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void initialize() throws Exception
    {
        roleHelper = new RoleTestHelper();
        role = roleHelper.createRole(roleName, "arn:aws:iam::aws:policy/CloudWatchLogsFullAccess",
                                               "arn:aws:iam::aws:policy/AmazonKinesisFullAccess",
                                               "arn:aws:iam::aws:policy/AmazonSNSFullAccess");
        roleHelper.waitUntilRoleAssumable(role.getArn(), 60);
    }


    @AfterClass
    public static void shutdown() throws Exception
    {
        if (roleHelper != null)
        {
            roleHelper.deleteRole("TestLogWriterAssumedRole");
            roleHelper.shutdown();
        }
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testCloudWatchWriter() throws Exception
    {
        init("testCloudWatchWriter");

        final int numMessages = 1001;

        AWSLogs helperClient = null;
        CloudWatchTestHelper testHelper = null;
        CloudWatchLogWriter writer = null;
        try
        {
            helperClient = AWSLogsClientBuilder.defaultClient();
            testHelper = new CloudWatchTestHelper(helperClient, "TestLogWriterAssumedRole", testName);
            testHelper.deleteLogGroupIfExists();

            CloudWatchWriterConfig config = new CloudWatchWriterConfig()
                                   .setLogGroupName(testHelper.getLogGroupName())
                                   .setLogStreamName(testName)
                                   .setBatchDelay(250)
                                   .setDiscardThreshold(10000)
                                   .setDiscardAction(DiscardAction.oldest)
                                   .setAssumedRole(roleName);

            CloudWatchWriterStatistics stats = new CloudWatchWriterStatistics();

            writer = (CloudWatchLogWriter)new CloudWatchWriterFactory().newLogWriter(config, stats, internalLogger);
            new DefaultThreadFactory(testName).startWriterThread(writer, exceptionHandler);

            for (int ii = 0 ; ii < numMessages ; ii++)
            {
                writer.addMessage(new LogMessage(System.currentTimeMillis(), "message " + ii));
            }

            CommonTestHelper.waitUntilMessagesSent(stats, numMessages, 30000);

            // we don't care about message content, just that we actually wrote something
            List<OutputLogEvent> messages = testHelper.retrieveAllMessages(testName, numMessages);
            assertTrue("wrote messages", messages.size() > 0);

            // what we really care about is how the writer was configured
            Object facade = ClassUtil.getFieldValue(writer, "facade", Object.class);
            Object client = ClassUtil.getFieldValue(facade, "client", Object.class);
            Object credentialsProvider = ClassUtil.getFieldValue(client, "awsCredentialsProvider", Object.class);

            assertEquals("credentials provider class", STSAssumeRoleSessionCredentialsProvider.class, credentialsProvider.getClass());

            assertEquals("internal error log", Collections.emptyList(), internalLogger.errorMessages);
        }
        finally
        {
            if (writer != null)
            {
                writer.stop();
            }
            if (testHelper != null)
            {
                testHelper.deleteLogGroupIfExists();
            }
            if (helperClient != null)
            {
                helperClient.shutdown();
            }
        }
    }


    @Test
    public void testKinesisWriter() throws Exception
    {
        init("testKinesisWriter");

        final int numMessages = 1001;

        AmazonKinesis helperClient = null;
        KinesisTestHelper testHelper = null;
        KinesisLogWriter writer = null;
        try
        {
            helperClient = AmazonKinesisClientBuilder.defaultClient();
            testHelper = new KinesisTestHelper(helperClient, testName);
            testHelper.deleteStreamIfExists();

            KinesisWriterConfig config = new KinesisWriterConfig()
                                   .setStreamName(testHelper.getStreamName())
                                   .setPartitionKey("irrelevant")
                                   .setAutoCreate(true)
                                   .setShardCount(1)
                                   .setBatchDelay(250)
                                   .setDiscardThreshold(10000)
                                   .setDiscardAction(DiscardAction.oldest)
                                   .setAssumedRole(roleName);

            KinesisWriterStatistics stats = new KinesisWriterStatistics();

            writer = (KinesisLogWriter)new KinesisWriterFactory().newLogWriter(config, stats, internalLogger);
            new DefaultThreadFactory(testName).startWriterThread(writer, exceptionHandler);

            for (int ii = 0 ; ii < numMessages ; ii++)
            {
                writer.addMessage(new LogMessage(System.currentTimeMillis(), "message " + ii));
            }

            CommonTestHelper.waitUntilMessagesSent(stats, numMessages, 30000);

            // we don't care about message content, just that we actually wrote something
            List<KinesisTestHelper.RetrievedRecord> messages = testHelper.retrieveAllMessages(numMessages);
            assertTrue("wrote messages", messages.size() > 0);

            // what we really care about is how the writer was configured
            Object facade = ClassUtil.getFieldValue(writer, "facade", Object.class);
            Object client = ClassUtil.getFieldValue(facade, "client", Object.class);
            Object credentialsProvider = ClassUtil.getFieldValue(client, "awsCredentialsProvider", Object.class);

            assertEquals("credentials provider class", STSAssumeRoleSessionCredentialsProvider.class, credentialsProvider.getClass());

            assertEquals("internal error log", Collections.emptyList(), internalLogger.errorMessages);
        }
        finally
        {
            if (writer != null)
            {
                writer.stop();
            }
            if (testHelper != null)
            {
                testHelper.deleteStreamIfExists();
            }
            if (helperClient != null)
            {
                helperClient.shutdown();
            }
        }
    }


    @Test
    public void testSNSWriter() throws Exception
    {
        init("testSNSWriter");

        final int numMessages = 11;

        AmazonSNS helperSNSclient = null;
        AmazonSQS helperSQSclient = null;
        SNSTestHelper testHelper = null;
        SNSLogWriter writer = null;
        try
        {
            helperSNSclient = AmazonSNSClientBuilder.defaultClient();
            helperSQSclient = AmazonSQSClientBuilder.defaultClient();
            testHelper = new SNSTestHelper(helperSNSclient, helperSQSclient);

            testHelper.deleteTopicAndQueue();   // out with the old (if it exists)
            testHelper.createTopicAndQueue();   // in with the new -- no auto-create, since we need the queue

            SNSWriterConfig config = new SNSWriterConfig()
                                     .setTopicName(testHelper.getTopicName())
                                     .setSubject("irrelevant")
                                     .setAutoCreate(true)
                                     .setDiscardThreshold(10000)
                                     .setDiscardAction(DiscardAction.oldest)
                                     .setAssumedRole(roleName);

            SNSWriterStatistics stats = new SNSWriterStatistics();

            writer = (SNSLogWriter)new SNSWriterFactory().newLogWriter(config, stats, internalLogger);
            new DefaultThreadFactory(testName).startWriterThread(writer, exceptionHandler);

            for (int ii = 0 ; ii < numMessages ; ii++)
            {
                writer.addMessage(new LogMessage(System.currentTimeMillis(), "message " + ii));
            }

            CommonTestHelper.waitUntilMessagesSent(stats, numMessages, 30000);

            // we don't care about message content, just that we actually wrote something
            List<Map<String,Object>> messages = testHelper.retrieveMessages(numMessages);
            assertTrue("wrote messages", messages.size() > 0);

            // what we really care about is how the writer was configured
            Object facade = ClassUtil.getFieldValue(writer, "facade", Object.class);
            Object client = ClassUtil.getFieldValue(facade, "client", Object.class);
            Object credentialsProvider = ClassUtil.getFieldValue(client, "awsCredentialsProvider", Object.class);

            assertEquals("credentials provider class", STSAssumeRoleSessionCredentialsProvider.class, credentialsProvider.getClass());

            assertEquals("internal error log", Collections.emptyList(), internalLogger.errorMessages);
        }
        finally
        {
            if (writer != null)
            {
                writer.stop();
            }
            if (testHelper != null)
            {
                testHelper.deleteTopicAndQueue();
            }
            if (helperSNSclient != null)
            {
                helperSNSclient.shutdown();
            }
            if (helperSQSclient != null)
            {
                helperSQSclient.shutdown();
            }
        }
    }
}
