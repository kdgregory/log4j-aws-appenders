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


import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import org.slf4j.MDC;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;

import com.kdgregory.logging.aws.sns.SNSLogWriter;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;
import com.kdgregory.logging.aws.sns.SNSWriterFactory;
import com.kdgregory.logging.aws.sns.SNSWriterStatistics;
import com.kdgregory.logging.common.util.MessageQueue.DiscardAction;
import com.kdgregory.logging.testhelpers.LogWriterMessageWriter;
import com.kdgregory.logging.testhelpers.SNSTestHelper;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;


/**
 *  Tests the SNS log-writer using a proxy for AWS connections.
 *
 *  This is intended to be run on an EC2 instance in a private subnet, with a
 *  proxy  server running in a public subnet.
 *  <p>
 *  You must set the COM_KDGREGORY_LOGGING_PROXY_URL  environment variable, and
 *  edit the static variables in {@link AbstractProxyIntegrationTest} to match.
 */
public class SNSLogWriterProxyIntegrationTest
extends AbstractProxyIntegrationTest
{
    // "helper" clients are shared by all tests
    private static AmazonSNS helperSNSclient;
    private static AmazonSQS helperSQSclient;

    // these are all assigned by init()
    private SNSTestHelper testHelper;
    private SNSWriterStatistics stats;
    private SNSWriterFactory factory;

    // hold onto this so it can be shut down at the end of the test
    private SNSLogWriter writer;

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    private final static String DEFAULT_SUBJECT = "integration test";


    private SNSWriterConfig init(String testName, AmazonSNS snsClient, AmazonSQS sqsClient)
    throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        testHelper = new SNSTestHelper(snsClient, sqsClient);

        testHelper.createTopicAndQueue();

        stats = new SNSWriterStatistics();
        internalLogger = new TestableInternalLogger();
        factory = new SNSWriterFactory();

        return new SNSWriterConfig()
               .setTopicName(testHelper.getTopicName())
               .setSubject(DEFAULT_SUBJECT)
               .setAutoCreate(true)
               .setDiscardThreshold(10000)
               .setDiscardAction(DiscardAction.oldest);
    }


    private void createWriter(SNSWriterConfig config)
    {
        writer = (SNSLogWriter)factory.newLogWriter(config, stats, internalLogger);
        threadFactory.startWriterThread(writer, null);
    }

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    {
        helperSNSclient = configureProxy(AmazonSNSClientBuilder.standard()).build();
        helperSQSclient = configureProxy(AmazonSQSClientBuilder.standard()).build();
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


    @AfterClass
    public static void afterClass()
    {
        helperSNSclient.shutdown();
        helperSQSclient.shutdown();
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testProxy() throws Exception
    {
        final int numMessages = 11;

        SNSWriterConfig config = init("testProxy", helperSNSclient, helperSQSclient);
        createWriter(config);

        new LogWriterMessageWriter(writer, numMessages).run();

        List<Map<String,Object>> messages = testHelper.retrieveMessages(numMessages);
        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages, DEFAULT_SUBJECT);

        assertEquals("internal error log", Collections.emptyList(), internalLogger.errorMessages);

        testHelper.deleteTopicAndQueue();
    }

}
