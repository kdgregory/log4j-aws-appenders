// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import java.util.regex.Matcher;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.log4j.aws.internal.AbstractAppender;
import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.log4j.aws.testhelpers.MessageWriter;


public class CloudWatchAppenderIntegrationTest
{
    // CHANGE THESE IF YOU CHANGE THE CONFIG
    private final static String LOGGROUP_NAME   = "AppenderIntegratonTest";
    private final static String LOGSTREAM_BASE  = "AppenderTest-";

    private Logger mainLogger;
    private AWSLogs client;


    public void setUp(String propertiesName) throws Exception
    {
        URL config = ClassLoader.getSystemResource(propertiesName);
        PropertyConfigurator.configure(config);

        mainLogger = Logger.getLogger(getClass());
        client = AWSLogsClientBuilder.defaultClient();
        deleteLogGroupIfExists();
    }


//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void smoketest() throws Exception
    {
        setUp("CloudWatchAppenderIntegrationTest-smoketest.properties");
        mainLogger.info("smoketest: starting");

        final int numMessages = 1001;
        final int rotationCount  = 333;

        Logger testLogger = Logger.getLogger("TestLogger");
        CloudWatchAppender appender = (CloudWatchAppender)testLogger.getAppender("test");

        (new MessageWriter(testLogger, numMessages)).run();

        mainLogger.info("smoketest: all messages written; sleeping to give writers chance to run");
        Thread.sleep(5000);

        assertMessages(LOGSTREAM_BASE + "1", rotationCount);
        assertMessages(LOGSTREAM_BASE + "2", rotationCount);
        assertMessages(LOGSTREAM_BASE + "3", rotationCount);
        assertMessages(LOGSTREAM_BASE + "4", numMessages % rotationCount);

        CloudWatchLogWriter lastWriter = getWriter(appender);
        assertEquals("number of batches for last writer", 1, lastWriter.getBatchCount());

        // while we're here, verify that batch delay is propagated
        appender.setBatchDelay(1234L);
        assertEquals("batch delay", 1234L, lastWriter.getBatchDelay());

        mainLogger.info("smoketest: finished");
    }


    @Test
    public void testMultipleThreadsSingleAppender() throws Exception
    {
        setUp("CloudWatchAppenderIntegrationTest-testMultipleThreadsSingleAppender.properties");
        mainLogger.info("multi-thread/single-appender: starting");

        final int messagesPerThread = 200;
        final int rotationCount  = 333;

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

        mainLogger.info("multi-thread/single-appender: all threads started; sleeping to give writer chance to run");
        Thread.sleep(3000);

        assertMessages(LOGSTREAM_BASE + "1", rotationCount);
        assertMessages(LOGSTREAM_BASE + "2", rotationCount);
        assertMessages(LOGSTREAM_BASE + "3", rotationCount);
        assertMessages(LOGSTREAM_BASE + "4", (messagesPerThread * writers.length) % rotationCount);

        mainLogger.info("multi-thread/single-appender: finished");
    }


    @Test
    public void testMultipleThreadsMultipleAppenders() throws Exception
    {
        setUp("CloudWatchAppenderIntegrationTest-testMultipleThreadsMultipleAppenders.properties");
        mainLogger.info("multi-thread/multi-appender: starting");

        final int messagesPerThread = 300;

        MessageWriter.runOnThreads(
            new MessageWriter(Logger.getLogger("TestLogger1"), messagesPerThread),
            new MessageWriter(Logger.getLogger("TestLogger2"), messagesPerThread),
            new MessageWriter(Logger.getLogger("TestLogger3"), messagesPerThread));

        mainLogger.info("multi-thread/multi-appender: all threads started; sleeping to give writer chance to run");
        Thread.sleep(3000);

        assertMessages(LOGSTREAM_BASE + "1", messagesPerThread);
        assertMessages(LOGSTREAM_BASE + "2", messagesPerThread);
        assertMessages(LOGSTREAM_BASE + "3", messagesPerThread);

        mainLogger.info("multi-thread/multi-appender: finished");
    }


//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Asserts that the stream contains the expected number of messages, and that
     *  they're in order. Properly handles multi-threaded writes.
     */
    private void assertMessages(String streamName, int expectedMessageCount) throws Exception
    {
        LinkedHashSet<OutputLogEvent> events = retrieveAllMessages(streamName);
        assertEquals("number of events in " + streamName, expectedMessageCount, events.size());

        Map<Integer,Integer> lastMessageByThread = new HashMap<Integer,Integer>();
        for (OutputLogEvent event : events)
        {
            String message = event.getMessage().trim();
            Matcher matcher = MessageWriter.PATTERN.matcher(message);
            assertTrue("message matches pattern: " + message, matcher.matches());

            Integer threadNum = MessageWriter.getThreadId(matcher);
            Integer messageNum = MessageWriter.getMessageNumber(matcher);
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

        ensureLogStreamAvailable(logStreamName);
        GetLogEventsRequest request = new GetLogEventsRequest()
                              .withLogGroupName(LOGGROUP_NAME)
                              .withLogStreamName(logStreamName)
                              .withStartFromHead(Boolean.TRUE);

        // once you've read all outstanding messages, the request will return the same token
        // so we try to read at least one response, and repeat until the token doesn't change

        String prevToken = "";
        String nextToken = "";
        do
        {
            prevToken = nextToken;
            GetLogEventsResult response = client.getLogEvents(request);
            result.addAll(response.getEvents());
            nextToken = response.getNextForwardToken();
            request.setNextToken(nextToken);
            Thread.sleep(500);
        } while (! prevToken.equals(nextToken));

        return result;
    }


    /**
     *  Waits until the named logstream is available, throwing if it isn't available
     *  after one minute. If the log group isn't available, that's considered equal
     *  to the stream not being ready.
     */
    private void ensureLogStreamAvailable(String streamName)
    throws Exception
    {
        for (int ii = 0 ; ii < 60 ; ii++)
        {
            try
            {
                DescribeLogStreamsRequest reqest = new DescribeLogStreamsRequest()
                                                   .withLogGroupName(LOGGROUP_NAME)
                                                   .withLogStreamNamePrefix(streamName);
                DescribeLogStreamsResult response = client.describeLogStreams(reqest);
                List<LogStream> streams = response.getLogStreams();
                if ((streams != null) && (streams.size() > 0))
                {
                    return;
                }
            }
            catch (ResourceNotFoundException ignored)
            {
                // this indicates that the log group isn't available
            }
            Thread.sleep(1000);
        }
        fail("stream not ready within 60 seconds");
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
