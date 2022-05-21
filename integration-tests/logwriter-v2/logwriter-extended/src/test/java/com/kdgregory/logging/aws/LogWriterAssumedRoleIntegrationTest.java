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

import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.iam.model.Role;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

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
//       and, unlike the v1 version of this test, it's not that easy to dig the credentials provider
//       out of the test; so this test acts to exercise the functionality, and then I look at the
//       available information after the test has completed

public class LogWriterAssumedRoleIntegrationTest
{
    // one assumable role that is used for all tests
    private static String roleName = "TestLogWriterAssumedRole";
    private static RoleTestHelper roleHelper;
    private static Role role;

    // this is for external logging within the tests
    private static Logger localLogger = LoggerFactory.getLogger(LogWriterAssumedRoleIntegrationTest.class);

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
        roleHelper.waitUntilRoleAssumable(role.arn(), 60);
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

        CloudWatchLogsClient helperClient = null;
        CloudWatchTestHelper testHelper = null;
        CloudWatchLogWriter writer = null;
        try
        {
            helperClient = CloudWatchLogsClient.builder().build();
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

            new LogWriterMessageWriter(writer, numMessages).run();
            CommonTestHelper.waitUntilMessagesSent(stats, numMessages, 30000);
            testHelper.assertMessages(testName, numMessages);

            // the v2 client configuration is managed as a map of attributes, and it's unclear to me
            // whether I can cleanly dig into it to find the assumed role credential provider; so I
            // punted and check CloudTrail after running this test

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
                helperClient.close();
            }
        }
    }


    @Test
    public void testKinesisWriter() throws Exception
    {
        init("testKinesisWriter");

        final int numMessages = 1001;

        KinesisClient helperClient = null;
        KinesisTestHelper testHelper = null;
        KinesisLogWriter writer = null;
        try
        {
            helperClient = KinesisClient.builder().build();
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
                helperClient.close();
            }
        }
    }


    @Test
    public void testSNSWriter() throws Exception
    {
        init("testSNSWriter");

        final int numMessages = 11;

        SnsClient helperSNSclient = null;
        SqsClient helperSQSclient = null;
        SNSTestHelper testHelper = null;
        SNSLogWriter writer = null;
        try
        {
            helperSNSclient = SnsClient.builder().build();
            helperSQSclient = SqsClient.builder().build();
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
                helperSNSclient.close();
            }
            if (helperSQSclient != null)
            {
                helperSQSclient.close();
            }
        }
    }
}
