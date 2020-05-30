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

package com.kdgregory.logging.test;

import static net.sf.kdgcommons.test.StringAsserts.*;

import java.util.List;

import static org.junit.Assert.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.kdgcommons.lang.ClassUtil;

import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;

import com.kdgregory.logging.aws.sns.SNSLogWriter;
import com.kdgregory.logging.aws.sns.SNSWriterStatistics;
import com.kdgregory.logging.testhelpers.CommonTestHelper;
import com.kdgregory.logging.testhelpers.MessageWriter;
import com.kdgregory.logging.testhelpers.SNSTestHelper;


public abstract class AbstractSNSAppenderIntegrationTest
{
    // these clients are shared by all tests
    protected static AmazonSNS helperSNSclient;
    protected static AmazonSQS helperSQSclient;

    // this one is used solely by the static factory test
    // (which doesn't need an SQS queue because we don't send anything)
    protected static AmazonSNS factoryClient;

    // these are for the alternate region test
    protected AmazonSNS altSNSclient;
    protected AmazonSQS altSQSclient;

    protected SNSTestHelper testHelper;

    // initialized here, and again by init() after the logging framework has been initialized
    protected Logger localLogger = LoggerFactory.getLogger(getClass());


//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Subclasses must implement this to give the common tests access to the
     *  logger components.
     */
    public interface LoggerAccessor
    {
        /**
         *  Creates a new Messagewriter that will log to the tested appender.
         */
        MessageWriter newMessageWriter(int numMessages);

        /**
         *  Retrieves the current writer from the tested appender.
         */
        SNSLogWriter getWriter() throws Exception;

        /**
         *  Returns the statistics object associated with the tested appender.
         */
        SNSWriterStatistics getStats();

        /**
         *  Waits until the tested appender's writer has been initialized.
         */
        String waitUntilWriterInitialized() throws Exception;
    }


    /**
     *  Called by writer in testFactoryMethod().
     */
    public static AmazonSNS createClient()
    {
        factoryClient = AmazonSNSClientBuilder.defaultClient();
        return factoryClient;
    }


//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    protected static void beforeClass()
    {
        helperSNSclient = AmazonSNSClientBuilder.defaultClient();
        helperSQSclient = AmazonSQSClientBuilder.defaultClient();
    }


    public void tearDown()
    {
        // this is a static variable but set by a single test, so is cleared likewise
        if (factoryClient != null)
        {
            factoryClient.shutdown();
            factoryClient = null;
        }

        if (altSNSclient != null)
        {
            altSNSclient.shutdown();
            altSNSclient = null;
        }

        if (altSQSclient != null)
        {
            altSQSclient.shutdown();
            altSQSclient = null;
        }

        localLogger.info("finished");
    }


    public static void afterClass()
    {
        if (helperSNSclient != null)
        {
            helperSNSclient.shutdown();
        }
        if (helperSQSclient != null)
        {
            helperSQSclient.shutdown();
        }
    }

//----------------------------------------------------------------------------
//  Test Bodies
//----------------------------------------------------------------------------

    protected void smoketestByArn(LoggerAccessor accessor)
    throws Exception
    {
        final int numMessages = 11;

        localLogger.info("writing messages");
        accessor.newMessageWriter(numMessages).run();

        localLogger.info("reading messages");
        List<String> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages);

        assertEquals("actual topic name, from statistics",  testHelper.getTopicName(),      accessor.getStats().getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   testHelper.getTopicARN(),       accessor.getStats().getActualTopicArn());
        assertEquals("messages written, from stats",        numMessages,                    accessor.getStats().getMessagesSent());

        assertNull("factory should not have been used to create client", factoryClient);
    }


    protected void smoketestByName(LoggerAccessor accessor)
    throws Exception
    {
        final int numMessages = 11;

        localLogger.info("writing messages");
        accessor.newMessageWriter(numMessages).run();

        localLogger.info("reading messages");
        List<String> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages, "Example");

        assertEquals("actual topic name, from statistics",  testHelper.getTopicName(),      accessor.getStats().getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   testHelper.getTopicARN(),       accessor.getStats().getActualTopicArn());
        assertEquals("messages written, from stats",        numMessages,                    accessor.getStats().getMessagesSent());

        assertNull("factory should not have been used to create client", factoryClient);
    }


    protected void testTopicMissingAutoCreate(LoggerAccessor accessor)
    throws Exception
    {
        final int numMessages = 11;

        localLogger.info("writing messages");
        accessor.newMessageWriter(numMessages).run();

        localLogger.info("waiting for writer initialization to finish");
        accessor.waitUntilWriterInitialized();

        assertNotEmpty("topic was created", testHelper.lookupTopic());

        assertEquals("actual topic name, from statistics",  testHelper.getTopicName(),      accessor.getStats().getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   testHelper.getTopicARN(),       accessor.getStats().getActualTopicArn());

        // no queue attached to this topic so we can't read messages directly

        CommonTestHelper.waitUntilMessagesSent(accessor.getStats(), numMessages, 30000);
    }


    protected void testTopicMissingNoAutoCreate(LoggerAccessor accessor)
    throws Exception
    {
        String errorMessage = accessor.waitUntilWriterInitialized();
        assertNotNull("writer was created", accessor.getWriter());
        assertNotEmpty("writer initialization failed", errorMessage);
        assertTrue("error message contains topic name (was: " + errorMessage + ")", errorMessage.contains(testHelper.getTopicName()));

        assertNull("topic was not created", testHelper.lookupTopic());

        assertEquals("actual topic name, from statistics",  testHelper.getTopicName(),      accessor.getStats().getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   testHelper.getTopicARN(),       accessor.getStats().getActualTopicArn());
        assertEquals("messages written, from stats",        0,                              accessor.getStats().getMessagesSent());
    }


    protected void testMultiThread(LoggerAccessor accessor)
    throws Exception
    {
        final int numMessages = 11;
        final int numThreads = 3;
        final int totalMessages = numMessages * numThreads;

        for (int ii = 0 ; ii < numThreads ; ii++)
        {
            new Thread(accessor.newMessageWriter(numMessages)).start();
        }

        localLogger.info("reading messages");
        List<String> messages = testHelper.retrieveMessages(totalMessages);

        assertEquals("number of messages", totalMessages, messages.size());
        testHelper.assertMessageContent(messages, "Example");

        assertEquals("actual topic name, from statistics",  testHelper.getTopicName(),      accessor.getStats().getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   testHelper.getTopicARN(),       accessor.getStats().getActualTopicArn());
        assertEquals("messages written, from stats",        totalMessages,                  accessor.getStats().getMessagesSent());
    }


    protected void testMultiAppender(LoggerAccessor accessor1, LoggerAccessor accessor2)
    throws Exception
    {
        final int numMessages = 11;
        final int numAppenders = 2;
        final int totalMessages = numMessages * numAppenders;

        // same logger regardless of which info object we use
        accessor1.newMessageWriter(numMessages).run();

        localLogger.info("reading messages");
        List<String> messages = testHelper.retrieveMessages(totalMessages);

        assertEquals("number of messages", totalMessages, messages.size());
        testHelper.assertMessageContent(messages, "Example1", "Example2");

        assertEquals("actual topic name, appender1, from statistics",   testHelper.getTopicName(),      accessor1.getStats().getActualTopicName());
        assertEquals("actual topic ARN, appender1, from statistics",    testHelper.getTopicARN(),       accessor1.getStats().getActualTopicArn());
        assertEquals("messages written, appender1, from stats",         numMessages,                    accessor1.getStats().getMessagesSent());

        assertEquals("actual topic name, appender2, from statistics",   testHelper.getTopicName(),      accessor2.getStats().getActualTopicName());
        assertEquals("actual topic ARN, appender2, from statistics",    testHelper.getTopicARN(),       accessor2.getStats().getActualTopicArn());
        assertEquals("messages written, appender2, from stats",         numMessages,                    accessor2.getStats().getMessagesSent());
    }


    protected void testFactoryMethod(LoggerAccessor accessor)
    throws Exception
    {
        final int numMessages = 11;

        localLogger.info("writing messages");
        accessor.newMessageWriter(numMessages).run();

        localLogger.info("reading messages");
        List<String> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages);

        SNSLogWriter writer = accessor.getWriter();
        AmazonSNS actualClient = ClassUtil.getFieldValue(writer, "client", AmazonSNS.class);
        assertSame("factory should have been used to create client", factoryClient, actualClient);
    }


    protected void testAlternateRegion(LoggerAccessor accessor)
    throws Exception
    {
        final int numMessages = 11;

        // BEWARE: my default region is us-east-1, so I use us-east-2 as the alternate
        //         if that is your default, then the test will fail

        altSNSclient = AmazonSNSClientBuilder.standard().withRegion("us-east-2").build();
        altSQSclient = AmazonSQSClientBuilder.standard().withRegion("us-east-2").build();
        SNSTestHelper altTestHelper = new SNSTestHelper(testHelper, altSNSclient, altSQSclient);

        localLogger.info("writing messages");
        accessor.newMessageWriter(numMessages).run();

        // no queue attached to this topic so we can't read messages directly
        CommonTestHelper.waitUntilMessagesSent(accessor.getStats(), numMessages, 30000);

        assertNotEmpty("topic was created",                  altTestHelper.lookupTopic());
        assertNull("topic does not exist in default region", testHelper.lookupTopic());

        assertEquals("actual topic name, from statistics",  altTestHelper.getTopicName(),      accessor.getStats().getActualTopicName());
        assertEquals("actual topic ARN, from statistics",   altTestHelper.getTopicARN(),       accessor.getStats().getActualTopicArn());
    }


    protected void testAssumedRole(LoggerAccessor accessor)
    throws Exception
    {
        final int numMessages = 11;

        localLogger.info("writing messages");
        accessor.newMessageWriter(numMessages).run();

        localLogger.info("reading messages");
        List<String> messages = testHelper.retrieveMessages(numMessages);

        assertEquals("number of messages", numMessages, messages.size());
        testHelper.assertMessageContent(messages);

        assertEquals("credentials provider",
                     STSAssumeRoleSessionCredentialsProvider.class,
                     CommonTestHelper.getCredentialsProviderClass(accessor.getWriter())
                     );
    }
}
