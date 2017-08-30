// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchLogWriter;


public class TestCloudWatchAppender
{
    // CHANGE THESE IF YOU CHANGE THE CONFIG
    private final static String LOGGROUP_NAME = "Smoketest";
    private final static int    ROTATION_COUNT = 333;

    private AWSLogsClient client;


    @Before
    public void setUp() throws Exception
    {
        URL config = ClassLoader.getSystemResource("TestCloudWatchAppender.properties");
        PropertyConfigurator.configure(config);

        client = new AWSLogsClient();

        // note: we leave the log group at the end of the test, for diagnositics, so must
        //       delete it before starting a new test
        try
        {
            client.deleteLogGroup(new DeleteLogGroupRequest().withLogGroupName(LOGGROUP_NAME));
        }
        catch (ResourceNotFoundException ignored)
        {
            // it's OK if the log group doesn't exist when we start running
        }
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void smoketest() throws Exception
    {
        final int numMessages = 1001;

        Logger logger = Logger.getLogger(getClass());
        CloudWatchAppender appender = (CloudWatchAppender)logger.getAppender("test");

        for (int ii = 0 ; ii < numMessages ; ii++)
        {
            logger.debug("message " + ii);
        }

        // give the writers a chance to do their thing
        Thread.sleep(3000);

        assertMessages("smoketest-1", ROTATION_COUNT);
        assertMessages("smoketest-2", ROTATION_COUNT);
        assertMessages("smoketest-3", ROTATION_COUNT);
        assertMessages("smoketest-4", numMessages % ROTATION_COUNT);

        CloudWatchLogWriter lastWriter = (CloudWatchLogWriter)appender.writer;
        assertEquals("number of batches for last writer", 1, lastWriter.getBatchCount());

        // while we're here, verify that batch delay is propagated
        appender.setBatchDelay(1234L);
        assertEquals("batch delay", 1234L, lastWriter.getBatchDelay());
    }


    @Test
    public void concurrencyTest() throws Exception
    {
        final int numThreads = 5;
        final int numMessagesPerThread = 200;
        final int totalMessageCount = numThreads * numMessagesPerThread;
        final int expectedStreamCount = (totalMessageCount / ROTATION_COUNT) + 1;

        final Logger logger = Logger.getLogger(getClass());

        List<Thread> threads = new ArrayList<Thread>();
        for (int threadNum = 0 ; threadNum < numThreads ; threadNum++)
        {
            Thread thread = new Thread(new Runnable()
            {
                public void run()
                {
                    for (int ii = 0 ; ii < numMessagesPerThread ; ii++)
                    {
                        logger.debug("message on thread " + Thread.currentThread().getId() + ": " + ii);
                    }
                }
            });
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads)
        {
            thread.join();
        }

        // give the writers a chance to do their thing
        Thread.sleep(3000);

        for (int sequence = 1 ; sequence <= expectedStreamCount ; sequence++)
        {
            LinkedHashSet<OutputLogEvent> events = retrieveAllMessages("smoketest-" + sequence);
            if (sequence == expectedStreamCount)
                assertEquals("messages in last stream", totalMessageCount % ROTATION_COUNT, events.size());
            else
                assertEquals("messages in stream " + sequence, ROTATION_COUNT, events.size());
        }
    }

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Asserts that the stream contains the expected number of messages, and that
     *  they're in order.
     */
    private void assertMessages(String streamName, int expectedMessageCount) throws Exception
    {
        LinkedHashSet<OutputLogEvent> events = retrieveAllMessages(streamName);
        assertEquals("number of events in " + streamName, expectedMessageCount, events.size());

        int prevMessageNum = -1;
        for (OutputLogEvent event : events)
        {
            int messageNum = Integer.parseInt(event.getMessage().replaceAll(".* message ", "").trim());
            if (prevMessageNum >= 0)
                assertEquals("message sequence", prevMessageNum + 1, messageNum);
            prevMessageNum = messageNum;
        }
    }


    /**
     *  Reads all messages from a stream.
     */
    private LinkedHashSet<OutputLogEvent> retrieveAllMessages(String logStreamName) throws Exception
    {
        LinkedHashSet<OutputLogEvent> result = new LinkedHashSet<OutputLogEvent>();

        GetLogEventsRequest request = new GetLogEventsRequest()
                              .withLogGroupName(LOGGROUP_NAME)
                              .withLogStreamName(logStreamName)
                              .withStartFromHead(Boolean.TRUE);

        // once you've read all outstanding messages, the request will return the same token
        // so we try to read at least one response, and repeat until the token doesn't change

        String prevToken = "";
        String nextToken;
        do
        {
            GetLogEventsResult response = client.getLogEvents(request);
            result.addAll(response.getEvents());
            nextToken = response.getNextForwardToken();
            request.setNextToken(nextToken);
            prevToken = nextToken;
            Thread.sleep(500);
        } while (! prevToken.equals(nextToken));

        return result;
    }

}
