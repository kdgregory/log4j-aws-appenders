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
import java.util.List;

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

import com.amazonaws.regions.Regions;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClient;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterFactory;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.factories.DefaultThreadFactory;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.testhelpers.CloudWatchTestHelper;
import com.kdgregory.logging.testhelpers.CommonTestHelper;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;


public class CloudWatchLogWriterIntegrationTest
{
    private final static String BASE_LOGGROUP_NAME = "CloudWatchLogWriterIntegrationTest";

    // single "helper" client that's shared by all tests
    private static AWSLogsClient helperClient;

    // this one is created by the "alternate region" tests
    private AWSLogsClient altClient;

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
        return new CloudWatchWriterConfig(logGroupName, logStreamName, null, false, false, 250, 10000, DiscardAction.oldest, null, null, null, null);
    }


    /**
     *  Creates the log writer and its execution thread, and starts it running.
     */
    private CloudWatchLogWriter createWriter(CloudWatchWriterConfig config)
    throws Exception
    {
        CloudWatchLogWriter writer = (CloudWatchLogWriter)writerFactory.newLogWriter(config, stats, internalLogger);
        writers.add(writer);
        threadFactory.startLoggingThread(writer, false, null);
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
        factoryClient = new AWSLogsClient();
        return factoryClient;
    }

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    {
        // constructor because we're running against 1.11.0
        helperClient = new AWSLogsClient();
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

        CommonTestHelper.waitUntilMessagesSent(stats, numMessages, 30000);
        testHelper.assertMessages(logStreamName, numMessages);

        assertNull("static factory method not called", factoryClient);

        assertNull("retention period not set", testHelper.describeLogGroup().getRetentionInDays());

        testHelper.deleteLogGroupIfExists();
    }


    @Test
    public void testFactoryMethod() throws Exception
    {
        final int numMessages = 1001;

        init("testFactoryMethod", helperClient);

        CloudWatchWriterConfig config = defaultConfig();
        config.clientFactoryMethod = getClass().getName() + ".staticClientFactory";
        CloudWatchLogWriter writer = createWriter(config);

        new MessageWriter(writer, numMessages).run();

        CommonTestHelper.waitUntilMessagesSent(stats, numMessages, 30000);
        testHelper.assertMessages(logStreamName, numMessages);

        assertNotNull("factory method was called", factoryClient);
        assertSame("factory-created client used by writer", factoryClient, ClassUtil.getFieldValue(writer, "client", AWSLogs.class));

        testHelper.deleteLogGroupIfExists();
    }


    @Test
    public void testAlternateRegion() throws Exception
    {
        final int numMessages = 1001;

        // default region for constructor is always us-east-1
        altClient = new AWSLogsClient().withRegion(Regions.US_WEST_1);

        init("testAlternateRegion", altClient);

        CloudWatchWriterConfig config = defaultConfig();
        config.clientRegion = "us-west-1";
        CloudWatchLogWriter writer = createWriter(config);

        new MessageWriter(writer, numMessages).run();

        CommonTestHelper.waitUntilMessagesSent(stats, numMessages, 30000);
        testHelper.assertMessages(logStreamName, numMessages);

        assertFalse("stream does not exist in default region",
                    new CloudWatchTestHelper(helperClient, BASE_LOGGROUP_NAME, "testAlternateRegion")
                        .isLogStreamAvailable(logStreamName));

        testHelper.deleteLogGroupIfExists();
    }


    @Test
    public void testAlternateEndpoint() throws Exception
    {
        final int numMessages = 1001;

        // the goal here is to verify that we can use a region that didn't exist when 1.11.0 came out
        // BEWARE: my default region is us-east-1, so I use us-east-2 as the alternate
        //         if that is your default, then the test will fail
        altClient = new AWSLogsClient().withEndpoint("logs.us-east-2.amazonaws.com");

        init("testAlternateEndpoint", altClient);

        CloudWatchWriterConfig config = defaultConfig();
        config.clientEndpoint = "logs.us-east-2.amazonaws.com";
        CloudWatchLogWriter writer = createWriter(config);

        new MessageWriter(writer, numMessages).run();

        CommonTestHelper.waitUntilMessagesSent(stats, numMessages, 30000);
        testHelper.assertMessages(logStreamName, numMessages);

        assertFalse("stream does not exist in default region",
                    new CloudWatchTestHelper(helperClient, BASE_LOGGROUP_NAME, "testAlternateEndpoint")
                        .isLogStreamAvailable(logStreamName));

        testHelper.deleteLogGroupIfExists();
    }


    @Test
    public void testRetentionPeriod() throws Exception
    {
        init("testRetentionPeriod", helperClient);

        CloudWatchWriterConfig config = defaultConfig();
        config.retentionPeriod = 3;
        CloudWatchLogWriter writer = createWriter(config);

        // will write a single message so that we have something to wait for
        new MessageWriter(writer, 1).run();
        CommonTestHelper.waitUntilMessagesSent(stats, 1, 30000);

        assertEquals("retention period", 3, testHelper.describeLogGroup().getRetentionInDays().intValue());

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
            config.logStreamName = "testDedicatedWriter-" + ii;
            config.dedicatedWriter = true;
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

        CommonTestHelper.waitUntilMessagesSent(stats, numWriters * numReps, 30000);
        internalLogger.assertInternalErrorLog();

        testHelper.deleteLogGroupIfExists();
    }
}
