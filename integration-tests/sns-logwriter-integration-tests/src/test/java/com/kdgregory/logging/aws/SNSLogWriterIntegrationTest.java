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


import java.util.List;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Logger;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;

import com.kdgregory.logging.aws.sns.SNSLogWriter;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;
import com.kdgregory.logging.aws.sns.SNSWriterFactory;
import com.kdgregory.logging.aws.sns.SNSWriterStatistics;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.factories.DefaultThreadFactory;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.testhelpers.SNSTestHelper;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;


public class SNSLogWriterIntegrationTest
{
    // these clients are shared by all tests
    private static AmazonSNS snsClient;
    private static AmazonSQS sqsClient;

    // this is for logging within the test
    private Logger localLogger = LoggerFactory.getLogger(getClass());

    // these are all assigned by init()
    private SNSTestHelper testHelper;
    private TestableInternalLogger internalLogger;
    private SNSWriterStatistics stats;
    private SNSWriterConfig config;
    private SNSWriterFactory factory;
    private SNSLogWriter writer;


//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    {
        // constructor because we're running against 1.11.0
        snsClient = new AmazonSNSClient();
        sqsClient = new AmazonSQSClient();
    }

    @After
    public void tearDown()
    {
        if (writer != null)
        {
            writer.stop();
        }
        localLogger.info("finished");
        MDC.clear();
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void smoketest() throws Exception
    {
        final int numMessages = 11;

        init("logwriter-smoketest");

        new MessageWriter(numMessages).run();

        List<String> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages, "integration test");
    }

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    private void init(String testName)
    throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        testHelper = new SNSTestHelper(snsClient, sqsClient);

        testHelper.createTopicAndQueue();

        stats = new SNSWriterStatistics();
        internalLogger = new TestableInternalLogger();
        config = new SNSWriterConfig(testHelper.getTopicName(), null, true, "integration test", 10000, DiscardAction.oldest, null, null);
        factory = new SNSWriterFactory();
        writer = (SNSLogWriter)factory.newLogWriter(config, stats, internalLogger);

        new DefaultThreadFactory("test").startLoggingThread(writer, null);
    }


    private class MessageWriter
    extends com.kdgregory.logging.testhelpers.MessageWriter
    {
        public MessageWriter(int numMessages)
        {
            super(numMessages);
        }

        @Override
        protected void writeLogMessage(String message)
        {
            writer.addMessage(new LogMessage(System.currentTimeMillis(), message));
        }
    }
}
