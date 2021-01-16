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

import org.slf4j.LoggerFactory;

import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.test.StringAsserts;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

import org.slf4j.Logger;

import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;

import com.kdgregory.logging.aws.internal.facade.KinesisFacade;
import com.kdgregory.logging.aws.kinesis.KinesisLogWriter;
import com.kdgregory.logging.aws.kinesis.KinesisWriterStatistics;
import com.kdgregory.logging.testhelpers.CommonTestHelper;
import com.kdgregory.logging.testhelpers.KinesisTestHelper;
import com.kdgregory.logging.testhelpers.MessageWriter;
import com.kdgregory.logging.testhelpers.KinesisTestHelper.RetrievedRecord;


public abstract class AbstractKinesisAppenderIntegrationTest
{
    // this client is shared by all tests
    protected static AmazonKinesis helperClient;

    // this one is used solely by the static factory test
    protected static AmazonKinesis factoryClient;

    // this one is used by the alternate region test
    protected static AmazonKinesis altClient;

    // initialized here, and again by init() after the logging framework has been initialized
    protected Logger localLogger = LoggerFactory.getLogger(getClass());

    protected KinesisTestHelper testHelper;


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
        KinesisLogWriter getWriter() throws Exception;

        /**
         *  Returns the statistics object associated with the tested appender.
         */
        KinesisWriterStatistics getStats();

        /**
         *  Waits until the tested appender's writer has been initialized.
         */
        String waitUntilWriterInitialized() throws Exception;
    }


    /**
     *  Called by writer in testFactoryMethod().
     */
    public static AmazonKinesis createClient()
    {
        factoryClient = AmazonKinesisClientBuilder.defaultClient();
        return factoryClient;
    }

//----------------------------------------------------------------------------
//  JUnit Scaffolding -- must be overridden by subclasses (I'm assuming that
//  JUnit doesn't go out of its way to find annotations on superclasses)
//----------------------------------------------------------------------------

    protected static void beforeClass()
    throws Exception
    {
        helperClient = AmazonKinesisClientBuilder.defaultClient();
    }


    public void tearDown()
    throws Exception
    {
        // set by single test, but easier to reset always (if needed)
        if (factoryClient != null)
        {
            factoryClient.shutdown();
            factoryClient = null;
        }

        // set by single test, but easier to reset always (if needed)
        if (altClient != null)
        {
            altClient.shutdown();
            altClient = null;
        }

        localLogger.info("finished");
    }


    public static void afterClass()
    throws Exception
    {
        if (helperClient != null)
        {
            helperClient.shutdown();
        }
    }

//----------------------------------------------------------------------------
//  Test Bodies
//----------------------------------------------------------------------------

    protected void smoketest(LoggerAccessor accessor)
    throws Exception
    {
        final int numMessages = 1001;

        accessor.newMessageWriter(numMessages).run();

        localLogger.info("reading messages");
        List<RetrievedRecord> messages = testHelper.retrieveAllMessages(numMessages);

        testHelper.assertMessages(messages, 1, numMessages);
        testHelper.assertPartitionKeys(messages, numMessages, "test");

        testHelper.assertShardCount(3);
        testHelper.assertRetentionPeriod(48);

        testHelper.assertStats(accessor.getStats(), numMessages);

        assertNull("factory should not have been used to create client", factoryClient);

        testHelper.deleteStreamIfExists();
    }


    protected void testMultipleThreadsSingleAppender(LoggerAccessor accessor)
    throws Exception
    {
        int messagesPerThread = 500;

        MessageWriter[] writers = new MessageWriter[]
        {
            accessor.newMessageWriter(messagesPerThread),
            accessor.newMessageWriter(messagesPerThread),
            accessor.newMessageWriter(messagesPerThread),
            accessor.newMessageWriter(messagesPerThread),
            accessor.newMessageWriter(messagesPerThread)
        };

        MessageWriter.runOnThreads(writers);
        int expectedMessages = writers.length * messagesPerThread;

        localLogger.info("reading messages");
        List<RetrievedRecord> messages = testHelper.retrieveAllMessages(expectedMessages);

        testHelper.assertMessages(messages, writers.length, messagesPerThread);
        testHelper.assertPartitionKeys(messages, messagesPerThread * writers.length, "test");

        Map<String,List<RetrievedRecord>> groupedByShard = testHelper.groupByShard(messages);
        assertEquals("all messages written to same shard", 1, groupedByShard.size());

        testHelper.assertShardCount(2);
        testHelper.assertRetentionPeriod(24);

        testHelper.deleteStreamIfExists();
    }


    protected void testMultipleThreadsMultipleAppendersDistinctPartitions(LoggerAccessor... accessors)
    throws Exception
    {
        int messagesPerThread = 500;

        MessageWriter[] writers = new MessageWriter[]
        {
            accessors[0].newMessageWriter(messagesPerThread),
            accessors[1].newMessageWriter(messagesPerThread),
            accessors[2].newMessageWriter(messagesPerThread),
            accessors[0].newMessageWriter(messagesPerThread),
            accessors[1].newMessageWriter(messagesPerThread),
            accessors[2].newMessageWriter(messagesPerThread),
        };

        MessageWriter.runOnThreads(writers);
        int expectedMessages = writers.length * messagesPerThread;

        localLogger.info("reading messages");
        List<RetrievedRecord> messages = testHelper.retrieveAllMessages(expectedMessages);

        testHelper.assertMessages(messages, writers.length, messagesPerThread);
        testHelper.assertPartitionKeys(messages, messagesPerThread * 2, "test1", "test2", "test3");

        Map<String,List<RetrievedRecord>> groupedByShard = testHelper.groupByShard(messages);
        assertEquals("messages written to multiple shards", 2, groupedByShard.size());

        testHelper.assertShardCount(2);
        testHelper.assertRetentionPeriod(24);

        testHelper.deleteStreamIfExists();
    }


    protected void testRandomPartitionKeys(LoggerAccessor accessor)
    throws Exception
    {
        final int numMessages = 250;

        accessor.newMessageWriter(numMessages).run();

        localLogger.info("reading messages");
        List<RetrievedRecord> messages = testHelper.retrieveAllMessages(numMessages);

        testHelper.assertShardCount(2);
        testHelper.assertMessages(messages, 1, numMessages);
        testHelper.assertRandomPartitionKeys(messages, numMessages);

        testHelper.deleteStreamIfExists();
    }


    protected void testFailsIfNoStreamPresent(LoggerAccessor accessor)
    throws Exception
    {
        final String streamName = "AppenderIntegrationTest-testFailsIfNoStreamPresent";
        final int numMessages = 1001;

        accessor.newMessageWriter(numMessages).run();

        localLogger.info("waiting for writer initialization to finish");
        String initializationMessage = accessor.waitUntilWriterInitialized();

        StringAsserts.assertRegex(
            "initialization message did not indicate missing stream (was \"" + initializationMessage + "\")",
            ".*stream.*" + streamName + ".* not exist .*",
            initializationMessage);

        testHelper.deleteStreamIfExists();
    }


    protected void testFactoryMethod(LoggerAccessor accessor)
    throws Exception
    {
        final int numMessages = 1001;

        localLogger.info("writing messages");
        accessor.newMessageWriter(numMessages).run();

        localLogger.info("reading messages");
        List<RetrievedRecord> messages = testHelper.retrieveAllMessages(numMessages);

        testHelper.assertMessages(messages, 1, numMessages);
        testHelper.assertPartitionKeys(messages, numMessages, "test");

        testHelper.assertStats(accessor.getStats(), numMessages);

        KinesisFacade facade = ClassUtil.getFieldValue(accessor.getWriter(), "facade", KinesisFacade.class);
        AmazonKinesis client = ClassUtil.getFieldValue(facade, "client", AmazonKinesis.class);
        assertSame("factory should have been used to create client", factoryClient, client);

        testHelper.deleteStreamIfExists();
    }


    protected void testAlternateRegion(LoggerAccessor accessor, KinesisTestHelper altTestHelper)
    throws Exception
    {
        final int numMessages = 1001;

        localLogger.info("writing messages");
        accessor.newMessageWriter(numMessages).run();

        localLogger.info("reading messages");
        List<RetrievedRecord> messages = altTestHelper.retrieveAllMessages(numMessages);

        testHelper.assertMessages(messages, 1, numMessages);
        assertNull("stream does not exist in default region", testHelper.describeStream());

        altTestHelper.deleteStreamIfExists();
    }


    protected void testAssumedRole(LoggerAccessor accessor)
    throws Exception
    {
        final int numMessages = 1001;

        localLogger.info("writing messages");
        accessor.newMessageWriter(numMessages).run();

        localLogger.info("reading messages");
        List<RetrievedRecord> messages = testHelper.retrieveAllMessages(numMessages);

        testHelper.assertMessages(messages, 1, numMessages);
        testHelper.assertPartitionKeys(messages, numMessages, "test");

        testHelper.assertStats(accessor.getStats(), numMessages);
        assertEquals("credentials provider",
                     STSAssumeRoleSessionCredentialsProvider.class,
                     CommonTestHelper.getCredentialsProviderClass(accessor.getWriter())
                     );

        testHelper.deleteStreamIfExists();
    }
}
