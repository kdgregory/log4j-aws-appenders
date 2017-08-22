// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.amazonaws.services.kinesis.AmazonKinesis;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.kinesis.model.*;
import com.amazonaws.util.BinaryUtils;


public class SmoketestKinesisAppender
{
    private final static String STREAM_NAME = "SmoketestKinesisAppender";
    private final static int TRIES_AT_END_OF_STREAM = 5;

    private AmazonKinesis kinesisClient;

    @Before
    public void setUp() throws Exception
    {
        URL config = ClassLoader.getSystemResource("SmoketestKinesisAppender.properties");
        PropertyConfigurator.configure(config);

        kinesisClient = AmazonKinesisClientBuilder.defaultClient();

    }


    @Test
    public void smoketest() throws Exception
    {
        Logger logger = Logger.getLogger(getClass());
        KinesisAppender appender = (KinesisAppender)logger.getAppender("test");

        for (int ii = 0 ; ii < 1001 ; ii++)
        {
            logger.debug("message " + ii);
        }

        assertMessages(1001);

        System.out.println("done");
    }


    private void assertMessages(int expectedMessageCount) throws Exception
    {
        List<String> messages = retrieveAllMessages();
        assertEquals("number of messages", expectedMessageCount, messages.size());

        int prevMessageNum = -1;
        for (String message : messages)
        {
            int messageNum = Integer.parseInt(message.replaceAll(".* message ", "").trim());
            if (prevMessageNum >= 0)
                assertEquals("message sequence", prevMessageNum + 1, messageNum);
            prevMessageNum = messageNum;
        }
    }


    List<String> retrieveAllMessages() throws Exception
    {
        List<String> result = new ArrayList<String>();
        
        String shardId = waitForStreamToBeReady();
        String shardItx = getInitialShardIterator(shardId);

        int eosTries = TRIES_AT_END_OF_STREAM;
        while (eosTries > 0)
        {
            GetRecordsRequest recordsRequest = new GetRecordsRequest().withShardIterator(shardItx);
            GetRecordsResult recordsResponse = kinesisClient.getRecords(recordsRequest);
            for (Record record : recordsResponse.getRecords())
            {
                byte[] data = BinaryUtils.copyAllBytesFrom(record.getData());
                result.add(new String(data, "UTF-8"));
            }
            shardItx = recordsResponse.getNextShardIterator();
            eosTries = recordsResponse.getMillisBehindLatest() == 0
                     ? eosTries - 1
                     : TRIES_AT_END_OF_STREAM;
            Thread.sleep(500);
        }

        return result;
    }
    
    
    private String waitForStreamToBeReady() throws Exception
    {
        for (int ii = 0 ; ii < 60 ; ii++)
        {
            Thread.sleep(1000);
            try
            {
                DescribeStreamRequest describeRequest = new DescribeStreamRequest().withStreamName(STREAM_NAME);
                DescribeStreamResult describeReponse  = kinesisClient.describeStream(describeRequest);
                List<Shard> shards = describeReponse.getStreamDescription().getShards();
                if ((shards != null) && (shards.size() > 0))
                {
                    // for testing, we know that we have only one shared
                    return shards.get(0).getShardId();
                }
            }
            catch (ResourceNotFoundException ex)
            {
                // ignored
            }
        }
        throw new RuntimeException("stream wasn't ready within 60 seconds");
    }
    
    
    private String getInitialShardIterator(String shardId)
    {
        GetShardIteratorRequest shardItxRequest = new GetShardIteratorRequest()
                                                  .withStreamName(STREAM_NAME)
                                                  .withShardIteratorType(ShardIteratorType.TRIM_HORIZON)
                                                  .withShardId(shardId);
        GetShardIteratorResult shardItxResponse = kinesisClient.getShardIterator(shardItxRequest);
        return shardItxResponse.getShardIterator();
    }
}
