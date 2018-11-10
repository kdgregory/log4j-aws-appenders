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

package com.kdgregory.logging.testhelpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

import static org.junit.Assert.*;

import net.sf.kdgcommons.collections.DefaultMap;
import net.sf.kdgcommons.lang.ThreadUtil;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.model.*;
import com.amazonaws.util.BinaryUtils;

import com.kdgregory.logging.aws.kinesis.KinesisWriterStatistics;


/**
 *  A collection of utility methods to support integration tests. This is an
 *  instantiable class, preserving the AWS client.
 *  <p>
 *  This is not intended for production use outside of this library.
 */
public class KinesisTestHelper
{
    private AmazonKinesis client;
    private String streamName;


    public KinesisTestHelper(AmazonKinesis client, String streamName)
    {
        this.client = client;
        this.streamName = streamName;
    }


    /**
     *  Returns the stream description, null if the stream doesn't exist. Will
     *  automatically retry after a wait if throttled.
     */
    public StreamDescription describeStream()
    {
        try
        {
            DescribeStreamRequest describeRequest = new DescribeStreamRequest().withStreamName(streamName);
            DescribeStreamResult describeReponse  = client.describeStream(describeRequest);
            return describeReponse.getStreamDescription();
        }
        catch (ResourceNotFoundException ex)
        {
            return null;
        }
        catch (LimitExceededException ignored)
        {
            ThreadUtil.sleepQuietly(1000);
            return describeStream();
        }
    }


    public StreamDescription waitForStreamToBeReady() throws Exception
    {
        for (int ii = 0 ; ii < 60 ; ii++)
        {
            Thread.sleep(1000);
            StreamDescription desc = describeStream();
            if ((desc != null) && (StreamStatus.ACTIVE.toString().equals(desc.getStreamStatus())))
            {
                return desc;
            }
        }
        throw new RuntimeException("stream \"" + streamName + "\" wasn't ready within 60 seconds");
    }


    public void deleteStreamIfExists() throws Exception
    {
        if (describeStream() != null)
        {
            client.deleteStream(new DeleteStreamRequest().withStreamName(streamName));
//            localLogger.info("deleted stream; waiting for it to be gone");
            while (describeStream() != null)
            {
                Thread.sleep(1000);
            }
        }
    }


    public Map<String,String> getInitialShardIterators()
    {
        Map<String,String> result = new HashMap<String,String>();
        for (Shard shard : describeStream().getShards())
        {
            String shardId = shard.getShardId();
            GetShardIteratorRequest shardItxRequest = new GetShardIteratorRequest()
                                                      .withStreamName(streamName)
                                                      .withShardIteratorType(ShardIteratorType.TRIM_HORIZON)
                                                      .withShardId(shardId);
            GetShardIteratorResult shardItxResponse = client.getShardIterator(shardItxRequest);
            result.put(shardId, shardItxResponse.getShardIterator());
        }
        return result;
    }


    public String readMessagesFromShard(String shardId, String shardItx, List<RetrievedRecord> messages)
    throws Exception
    {
        GetRecordsRequest recordsRequest = new GetRecordsRequest().withShardIterator(shardItx);
        GetRecordsResult recordsResponse = client.getRecords(recordsRequest);
        for (Record record : recordsResponse.getRecords())
        {
            messages.add(new RetrievedRecord(shardId, record));
        }
        return recordsResponse.getNextShardIterator();
    }


    /**
     *  Attempts to retrieve messages from the stream for up to 60 seconds, reading each
     *  shard once per second. Once the expected number of records has been read, will
     *  continue to read for an additional several seconds to pick up unexpected records.
     *  <p>
     *  Returns the records grouped by shard, so that multi-shard tests can verify that
     *  all shards were written.
     */
    public List<RetrievedRecord> retrieveAllMessages(int expectedRecords)
    throws Exception
    {
        waitForStreamToBeReady();

        List<RetrievedRecord> result = new ArrayList<RetrievedRecord>();

        Map<String,String> shardItxs = getInitialShardIterators();
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


    public Map<String,List<RetrievedRecord>> groupByShard(List<RetrievedRecord> messages)
    {
        Map<String,List<RetrievedRecord>> result = new HashMap<String,List<RetrievedRecord>>();
        for (RetrievedRecord record : messages)
        {
            List<RetrievedRecord> byShard = result.get(record.shardId);
            if (byShard == null)
            {
                byShard = new ArrayList<RetrievedRecord>();
                result.put(record.shardId, byShard);
            }
            byShard.add(record);
        }
        return result;
    }


    public void assertMessages(List<RetrievedRecord> messages, int expectedThreadCount, int expectedMessagesPerPartitionKey, String... expectedPartitionKeys)
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


    /**
     *  Gets the stream description and asserts that the shard count is as expected.
     *  This is a method for consistency with assertRetentionPeriod() (ie, so that
     *  test code makes two named calls rather than one named call and one assert).
     */
    public void assertShardCount(int expectedShardCount)
    {
        assertEquals("shard count", expectedShardCount, describeStream().getShards().size());
    }


    /**
     *  Repeatedly gets the stream description and checks the retention period (because
     *  it is eventually consistent). Fails if the retention period is not the expected
     *  value within a minutes.
     *  <p>
     *  To minimize the time taken by this method, call after retrieving messages.
     */
    public void assertRetentionPeriod(Integer expectedRetentionPeriod) throws Exception
    {
        for (int ii = 0 ; ii < 60 ; ii++)
        {
            Integer actualRetentionPeriod = describeStream().getRetentionPeriodHours();
            if ((actualRetentionPeriod != null) && (actualRetentionPeriod.equals(expectedRetentionPeriod)))
                return;
            else
                Thread.sleep(1000);
        }
        fail("retention period was not " + expectedRetentionPeriod + " within 60 seconds");
    }


    /**
     *  Performs assertions against the writer statistics.
     */
    public void assertStats(KinesisWriterStatistics stats, int expectedMessages)
    {
        assertEquals("stats: log stream name",      streamName,         stats.getActualStreamName());
        assertEquals("stats: messages written",     expectedMessages,   stats.getMessagesSent());
    }


    /**
     *  Holder for a retrieved record. Extracts the record data and partition key,
     *  and adds the shard ID (passed in).
     */
    public static class RetrievedRecord
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
}
