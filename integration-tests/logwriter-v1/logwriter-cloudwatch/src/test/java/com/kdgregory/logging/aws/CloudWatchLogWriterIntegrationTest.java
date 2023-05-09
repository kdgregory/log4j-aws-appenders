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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.lang.StringUtil;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.logs.model.OutputLogEvent;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchConstants;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterFactory;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.aws.facade.CloudWatchFacade;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.util.DefaultThreadFactory;
import com.kdgregory.logging.common.util.MessageQueue.DiscardAction;
import com.kdgregory.logging.testhelpers.CloudWatchTestHelper;
import com.kdgregory.logging.testhelpers.CommonTestHelper;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;


public class CloudWatchLogWriterIntegrationTest
{
    private final static String BASE_LOGGROUP_NAME = "CloudWatchLogWriterIntegrationTest";

    // single "helper" client that's shared by all tests
    private static AWSLogs helperClient;

    // this one is created by the "alternate region" tests
    private AWSLogs altClient;

    // this client is used in testFactoryMethod(), should be null everywhere else
    private static AWSLogs factoryClient;

    // this is for logging within the test
    private Logger localLogger = LoggerFactory.getLogger(getClass());

    // these are all assigned by init()
    private String logGroupName;
    private String logStreamName; // default, tests may ignore
    private CloudWatchTestHelper testHelper;
    private CloudWatchWriterStatistics stats;
    private TestableInternalLogger internalLogger;
    private CloudWatchWriterFactory writerFactory;
    private DefaultThreadFactory threadFactory;

    // tests that create multiple writers/threads will find them here
    private List<Thread> writerThreads = new ArrayList<Thread>();
    private List<CloudWatchLogWriter> writers = new ArrayList<CloudWatchLogWriter>();

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Called at the beginning of most tests to create the test helper and
     *  writer, and delete any previous log group.
     */
    private void init(String testName, AWSLogs client)
    throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        testHelper = new CloudWatchTestHelper(client, BASE_LOGGROUP_NAME, testName);
        testHelper.deleteLogGroupIfExists();

        logGroupName = testHelper.getLogGroupName();
        logStreamName = testName;

        stats = new CloudWatchWriterStatistics();
        internalLogger = new TestableInternalLogger();
        writerFactory = new CloudWatchWriterFactory();

        threadFactory = new DefaultThreadFactory("test")
        {
            @Override
            protected Thread createThread(LogWriter logWriter, UncaughtExceptionHandler exceptionHandler)
            {
                Thread thread = super.createThread(logWriter, exceptionHandler);
                writerThreads.add(thread);
                return thread;
            }
        };
    }


    /**
     *  Returns a default writer config, which the test can then modify.
     */
    private CloudWatchWriterConfig defaultConfig()
    {
        return new CloudWatchWriterConfig()
               .setLogGroupName(logGroupName)
               .setLogStreamName(logStreamName)
               .setBatchDelay(250)
               .setDiscardThreshold(10000)
               .setDiscardAction(DiscardAction.oldest);
    }


    /**
     *  Creates the log writer and its execution thread, and starts it running.
     */
    private CloudWatchLogWriter createWriter(CloudWatchWriterConfig config)
    throws Exception
    {
        CloudWatchLogWriter writer = (CloudWatchLogWriter)writerFactory.newLogWriter(config, stats, internalLogger);
        writers.add(writer);
        threadFactory.startWriterThread(writer, null);
        return writer;
    }


    private class MessageWriter
    extends com.kdgregory.logging.testhelpers.MessageWriter
    {
        private CloudWatchLogWriter writer;

        public MessageWriter(CloudWatchLogWriter writer, int numMessages)
        {
            super(numMessages);
            this.writer = writer;
        }

        @Override
        protected void writeLogMessage(String message)
        {
            writer.addMessage(new LogMessage(System.currentTimeMillis(), message));
        }
    }


    public static AWSLogs staticClientFactory()
    {
        factoryClient = AWSLogsClientBuilder.defaultClient();
        return factoryClient;
    }

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    {
        helperClient = AWSLogsClientBuilder.defaultClient();
    }


    @Before
    public void setUp()
    {
        factoryClient = null;
    }


    @After
    public void tearDown()
    {
        for (CloudWatchLogWriter writer : writers)
        {
            writer.stop();
        }

        if (altClient != null)
        {
            altClient.shutdown();
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
        helperClient.shutdown();
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void smoketest() throws Exception
    {
        final int numMessages = 1001;

        init("smoketest", helperClient);

        CloudWatchWriterConfig config = defaultConfig();
        CloudWatchLogWriter writer = createWriter(config);

        new MessageWriter(writer, numMessages).run();

        CommonTestHelper.waitUntilMessagesSent(stats, numMessages);
        testHelper.assertMessages(logStreamName, numMessages);

        assertNull("static factory method not called", factoryClient);

        assertNull("retention period not set", testHelper.describeLogGroup().getRetentionInDays());

        assertEquals("internal error log", Collections.emptyList(), internalLogger.errorMessages);

        testHelper.deleteLogGroupIfExists();
    }


    @Test
    public void testFactoryMethod() throws Exception
    {
        final int numMessages = 1001;

        init("testFactoryMethod", helperClient);

        CloudWatchWriterConfig config = defaultConfig();
        config.setClientFactoryMethod(getClass().getName() + ".staticClientFactory");
        CloudWatchLogWriter writer = createWriter(config);

        new MessageWriter(writer, numMessages).run();

        CommonTestHelper.waitUntilMessagesSent(stats, numMessages);
        testHelper.assertMessages(logStreamName, numMessages);

        assertNotNull("factory method was called", factoryClient);

        // this is getting a little obsessive...
        Object facade = ClassUtil.getFieldValue(writer, "facade", CloudWatchFacade.class);
        Object client = ClassUtil.getFieldValue(facade, "client", AWSLogs.class);
        assertSame("factory-created client used by writer", factoryClient, client);

        assertEquals("internal error log", Collections.emptyList(), internalLogger.errorMessages);

        testHelper.deleteLogGroupIfExists();
    }


    @Test
    public void testAlternateRegion() throws Exception
    {
        final int numMessages = 1001;

        altClient = AWSLogsClientBuilder.standard().withRegion(Regions.US_WEST_1).build();

        init("testAlternateRegion", altClient);

        CloudWatchWriterConfig config = defaultConfig();
        config.setClientRegion("us-west-1");
        CloudWatchLogWriter writer = createWriter(config);

        new MessageWriter(writer, numMessages).run();

        CommonTestHelper.waitUntilMessagesSent(stats, numMessages);
        testHelper.assertMessages(logStreamName, numMessages);

        assertFalse("stream does not exist in default region",
                    new CloudWatchTestHelper(helperClient, BASE_LOGGROUP_NAME, "testAlternateRegion")
                        .isLogStreamAvailable(logStreamName));

        assertEquals("internal error log", Collections.emptyList(), internalLogger.errorMessages);

        testHelper.deleteLogGroupIfExists();
    }


    @Test
    public void testAlternateEndpoint() throws Exception
    {
        final int numMessages = 1001;

        altClient = AWSLogsClientBuilder.standard().withRegion(Regions.US_EAST_2).build();

        init("testAlternateEndpoint", altClient);

        CloudWatchWriterConfig config = defaultConfig();
        config.setClientEndpoint("logs.us-east-2.amazonaws.com");
        CloudWatchLogWriter writer = createWriter(config);

        new MessageWriter(writer, numMessages).run();

        CommonTestHelper.waitUntilMessagesSent(stats, numMessages);
        testHelper.assertMessages(logStreamName, numMessages);

        assertFalse("stream does not exist in default region",
                    new CloudWatchTestHelper(helperClient, BASE_LOGGROUP_NAME, "testAlternateEndpoint")
                        .isLogStreamAvailable(logStreamName));

        assertEquals("internal error log", Collections.emptyList(), internalLogger.errorMessages);

        testHelper.deleteLogGroupIfExists();
    }


    @Test
    public void testRetentionPeriod() throws Exception
    {
        init("testRetentionPeriod", helperClient);

        CloudWatchWriterConfig config = defaultConfig();
        config.setRetentionPeriod(3);
        CloudWatchLogWriter writer = createWriter(config);

        // will write a single message so that we have something to wait for
        new MessageWriter(writer, 1).run();
        CommonTestHelper.waitUntilMessagesSent(stats, 1);

        assertEquals("retention period", 3, testHelper.describeLogGroup().getRetentionInDays().intValue());

        assertEquals("internal error log", Collections.emptyList(), internalLogger.errorMessages);

        testHelper.deleteLogGroupIfExists();
    }


    @Test
    public void testDedicatedWriter() throws Exception
    {
        final int numWriters = 10;
        final int numReps = 50;

        init("testDedicatedWriter", helperClient);

        for (int ii = 0 ; ii < numWriters ; ii++)
        {
            localLogger.debug("creating writer {}", ii);
            CloudWatchWriterConfig config = defaultConfig();
            config.setLogStreamName("testDedicatedWriter-" + ii);
            config.setDedicatedWriter(true);
            createWriter(config);
        }

        for (int ii = 0 ; ii < numReps ; ii++)
        {
            localLogger.debug("writing message {}", ii);
            LogMessage message = new LogMessage(System.currentTimeMillis(), String.format("message %d", ii));
            for (CloudWatchLogWriter w : writers)
            {
                // the sleep is intended to ensure that the messages will end up
                // in separate batches; in practice, with the background threads
                // all runnable, it won't come back for three seconds or more
                w.addMessage(message);
                Thread.sleep(100);
            }
        }

        CommonTestHelper.waitUntilMessagesSent(stats, numWriters * numReps);

        assertEquals("internal error log", Collections.emptyList(), internalLogger.errorMessages);

        testHelper.deleteLogGroupIfExists();
    }


    @Test
    public void testOversizeMessageTruncation() throws Exception
    {
        final int numMessages = 10;

        // this test verifies that what I think is the maximum message size is acceptable to CloudWatch
        final int maxMessageSize = CloudWatchConstants.MAX_MESSAGE_SIZE;

        final String expectedMessage = StringUtil.repeat('X', maxMessageSize - 1) + "Y";
        final String messageToWrite = expectedMessage + "Z";

        init("testOversizeMessageTruncation", helperClient);

        CloudWatchWriterConfig config = defaultConfig();
        config.setTruncateOversizeMessages(true);
        CloudWatchLogWriter writer = createWriter(config);

        new MessageWriter(writer, numMessages)
        {
            @Override
            protected void writeLogMessage(String ignored)
            {
                super.writeLogMessage(messageToWrite);
            }
        }.run();

        CommonTestHelper.waitUntilMessagesSent(stats, numMessages);
        List<OutputLogEvent> logEvents = testHelper.retrieveAllMessages(logStreamName, numMessages);

        Set<String> messages = new HashSet<>();
        for (OutputLogEvent event : logEvents)
        {
            messages.add(event.getMessage());
        }

        assertEquals("all messages should be truncated to same value", 1, messages.size());
        assertEquals("message actually written",                       expectedMessage, messages.iterator().next());

        assertEquals("internal error log", Collections.emptyList(), internalLogger.errorMessages);

        testHelper.deleteLogGroupIfExists();
    }


    @Test
    // test for issue #180: will log exception immediately, then eventually time-out
    public void testExistingEmptyLogStream() throws Exception
    {
        final int numMessages = 1001;

        init("testExistingEmptyLogStream", helperClient);
        testHelper.createLogGroup();
        testHelper.createLogStream(logStreamName);

        CloudWatchWriterConfig config = defaultConfig();
        config.setLogStreamName(logStreamName);

        CloudWatchLogWriter writer = createWriter(config);

        new MessageWriter(writer, numMessages).run();

        CommonTestHelper.waitUntilMessagesSent(stats, numMessages);
        testHelper.assertMessages(logStreamName, numMessages);

        assertEquals("internal error log", Collections.emptyList(), internalLogger.errorMessages);

        testHelper.deleteLogGroupIfExists();
    }
}
