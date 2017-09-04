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


public class TestKinesisAppender
{
    // CHANGE THESE IF YOU CHANGE THE CONFIG
    private final static String LOGGER_NAME             = "SmoketestKinesisAppender";
    private final static String STREAM_NAME             = "AppenderIntegratonTest";
    private final static int    TRIES_AT_END_OF_STREAM  = 5;

    private Logger testLogger = Logger.getLogger(getClass());
    private AmazonKinesis client;


    @Before
    public void setUp() throws Exception
    {
        URL config = ClassLoader.getSystemResource("KinesisAppenderSmoketest.properties");
        PropertyConfigurator.configure(config);

        client = AmazonKinesisClientBuilder.defaultClient();
        deleteStreamIfExists();
    }


//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void smoketest() throws Exception
    {
        testLogger.info("smoketest: starting");

        final int numMessages = 1001;

        Logger logger = Logger.getLogger(LOGGER_NAME);
        (new MessageWriter(logger, numMessages)).run();

        testLogger.info("smoketest: reading messages");
        List<String> messages = retrieveAllMessages();

        // messages may be written out of order, so we'll just check total
        assertEquals("number of messages", numMessages, messages.size());

        testLogger.info("smoketest: finished");
    }


    @Test
    public void concurrencyTest() throws Exception
    {
        testLogger.info("concurrencyTest: starting");

        final int numThreads = 5;
        final int numMessagesPerThread = 200;
        final int totalMessageCount = numThreads * numMessagesPerThread;

        Logger logger = Logger.getLogger(LOGGER_NAME);

        List<Thread> threads = new ArrayList<Thread>();
        for (int threadNum = 0 ; threadNum < numThreads ; threadNum++)
        {
            Thread thread = new Thread(new MessageWriter(logger, numMessagesPerThread));
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads)
        {
            thread.join();
        }

        testLogger.info("concurrencyTest: reading messages");
        List<String> messages = retrieveAllMessages();

        // messages may be written out of order, so we'll just check total
        assertEquals("number of messages", totalMessageCount, messages.size());

        testLogger.info("concurrencyTest: finished");
    }

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Writes a sequence of messages to the log. Can either be called inline
     *  or on a thread.
     */
    private static class MessageWriter implements Runnable
    {
        private Logger logger;
        private int numMessages;

        public MessageWriter(Logger logger, int numMessages)
        {
            this.logger = logger;
            this.numMessages = numMessages;
        }

        public void run()
        {
            for (int ii = 0 ; ii < numMessages ; ii++)
            {
                logger.debug("message on thread " + Thread.currentThread().getId() + ": " + ii);
            }
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
            GetRecordsResult recordsResponse = client.getRecords(recordsRequest);
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


    private void deleteStreamIfExists() throws Exception
    {
        try
        {
            client.deleteStream(new DeleteStreamRequest().withStreamName(STREAM_NAME));
            testLogger.info("deleted stream; waiting for it to be gone");
            while (true)
            {
                client.describeStream(new DescribeStreamRequest().withStreamName(STREAM_NAME));
                Thread.sleep(1000);
            }
        }
        catch (ResourceNotFoundException ignored)
        {
            // if we can't find the stream either it never existed or has been deleted
            return;
        }
    }


    private String waitForStreamToBeReady() throws Exception
    {
        for (int ii = 0 ; ii < 60 ; ii++)
        {
            Thread.sleep(1000);
            try
            {
                DescribeStreamRequest describeRequest = new DescribeStreamRequest().withStreamName(STREAM_NAME);
                DescribeStreamResult describeReponse  = client.describeStream(describeRequest);
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
        GetShardIteratorResult shardItxResponse = client.getShardIterator(shardItxRequest);
        return shardItxResponse.getShardIterator();
    }
}
