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
    private final static String LOGGER1_NAME            = "SmoketestKinesisAppender1";
    private final static String LOGGER2_NAME            = "SmoketestKinesisAppender2";
    private final static String STREAM_NAME             = "AppenderIntegratonTest";
    private final static int    BATCH_DELAY             = 3000;

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

        Logger logger = Logger.getLogger(LOGGER1_NAME);
        (new MessageWriter(logger, numMessages)).run();

        testLogger.info("smoketest: waiting for stream to become ready");
        waitForStreamToBeReady();

        testLogger.info("smoketest: reading messages");
        List<String> messages = retrieveAllMessages(numMessages);

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

        Logger[] loggers = new Logger[] { Logger.getLogger(LOGGER1_NAME), Logger.getLogger(LOGGER2_NAME) };

        List<Thread> threads = new ArrayList<Thread>();
        for (int threadNum = 0 ; threadNum < numThreads ; threadNum++)
        {
            Thread thread = new Thread(new MessageWriter(loggers[threadNum % 2], numMessagesPerThread));
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads)
        {
            thread.join();
        }

        testLogger.info("concurrencyTest: waiting for stream to become ready");
        waitForStreamToBeReady();

        testLogger.info("concurrencyTest: reading messages");
        List<String> messages = retrieveAllMessages(totalMessageCount);

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


    private void deleteStreamIfExists() throws Exception
    {
        try
        {
            client.deleteStream(new DeleteStreamRequest().withStreamName(STREAM_NAME));
            testLogger.info("deleted stream; waiting for it to be gone");
            while (true)
            {
                // we'll loop until this throws
                describeStream();
                Thread.sleep(1000);
            }
        }
        catch (ResourceNotFoundException ignored)
        {
            return;
        }
    }


    private void waitForStreamToBeReady() throws Exception
    {
        for (int ii = 0 ; ii < 60 ; ii++)
        {
            Thread.sleep(1000);
            try
            {
                StreamDescription desc = describeStream();
                if (StreamStatus.ACTIVE.toString().equals(desc.getStreamStatus()))
                {
                    return;
                }
            }
            catch (ResourceNotFoundException ex)
            {
                // ignored
            }
        }
        throw new RuntimeException("stream wasn't ready within 60 seconds");
    }


    /**
     *  Attempts to retrieve messages from the stream for up to 60 seconds, reading each
     *  shard once per second. Once the expected number of records has been read, will
     *  continue to read for an additional several seconds to pick up unexpected records.
     */
    List<String> retrieveAllMessages(int expectedRecords)
    throws Exception
    {
        List<String> result = new ArrayList<String>();

        // this sleep gives all writers a chance to do their work
        Thread.sleep(BATCH_DELAY);

        List<String> shardItxs = getInitialShardIterators();

        int readAttempts = 60;
        while (readAttempts > 0)
        {
            List<String> newShardItxs = new ArrayList<String>();
            for (String shardItx : shardItxs)
            {
                newShardItxs.add(readMessagesFromShard(shardItx, result));
            }

            shardItxs = newShardItxs;
            if ((result.size() >= expectedRecords) && (readAttempts > 5))
            {
                readAttempts = 5;
            }
            else
            {
                readAttempts--;
            }
            Thread.sleep(1000);
        }

        return result;
    }


    private String readMessagesFromShard(String shardItx, List<String> messages)
    throws Exception
    {
        GetRecordsRequest recordsRequest = new GetRecordsRequest().withShardIterator(shardItx);
        GetRecordsResult recordsResponse = client.getRecords(recordsRequest);
        for (Record record : recordsResponse.getRecords())
        {
            byte[] data = BinaryUtils.copyAllBytesFrom(record.getData());
            messages.add(new String(data, "UTF-8"));
        }
        return recordsResponse.getNextShardIterator();
    }


    private List<String> getInitialShardIterators()
    {
        List<String> result = new ArrayList<String>();
        for (Shard shard : describeStream().getShards())
        {
            String shardId = shard.getShardId();
            GetShardIteratorRequest shardItxRequest = new GetShardIteratorRequest()
                                                      .withStreamName(STREAM_NAME)
                                                      .withShardIteratorType(ShardIteratorType.TRIM_HORIZON)
                                                      .withShardId(shardId);
            GetShardIteratorResult shardItxResponse = client.getShardIterator(shardItxRequest);
            result.add(shardItxResponse.getShardIterator());
        }
        return result;
    }


    private StreamDescription describeStream()
    {
            DescribeStreamRequest describeRequest = new DescribeStreamRequest().withStreamName(STREAM_NAME);
            DescribeStreamResult describeReponse  = client.describeStream(describeRequest);
            return describeReponse.getStreamDescription();
    }
}
