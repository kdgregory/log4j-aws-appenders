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

package com.kdgregory.log4j.aws;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import net.sf.kdgcommons.collections.DefaultMap;
import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.lang.ThreadUtil;
import net.sf.kdgcommons.test.StringAsserts;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.kinesis.model.*;
import com.amazonaws.util.BinaryUtils;

import com.kdgregory.logging.aws.kinesis.KinesisWriterStatistics;
import com.kdgregory.logging.aws.kinesis.KinesisLogWriter;


public class KinesisAppenderIntegrationTest
{
    private Logger localLogger;
    private AmazonKinesis localClient;

    private static boolean localFactoryUsed;

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        localFactoryUsed = false;
    }

//----------------------------------------------------------------------------
//  Tests
//
//  Note: most tests create their streams, since we want to examine various
//        combinations of shards and partition keys
//----------------------------------------------------------------------------

    @Test
    public void smoketest() throws Exception
    {
        final String streamName = "AppenderIntegrationTest-smoketest";
        final int numMessages = 1001;

        init("KinesisAppenderIntegrationTest/smoketest.properties", streamName);
        localLogger.info("smoketest: starting");

        Logger testLogger = Logger.getLogger("TestLogger");
        KinesisAppender appender = (KinesisAppender)testLogger.getAppender("test");

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("smoketest: reading messages");
        List<RetrievedRecord> messages = retrieveAllMessages(streamName, numMessages);

        assertMessages(messages, 1, numMessages, "test");

        assertShardCount(streamName, 3);
        assertRetentionPeriod(streamName, 48);

        assertTrue("client factory used", localFactoryUsed);

        KinesisWriterStatistics appenderStats = appender.getAppenderStatistics();
        assertEquals("log stream name, from stats",     streamName,     appenderStats.getActualStreamName());
        assertEquals("messages written, from stats",    numMessages,    appenderStats.getMessagesSent());

        localLogger.info("smoketest: finished");
    }


    @Test
    public void testMultipleThreadsSingleAppender() throws Exception
    {
        final String streamName = "AppenderIntegrationTest-testMultipleThreadsSingleAppender";
        int messagesPerThread = 500;

        init("KinesisAppenderIntegrationTest/testMultipleThreadsSingleAppender.properties", streamName);
        localLogger.info("multi-thread/single-appender: starting");

        Logger testLogger = Logger.getLogger("TestLogger");

        MessageWriter[] writers = new MessageWriter[]
        {
            new MessageWriter(testLogger, messagesPerThread),
            new MessageWriter(testLogger, messagesPerThread),
            new MessageWriter(testLogger, messagesPerThread),
            new MessageWriter(testLogger, messagesPerThread),
            new MessageWriter(testLogger, messagesPerThread)
        };

        MessageWriter.runOnThreads(writers);
        int expectedMessages = writers.length * messagesPerThread;

        localLogger.info("multi-thread/single-appender: reading messages");
        List<RetrievedRecord> messages = retrieveAllMessages(streamName, expectedMessages);

        assertMessages(messages, writers.length, messagesPerThread * writers.length, "test");

        Map<String,List<RetrievedRecord>> groupedByShard = groupByShard(messages);
        assertEquals("all messages written to same shard", 1, groupedByShard.size());

        assertShardCount(streamName, 2);
        assertRetentionPeriod(streamName, 24);

        assertFalse("client factory used", localFactoryUsed);

        localLogger.info("multi-thread/single-appender: finished");
    }


    @Test
    public void testMultipleThreadsMultipleAppendersDistinctPartitions() throws Exception
    {
        final String streamName = "AppenderIntegrationTest-testMultipleThreadsMultipleAppenders";
        int messagesPerThread = 500;

        init("KinesisAppenderIntegrationTest/testMultipleThreadsMultipleAppendersMultiplePartitions.properties", streamName);
        localLogger.info("multi-thread/multi-appender: starting");

        Logger testLogger1 = Logger.getLogger("TestLogger1");
        Logger testLogger2 = Logger.getLogger("TestLogger2");
        Logger testLogger3 = Logger.getLogger("TestLogger3");

        MessageWriter[] writers = new MessageWriter[]
        {
            new MessageWriter(testLogger1, messagesPerThread),
            new MessageWriter(testLogger2, messagesPerThread),
            new MessageWriter(testLogger3, messagesPerThread),
            new MessageWriter(testLogger1, messagesPerThread),
            new MessageWriter(testLogger2, messagesPerThread),
            new MessageWriter(testLogger3, messagesPerThread)
        };

        MessageWriter.runOnThreads(writers);
        int expectedMessages = writers.length * messagesPerThread;

        localLogger.info("multi-thread/multi-appender: reading messages");
        List<RetrievedRecord> messages = retrieveAllMessages(streamName, expectedMessages);

        assertMessages(messages, writers.length, messagesPerThread * 2, "test1", "test2", "test3");

        Map<String,List<RetrievedRecord>> groupedByShard = groupByShard(messages);
        assertEquals("messages written to multiple shards", 2, groupedByShard.size());

        assertShardCount(streamName, 2);
        assertRetentionPeriod(streamName, 24);

        assertFalse("client factory used", localFactoryUsed);

        localLogger.info("multi-thread/multi-appender: finished");
    }


    @Test
    public void testRandomPartitionKeys() throws Exception
    {
        final String streamName = "AppenderIntegrationTest-randomPartitionKeys";
        final int numMessages = 250;

        init("KinesisAppenderIntegrationTest/randomPartitionKeys.properties", streamName);
        localLogger.info("testRandomPartitionKeys: starting");

        Logger testLogger = Logger.getLogger("TestLogger");

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("testRandomPartitionKeys: reading messages");
        List<RetrievedRecord> messages = retrieveAllMessages(streamName, numMessages);

        assertShardCount(streamName, 2);

        // at this point I'm going to assume that the message content is correct,
        // because three other tests have asserted that, so will just verify overall
        // count and how the records were partitioned

        assertEquals("overall message count", numMessages, messages.size());

        Set<String> partitionKeys = new HashSet<String>();
        for (RetrievedRecord message : messages)
        {
            partitionKeys.add(message.partitionKey);
            StringAsserts.assertRegex("8-character numeric partition key (was: " + message.partitionKey + ")",
                                      "\\d{8}", message.partitionKey);
        }
        assertTrue("expected roughly " + numMessages + " partition keys (was: " + partitionKeys.size() + ")",
                   (partitionKeys.size() > numMessages - 20) && (partitionKeys.size() < numMessages + 20));

        localLogger.info("testRandomPartitionKeys: finished");
    }


    @Test
    public void testFailsIfNoStreamPresent() throws Exception
    {
        final String streamName = "AppenderIntegrationTest-testFailsIfNoStreamPresent";
        final int numMessages = 1001;

        init("KinesisAppenderIntegrationTest/testFailsIfNoStreamPresent.properties", streamName);
        localLogger.info("testFailsIfNoStreamPresent: starting");

        Logger testLogger = Logger.getLogger("TestLogger");
        KinesisAppender appender = (KinesisAppender)testLogger.getAppender("test");

        (new MessageWriter(testLogger, numMessages)).run();

        localLogger.info("testFailsIfNoStreamPresent: waiting for writer initialization to finish");

        waitForWriterInitialization(appender, 10);
        String initializationMessage = appender.getAppenderStatistics().getLastErrorMessage();

        StringAsserts.assertRegex(
            "initialization message did not indicate missing stream (was \"" + initializationMessage + "\")",
            ".*stream.*" + streamName + ".* not exist .*",
            initializationMessage);

        localLogger.info("testFailsIfNoStreamPresent: finished");
    }

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Factory method called by smoketest
     */
    public static AmazonKinesis createClient()
    {
        localFactoryUsed = true;
        return AmazonKinesisClientBuilder.defaultClient();
    }


    /**
     *  Loads the test-specific Log4J configuration and resets the environment.
     */
    public void init(String propertiesName, String streamName) throws Exception
    {
        URL config = ClassLoader.getSystemResource(propertiesName);
        assertNotNull("missing configuration: " + propertiesName, config);

        LogManager.resetConfiguration();
        PropertyConfigurator.configure(config);

        localLogger = Logger.getLogger(getClass());

        localClient = AmazonKinesisClientBuilder.defaultClient();

        deleteStreamIfExists(streamName);
    }


    /**
     *  Waits until the passed appender (1) creates a writer, and (2) that writer
     *  signals that initialization is complete or that an error occurred.
     *
     *  @return The writer's initialization message (null means successful init).
     */
    private void waitForWriterInitialization(KinesisAppender appender, int timeoutInSeconds)
    throws Exception
    {
        long timeoutAt = System.currentTimeMillis() + 1000 * timeoutInSeconds;
        while (System.currentTimeMillis() < timeoutAt)
        {
            KinesisLogWriter writer = ClassUtil.getFieldValue(appender, "writer", KinesisLogWriter.class);
            if ((writer != null) && writer.isInitializationComplete())
                return;
            else if (appender.getAppenderStatistics().getLastErrorMessage() != null)
                return;

            Thread.sleep(1000);
        }

        fail("writer not initialized within timeout");
    }


    /**
     *  Returns the stream description, null if the stream doesn't exist. Will
     *  automatically retry after a wait if throttled.
     */
    private StreamDescription describeStream(String streamName)
    {
        try
        {
            DescribeStreamRequest describeRequest = new DescribeStreamRequest().withStreamName(streamName);
            DescribeStreamResult describeReponse  = localClient.describeStream(describeRequest);
            return describeReponse.getStreamDescription();
        }
        catch (ResourceNotFoundException ex)
        {
            return null;
        }
        catch (LimitExceededException ignored)
        {
            ThreadUtil.sleepQuietly(1000);
            return describeStream(streamName);
        }
    }


    private void deleteStreamIfExists(String streamName) throws Exception
    {
        if (describeStream(streamName) != null)
        {
            localClient.deleteStream(new DeleteStreamRequest().withStreamName(streamName));
            localLogger.info("deleted stream; waiting for it to be gone");
            while (describeStream(streamName) != null)
            {
                Thread.sleep(1000);
            }
        }
    }


    private StreamDescription waitForStreamToBeReady(String streamName) throws Exception
    {
        for (int ii = 0 ; ii < 60 ; ii++)
        {
            Thread.sleep(1000);
            StreamDescription desc = describeStream(streamName);
            if ((desc != null) && (StreamStatus.ACTIVE.toString().equals(desc.getStreamStatus())))
            {
                return desc;
            }
        }
        throw new RuntimeException("stream \"" + streamName + "\" wasn't ready within 60 seconds");
    }


    /**
     *  Attempts to retrieve messages from the stream for up to 60 seconds, reading each
     *  shard once per second. Once the expected number of records has been read, will
     *  continue to read for an additional several seconds to pick up unexpected records.
     *  <p>
     *  Returns the records grouped by shard, so that multi-shard tests can verify that
     *  all shards were written.
     */
    List<RetrievedRecord> retrieveAllMessages(String streamName, int expectedRecords)
    throws Exception
    {
        waitForStreamToBeReady(streamName);

        List<RetrievedRecord> result = new ArrayList<RetrievedRecord>();

        Map<String,String> shardItxs = getInitialShardIterators(streamName);
        List<String> shardIds = new ArrayList<String>(shardItxs.keySet());

        int readAttempts = 60;
        while (readAttempts > 0)
        {
            for (String shardId : shardIds)
            {
                String shardItx = shardItxs.get(shardId);
                String newShardItx = readMessagesFromShard(shardId, shardItx, result);
                shardItxs.put(shardId, newShardItx);
            }

            // short-circuit if we've read all expected records
            readAttempts = (result.size() >= expectedRecords) && (readAttempts > 5)
                         ? 5
                         : readAttempts - 1;

            Thread.sleep(1000);
        }

        return result;
    }


    private Map<String,String> getInitialShardIterators(String streamName)
    {
        Map<String,String> result = new HashMap<String,String>();
        for (Shard shard : describeStream(streamName).getShards())
        {
            String shardId = shard.getShardId();
            GetShardIteratorRequest shardItxRequest = new GetShardIteratorRequest()
                                                      .withStreamName(streamName)
                                                      .withShardIteratorType(ShardIteratorType.TRIM_HORIZON)
                                                      .withShardId(shardId);
            GetShardIteratorResult shardItxResponse = localClient.getShardIterator(shardItxRequest);
            result.put(shardId, shardItxResponse.getShardIterator());
        }
        return result;
    }


    private String readMessagesFromShard(String shardId, String shardItx, List<RetrievedRecord> messages)
    throws Exception
    {
        GetRecordsRequest recordsRequest = new GetRecordsRequest().withShardIterator(shardItx);
        GetRecordsResult recordsResponse = localClient.getRecords(recordsRequest);
        for (Record record : recordsResponse.getRecords())
        {
            messages.add(new RetrievedRecord(shardId, record));
        }
        return recordsResponse.getNextShardIterator();
    }


    private void assertMessages(List<RetrievedRecord> messages, int expectedThreadCount, int expectedMessagesPerPartitionKey, String... expectedPartitionKeys)
    throws Exception
    {
        assertEquals("overall message count",
                     expectedMessagesPerPartitionKey * expectedPartitionKeys.length,
                     messages.size());

        Set<Integer> threadIds = new HashSet<Integer>();
        Map<Integer,Integer> countsByMessageNumber = new DefaultMap<Integer,Integer>(new HashMap<Integer,Integer>(), Integer.valueOf(0));
        Map<String,Integer> countsByPartitionKey = new DefaultMap<String,Integer>(new HashMap<String,Integer>(), Integer.valueOf(0));

        for (RetrievedRecord message : messages)
        {
            Matcher matcher = MessageWriter.PATTERN.matcher(message.message);
            assertTrue("message matches pattern: " + message, matcher.matches());

            Integer threadNum = MessageWriter.getThreadId(matcher);
            threadIds.add(threadNum);

            Integer messageNum = MessageWriter.getMessageNumber(matcher);
            int oldMessageCount = countsByMessageNumber.get(messageNum);
            countsByMessageNumber.put(messageNum, oldMessageCount + 1);

            int oldPartitionCount = countsByPartitionKey.get(message.partitionKey);
            countsByPartitionKey.put(message.partitionKey, oldPartitionCount + 1);
        }

        assertEquals("number of threads that were writing", expectedThreadCount, threadIds.size());

        for (Integer messageNum : countsByMessageNumber.keySet())
        {
            assertEquals("number of instance of message " + messageNum,
                         Integer.valueOf(expectedThreadCount),
                         countsByMessageNumber.get(messageNum));
        }

        for (String partitionKey : expectedPartitionKeys)
        {
            assertEquals("messages for partition key \"" + partitionKey + "\"",
                         expectedMessagesPerPartitionKey,
                         countsByPartitionKey.get(partitionKey).intValue());
        }
    }


    private Map<String,List<RetrievedRecord>> groupByShard(List<RetrievedRecord> messages)
    {
        Map<String,List<RetrievedRecord>> result = new HashMap<String,List<RetrievedRecord>>();
        for (RetrievedRecord record : messages)
        {
            List<RetrievedRecord> byShard = result.get(record.shardId);
            if (byShard == null)
            {
                byShard = new ArrayList<KinesisAppenderIntegrationTest.RetrievedRecord>();
                result.put(record.shardId, byShard);
            }
            byShard.add(record);
        }
        return result;
    }


    /**
     *  Gets the stream description and asserts that the shard count is as expected.
     *  This is a method for consistency with assertRetentionPeriod() (ie, so that
     *  test code makes two named calls rather than one named call and one assert).
     */
    private void assertShardCount(String streamName, int expectedShardCount)
    {
        assertEquals("shard count", expectedShardCount, describeStream(streamName).getShards().size());
    }


    /**
     *  Repeatedly gets the stream description and checks the retention period (because
     *  it is eventually consistent). Fails if the retention period is not the expected
     *  value within a minutes.
     *  <p>
     *  To minimize the time taken by this method, call after retrieving messages.
     */
    private void assertRetentionPeriod(String streamName, Integer expectedRetentionPeriod) throws Exception
    {
        for (int ii = 0 ; ii < 60 ; ii++)
        {
            Integer actualRetentionPeriod = describeStream(streamName).getRetentionPeriodHours();
            if ((actualRetentionPeriod != null) && (actualRetentionPeriod.equals(expectedRetentionPeriod)))
                return;
            else
                Thread.sleep(1000);
        }
        fail("retention period was not " + expectedRetentionPeriod + " within 60 seconds");
    }


    /**
     *  Holder for a retrieved record. Extracts the record data and partition key,
     *  and adds the shard ID (passed in).
     */
    private static class RetrievedRecord
    {
        public String shardId;
        public String partitionKey;
        public String message;

        public RetrievedRecord(String shardId, Record record)
        throws Exception
        {
            this.shardId = shardId;
            this.partitionKey = record.getPartitionKey();
            this.message = new String(BinaryUtils.copyAllBytesFrom(record.getData()), "UTF-8").trim();
        }
    }
    
    
    private static class MessageWriter
    extends com.kdgregory.logging.testhelpers.MessageWriter
    {
        private Logger logger;
        
        public MessageWriter(Logger logger, int numMessages)
        {
            super(numMessages);
            this.logger = logger;
        }

        @Override
        protected void writeLogMessage(String message)
        {
            logger.debug(message);
        }
    }
}
