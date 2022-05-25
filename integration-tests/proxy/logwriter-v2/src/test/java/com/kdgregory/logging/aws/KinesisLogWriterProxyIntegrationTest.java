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

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import org.slf4j.MDC;

import com.kdgregory.logging.aws.kinesis.KinesisLogWriter;
import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;
import com.kdgregory.logging.aws.kinesis.KinesisWriterFactory;
import com.kdgregory.logging.aws.kinesis.KinesisWriterStatistics;
import com.kdgregory.logging.common.util.MessageQueue.DiscardAction;
import com.kdgregory.logging.testhelpers.KinesisTestHelper;
import com.kdgregory.logging.testhelpers.KinesisTestHelper.RetrievedRecord;
import com.kdgregory.logging.testhelpers.LogWriterMessageWriter;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;

import software.amazon.awssdk.services.kinesis.KinesisClient;


/**
 *  Tests the Kinesis log-writer using a proxy for AWS connections.
 *
 *  This is intended to be run on an EC2 instance in a private subnet, with a
 *  proxy  server running in a public subnet.
 *  <p>
 *  You must set the COM_KDGREGORY_LOGGING_PROXY_URL  environment variable, and
 *  edit the static variables in {@link AbstractProxyIntegrationTest} to match.
 */
public class KinesisLogWriterProxyIntegrationTest
extends AbstractProxyIntegrationTest
{
    // single "helper" client that's shared by all tests
    private static KinesisClient helperClient;

    // these are all assigned by init()
    private KinesisTestHelper testHelper;
    private KinesisWriterStatistics stats;
    private KinesisWriterFactory factory;
    private KinesisLogWriter writer;

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    private KinesisWriterConfig init(String testName, KinesisClient client)
    throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        testHelper = new KinesisTestHelper(client, testName);
        testHelper.deleteStreamIfExists();

        stats = new KinesisWriterStatistics();
        internalLogger = new TestableInternalLogger();
        factory = new KinesisWriterFactory();

        return new KinesisWriterConfig()
               .setStreamName(testHelper.getStreamName())
               .setPartitionKey("{random}")
               .setAutoCreate(true)
               .setShardCount(1)
               .setBatchDelay(250)
               .setDiscardThreshold(10000)
               .setDiscardAction(DiscardAction.oldest);
    }


    private void createWriter(KinesisWriterConfig config)
    throws Exception
    {
        writer = (KinesisLogWriter)factory.newLogWriter(config, stats, internalLogger);
        threadFactory.startWriterThread(writer, null);
    }

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    {
        helperClient = configureProxy(KinesisClient.builder()).build();
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
        helperClient.close();
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testProxy() throws Exception
    {
        final int numMessages = 1001;

        KinesisWriterConfig config = init("testProxy", helperClient);
        createWriter(config);

        new LogWriterMessageWriter(writer, numMessages).run();

        List<RetrievedRecord> records = testHelper.retrieveAllMessages(numMessages);
        testHelper.assertMessages(records, 1, numMessages);
        testHelper.assertRandomPartitionKeys(records, numMessages);

        assertEquals("internal error log", Collections.emptyList(), internalLogger.errorMessages);

        testHelper.deleteStreamIfExists();
    }
}
