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

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import net.sf.kdgcommons.lang.ClassUtil;

import org.slf4j.Logger;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClient;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterFactory;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.factories.DefaultThreadFactory;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.testhelpers.CloudWatchTestHelper;
import com.kdgregory.logging.testhelpers.CommonTestHelper;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;


public class CloudWatchLogWriterIntegrationTest
{
    private final static String LOGGROUP_NAME = "CloudWatchLogWriterIntegrationTest";

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
    private CloudWatchTestHelper testHelper;
    private CloudWatchWriterStatistics stats;
    private TestableInternalLogger internalLogger;
    private CloudWatchWriterConfig config;
    private CloudWatchWriterFactory factory;
    private CloudWatchLogWriter writer;

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Called at the beginning of most tests to create the test helper and
     *  writer, and delete any previous log group.
     */
    private void init(String testName, Integer retentionPeriod, AWSLogs client, String factoryMethod, String region, String endpoint)
    throws Exception
    {
        initWithoutWriter(testName, client);
        writer = createWriter(testName, false, retentionPeriod, factoryMethod, region, endpoint);
    }


    /**
     *  Creates the test helper and initializes test member variables.
     */
    private void initWithoutWriter(String testName, AWSLogs client)
    throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        logGroupName = getClass().getSimpleName() + "-" + testName;

        testHelper = new CloudWatchTestHelper(client, logGroupName);
        testHelper.deleteLogGroupIfExists();

        stats = new CloudWatchWriterStatistics();
        internalLogger = new TestableInternalLogger();
        factory = new CloudWatchWriterFactory();
    }


    /**
     *  Creates the logwriter and its execution thread.
     */
    private CloudWatchLogWriter createWriter(String testName, boolean dedicatedWriter, Integer retentionPeriod, String factoryMethod, String region, String endpoint)
    throws Exception
    {
        config = new CloudWatchWriterConfig(logGroupName, testName, retentionPeriod, false, 250, 10000, DiscardAction.oldest, factoryMethod, region, endpoint);
        writer = (CloudWatchLogWriter)factory.newLogWriter(config, stats, internalLogger);

        new DefaultThreadFactory("test").startLoggingThread(writer, false, null);

        return writer;
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
        if (writer != null)
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

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void smoketest() throws Exception
    {
        final int numMessages = 1001;

        init("smoketest", null, helperClient, null, null, null);

        new MessageWriter(numMessages).run();

        CommonTestHelper.waitUntilMessagesSent(stats, numMessages, 30000);
        testHelper.assertMessages("smoketest", numMessages);

        assertNull("static factory method not called", factoryClient);

        assertNull("retention period not set", testHelper.describeLogGroup().getRetentionInDays());
    }


    @Test
    public void testFactoryMethod() throws Exception
    {
        final int numMessages = 1001;

        init("testFactoryMethod", null, helperClient, getClass().getName() + ".staticClientFactory", null, null);

        new MessageWriter(numMessages).run();

        CommonTestHelper.waitUntilMessagesSent(stats, numMessages, 30000);
        testHelper.assertMessages("testFactoryMethod", numMessages);

        assertNotNull("factory method was called", factoryClient);
        assertSame("factory-created client used by writer", factoryClient, ClassUtil.getFieldValue(writer, "client", AWSLogs.class));
    }


    @Test
    public void testAlternateRegion() throws Exception
    {
        final int numMessages = 1001;

        // default region for constructor is always us-east-1
        altClient = new AWSLogsClient().withRegion(Regions.US_WEST_1);

        init("testAlternateRegion", null, altClient, null, "us-west-1", null);

        new MessageWriter(numMessages).run();

        CommonTestHelper.waitUntilMessagesSent(stats, numMessages, 30000);
        testHelper.assertMessages("testAlternateRegion", numMessages);

        assertFalse("stream does not exist in default region",
                    new CloudWatchTestHelper(helperClient, LOGGROUP_NAME).isLogStreamAvailable("testAlternateRegion"));
    }


    @Test
    public void testAlternateEndpoint() throws Exception
    {
        final int numMessages = 1001;

        // the goal here is to verify that we can use a region that didn't exist when 1.11.0 came out
        // BEWARE: my default region is us-east-1, so I use us-east-2 as the alternate
        //         if that is your default, then the test will fail
        altClient = new AWSLogsClient().withEndpoint("logs.us-east-2.amazonaws.com");

        init("testAlternateEndpoint", null, altClient, null, null, "logs.us-east-2.amazonaws.com");

        new MessageWriter(numMessages).run();

        CommonTestHelper.waitUntilMessagesSent(stats, numMessages, 30000);
        testHelper.assertMessages("testAlternateEndpoint", numMessages);

        assertFalse("stream does not exist in default region",
                    new CloudWatchTestHelper(helperClient, LOGGROUP_NAME).isLogStreamAvailable("testAlternateEndpoint"));
    }


    @Test
    public void testRetentionPeriod() throws Exception
    {
        init("testRetentionPeriod", 3, helperClient, null, null, null);

        // will write a single message so that we have something to wait for
        new MessageWriter(1).run();
        CommonTestHelper.waitUntilMessagesSent(stats, 1, 30000);

        assertEquals("retention period", 3, testHelper.describeLogGroup().getRetentionInDays().intValue());
    }
    
    
    @Test
    public void testDedicatedWriter() throws Exception 
    {
        final int numWriters = 50;
        final int numReps = 50;

        initWithoutWriter("testDedicatedWriter", helperClient);
        
        List<CloudWatchLogWriter> writers = new ArrayList<CloudWatchLogWriter>(numWriters);
        for (int ii = 0 ; ii < numWriters ; ii++)
        {
            localLogger.debug("creating writer {}", ii);
            writers.add(createWriter("testDedicatedWriter-" + ii, false, null, null, null, null));
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
        
        for (CloudWatchLogWriter w : writers)
        {
            w.stop();
        }
        
        // FIXME - join on threads, remove this synchronized bloc
        
        synchronized (internalLogger)
        {
            System.err.println("logged error messages: " + internalLogger.errorMessages);
        }
    }
    
}
