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


import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
import com.kdgregory.logging.testhelpers.TestableInternalLogger;


public class CloudWatchLogWriterIntegrationTest
{
    private final static String LOGGROUP_NAME = "CloudWatchLogWriterIntegrationTest";

    // single client is shared by all tests
    private static AWSLogsClient cloudwatchClient;

    // except for this one, which is only used by "not the default domain" tests
    private AWSLogsClient altClient;

    // this is for logging within the test
    private Logger localLogger = LoggerFactory.getLogger(getClass());

    // these are all assigned by init()
    private CloudWatchTestHelper testHelper;
    private CloudWatchWriterStatistics stats;
    private TestableInternalLogger internalLogger;
    private CloudWatchWriterConfig config;
    private CloudWatchWriterFactory factory;
    private CloudWatchLogWriter writer;


//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    {
        // constructor because we're running against 1.11.0
        cloudwatchClient = new AWSLogsClient();
    }


    @After
    public void tearDown()
    {
        if (altClient != null)
        {
            altClient.shutdown();
        }

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
        final int numMessages = 1001;

        init("smoketest", cloudwatchClient, null, null, null);

        new MessageWriter(numMessages).run();

        testHelper.assertMessages("smoketest", numMessages);
    }


    @Test
    public void testAlternateRegion() throws Exception
    {
        final int numMessages = 1001;

        // default region for constructor is always us-east-1
        altClient = new AWSLogsClient().withRegion(Regions.US_WEST_1);

        init("testAlternateRegion", altClient, null, "us-west-1", null);

        new MessageWriter(numMessages).run();

        testHelper.assertMessages("testAlternateRegion", numMessages);

        assertFalse("stream does not exist in default region",
                    new CloudWatchTestHelper(cloudwatchClient, LOGGROUP_NAME).isLogStreamAvailable("testAlternateRegion"));
    }


    @Test
    public void testAlternateEndpoint() throws Exception
    {
        final int numMessages = 1001;

        // the goal here is to verify that we can use a region that didn't exist when 1.11.0 came out
        // BEWARE: my default region is us-east-1, so I use us-east-2 as the alternate
        //         if that is your default, then the test will fail
        altClient = new AWSLogsClient().withEndpoint("logs.us-east-2.amazonaws.com");

        init("testAlternateEndpoint", altClient, null, null, "logs.us-east-2.amazonaws.com");

        new MessageWriter(numMessages).run();

        testHelper.assertMessages("testAlternateEndpoint", numMessages);

        assertFalse("stream does not exist in default region",
                    new CloudWatchTestHelper(cloudwatchClient, LOGGROUP_NAME).isLogStreamAvailable("testAlternateEndpoint"));
    }

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    private void init(String testName, AWSLogs client, String factoryMethod, String region, String endpoint)
    throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        testHelper = new CloudWatchTestHelper(client, LOGGROUP_NAME);

        testHelper.deleteLogGroupIfExists();

        config = new CloudWatchWriterConfig(LOGGROUP_NAME, testName, 250, 10000, DiscardAction.oldest, factoryMethod, region, endpoint);
        stats = new CloudWatchWriterStatistics();
        internalLogger = new TestableInternalLogger();
        factory = new CloudWatchWriterFactory();
        writer = (CloudWatchLogWriter)factory.newLogWriter(config, stats, internalLogger);

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
}
