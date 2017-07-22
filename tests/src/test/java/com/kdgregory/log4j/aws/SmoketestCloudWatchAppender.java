// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.net.URL;
import java.util.LinkedHashSet;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.*;


public class SmoketestCloudWatchAppender
{
    private final static String LOGGROUP_NAME = "TestCloudwatchAppender";
    private final static long READ_TIMEOUT = 2000;

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

        for (int ii = 0 ; ii < 1000 ; ii++)
        {
            logger.debug("message " + ii);
        }

        // this is a hack: since batches are only formed by append(), the test will end
        // with one message still in the appender and not moved to the writer
        // ... batching should be managed in the writer
        LogManager.shutdown();

        assertMessages("smoketest-1", 333);
        assertMessages("smoketest-2", 333);
        assertMessages("smoketest-3", 333);
        assertMessages("smoketest-4", 1);
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
        long start = System.currentTimeMillis();
        LinkedHashSet<OutputLogEvent> result = new LinkedHashSet<OutputLogEvent>();

        GetLogEventsRequest request = new GetLogEventsRequest()
                              .withLogGroupName(LOGGROUP_NAME)
                              .withLogStreamName(logStreamName);

        while (System.currentTimeMillis() < start + READ_TIMEOUT)
        {
            try
            {
                GetLogEventsResult response = client.getLogEvents(request);
                result.addAll(response.getEvents());
            }
            catch (ResourceNotFoundException ignored)
            {
                // eventual consistency means maybe the stream might not be readable yet
            }
            Thread.sleep(250);
        }
        return result;
    }

}
