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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import net.sf.kdgcommons.lang.ClassUtil;

import org.slf4j.Logger;

import com.amazonaws.regions.Regions;
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
    // "helper" clients are shared by all tests
    private static AmazonSNSClient helperSNSclient;
    private static AmazonSQSClient helperSQSclient;

    // these are created by the "alternate region" tests
    private AmazonSNSClient altSNSclient;
    private AmazonSQSClient altSQSclient;

    // this client is used in testFactoryMethod(), should be null everywhere else
    private static AmazonSNSClient factoryClient;

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
//  Helpers
//----------------------------------------------------------------------------

    private void init(String testName, AmazonSNS snsClient, AmazonSQS sqsClient, String factoryMethod, String region, String endpoint)
    throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        testHelper = new SNSTestHelper(snsClient, sqsClient);

        testHelper.createTopicAndQueue();

        stats = new SNSWriterStatistics();
        internalLogger = new TestableInternalLogger();
        config = new SNSWriterConfig(testHelper.getTopicName(), null, "integration test", true, 10000, DiscardAction.oldest, factoryMethod, region, endpoint);
        factory = new SNSWriterFactory();
        writer = (SNSLogWriter)factory.newLogWriter(config, stats, internalLogger);

        new DefaultThreadFactory("test").startLoggingThread(writer, false, null);
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


    public static AmazonSNSClient staticClientFactory()
    {
        factoryClient = new AmazonSNSClient();
        return factoryClient;
    }

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    {
        // constructor because we're running against 1.11.0
        helperSNSclient = new AmazonSNSClient();
        helperSQSclient = new AmazonSQSClient();
    }

    @After
    public void tearDown()
    {
        if (writer != null)
        {
            writer.stop();
        }

        if (altSNSclient != null)
        {
            altSNSclient.shutdown();
        }

        if (altSQSclient != null)
        {
            altSQSclient.shutdown();
        }

        if (factoryClient != null)
        {
            factoryClient.shutdown();
            factoryClient = null;
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
    public void smoketest() throws Exception
    {
        final int numMessages = 11;

        init("smoketest", helperSNSclient, helperSQSclient, null, null, null);

        new MessageWriter(numMessages).run();

        List<String> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages, "integration test");
    }


    @Test
    public void testFactoryMethod() throws Exception
    {
        final int numMessages = 11;

        init("testFactoryMethod", helperSNSclient, helperSQSclient,  getClass().getName() + ".staticClientFactory", null, null);

        new MessageWriter(numMessages).run();

        List<String> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages, "integration test");

        assertNotNull("factory method was called", factoryClient);
        assertSame("factory-created client used by writer", factoryClient, ClassUtil.getFieldValue(writer, "client", AmazonSNS.class));
    }


    @Test
    public void testAlternateRegion() throws Exception
    {
        final int numMessages = 11;

        // default region for constructor is always us-east-1
        altSNSclient = new AmazonSNSClient().withRegion(Regions.US_WEST_1);
        altSQSclient = new AmazonSQSClient().withRegion(Regions.US_WEST_1);

        init("testAlternateRegion", altSNSclient, altSQSclient, null, "us-west-1", null);

        new MessageWriter(numMessages).run();

        List<String> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages, "integration test");

        assertNull("topic does not exist in default region",
                   (new SNSTestHelper(testHelper, helperSNSclient, helperSQSclient)).lookupTopic());
    }


    @Test
    public void testAlternateEndpoint() throws Exception
    {
        final int numMessages = 11;

        // the goal here is to verify that we can use a region that didn't exist when 1.11.0 came out
        // BEWARE: my default region is us-east-1, so I use us-east-2 as the alternate
        //         if that is your default, then the test will fail
        altSNSclient = new AmazonSNSClient().withEndpoint("sns.us-east-2.amazonaws.com");
        altSQSclient = new AmazonSQSClient().withEndpoint("sqs.us-east-2.amazonaws.com");

        init("testAlternateEndpoint", altSNSclient, altSQSclient, null, null, "sns.us-east-2.amazonaws.com");

        new MessageWriter(numMessages).run();

        List<String> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages, "integration test");

        assertNull("topic does not exist in default region",
                   (new SNSTestHelper(testHelper, helperSNSclient, helperSQSclient)).lookupTopic());
    }
}
