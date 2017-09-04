// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.log4j.aws.internal.AbstractAppender;
import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchLogWriter;


public class TestCloudWatchAppender
{
    // CHANGE THESE IF YOU CHANGE THE CONFIG
    private final static String LOGGER_NAME     = "SmoketestCloudWatchAppender";
    private final static String LOGGROUP_NAME   = "AppenderIntegratonTest";
    private final static String LOGSTREAM_BASE  = "AppenderTest-";
    private final static int    ROTATION_COUNT  = 333;

    private Logger testLogger = Logger.getLogger(getClass());
    private AWSLogs client;


    @Before
    public void setUp() throws Exception
    {
        URL config = ClassLoader.getSystemResource("CloudWatchAppenderSmoketest.properties");
        PropertyConfigurator.configure(config);

        client = AWSLogsClientBuilder.defaultClient();
        deleteLogGroupIfExists();
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
        CloudWatchAppender appender = (CloudWatchAppender)logger.getAppender("test");

        (new MessageWriter(logger, numMessages)).run();

        testLogger.info("smoketest: all messages written; sleeping to give writers chance to run");
        Thread.sleep(3000);

        assertMessages(LOGSTREAM_BASE + "1", ROTATION_COUNT);
        assertMessages(LOGSTREAM_BASE + "2", ROTATION_COUNT);
        assertMessages(LOGSTREAM_BASE + "3", ROTATION_COUNT);
        assertMessages(LOGSTREAM_BASE + "4", numMessages % ROTATION_COUNT);

        CloudWatchLogWriter lastWriter = getWriter(appender);
        assertEquals("number of batches for last writer", 1, lastWriter.getBatchCount());

        // while we're here, verify that batch delay is propagated
        appender.setBatchDelay(1234L);
        assertEquals("batch delay", 1234L, lastWriter.getBatchDelay());

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

        testLogger.info("concurrencyTest: all messages written; sleeping to give writers chance to run");
        Thread.sleep(3000);

        assertMessages(LOGSTREAM_BASE + "1", ROTATION_COUNT);
        assertMessages(LOGSTREAM_BASE + "2", ROTATION_COUNT);
        assertMessages(LOGSTREAM_BASE + "3", ROTATION_COUNT);
        assertMessages(LOGSTREAM_BASE + "4", totalMessageCount % ROTATION_COUNT);

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


    /**
     *  Asserts that the stream contains the expected number of messages, and that
     *  they're in order. Properly handles multi-threaded writes.
     */
    private void assertMessages(String streamName, int expectedMessageCount) throws Exception
    {
        LinkedHashSet<OutputLogEvent> events = retrieveAllMessages(streamName);
        assertEquals("number of events in " + streamName, expectedMessageCount, events.size());

        Pattern messagePattern = Pattern.compile(".*message on thread (\\d+): (\\d+)");

        Map<Integer,Integer> lastMessageByThread = new HashMap<Integer,Integer>();
        for (OutputLogEvent event : events)
        {
            String message = event.getMessage().trim();
            Matcher matcher = messagePattern.matcher(message);
            assertTrue("message matches pattern: " + message, matcher.matches());

            Integer threadNum = Integer.valueOf(matcher.group(1));
            Integer messageNum = Integer.valueOf(matcher.group(2));
            Integer prevMessageNum = lastMessageByThread.get(threadNum);
            if (prevMessageNum == null)
            {
                lastMessageByThread.put(threadNum, messageNum);
            }
            else
            {
                assertTrue("previous message (" + prevMessageNum + ") lower than current (" + messageNum + ")",
                           prevMessageNum.intValue() < messageNum.intValue());
            }
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


    /**
     *  We leave the log group for post-mortem analysis, but want to ensure
     *  that it's gone before starting a new test.
     */
    private void deleteLogGroupIfExists() throws Exception
    {
        try
        {
            client.deleteLogGroup(new DeleteLogGroupRequest().withLogGroupName(LOGGROUP_NAME));
            while (true)
            {
                DescribeLogGroupsRequest request = new DescribeLogGroupsRequest().withLogGroupNamePrefix(LOGGROUP_NAME);
                DescribeLogGroupsResult response = client.describeLogGroups(request);
                if ((response.getLogGroups() == null) || (response.getLogGroups().size() == 0))
                {
                    return;
                }
                Thread.sleep(250);
            }
        }
        catch (ResourceNotFoundException ignored)
        {
            // this gets thrown if we deleted a non-existent log group; that's OK
        }
    }


    // this is a hack, to avoid duplicating or exposing TestableCloudWatchAppender
    private CloudWatchLogWriter getWriter(CloudWatchAppender appender) throws Exception
    {
        Field writerField = AbstractAppender.class.getDeclaredField("writer");
        writerField.setAccessible(true);
        return (CloudWatchLogWriter)writerField.get(appender);
    }
}
