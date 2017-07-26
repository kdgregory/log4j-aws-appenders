// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.net.URL;
import java.util.LinkedHashSet;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchLogWriter;


public class SmoketestCloudWatchAppender
{
    private final static String LOGGROUP_NAME = "TestCloudWatchAppender";

    private AWSLogsClient client;


    @Before
    public void setUp() throws Exception
    {
        URL config = ClassLoader.getSystemResource("SmoketestCloudWatchAppender.properties");
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
        client.createLogGroup(new CreateLogGroupRequest().withLogGroupName(LOGGROUP_NAME));
    }


    @Test
    public void smoketest() throws Exception
    {
        Logger logger = Logger.getLogger(getClass());
        CloudWatchAppender appender = (CloudWatchAppender)logger.getAppender("test");

        for (int ii = 0 ; ii < 1001 ; ii++)
        {
            logger.debug("message " + ii);
        }

        // this is a multiple of the batch size, so should give all writers a chance to write
        // all of their messages before shutdown
        Thread.sleep(3000);

        assertMessages("smoketest-1", 333);
        assertMessages("smoketest-2", 333);
        assertMessages("smoketest-3", 333);
        assertMessages("smoketest-4", 2);

        CloudWatchLogWriter lastWriter = (CloudWatchLogWriter)appender.writer;
        assertEquals("number of batches for last writer", 1, lastWriter.getBatchCount());

        // while we're here, verify that batch delay is propagated
        appender.setBatchDelay(1234L);
        assertEquals("batch delay", 1234L, lastWriter.getBatchDelay());
    }


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
            Thread.sleep(250);
        } while (! prevToken.equals(nextToken));

        return result;
    }

}
