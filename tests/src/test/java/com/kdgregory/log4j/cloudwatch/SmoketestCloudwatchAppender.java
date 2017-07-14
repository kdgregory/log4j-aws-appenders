// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.cloudwatch;

import java.net.URL;
import java.util.LinkedHashSet;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.*;



public class SmoketestCloudwatchAppender
{
    private final static String LOGGROUP_NAME = "TestCloudwatchAppender";
    private final static long READ_TIMEOUT = 2000;

    private AWSLogsClient client;


    @Before
    public void setUp() throws Exception
    {
        URL config = ClassLoader.getSystemResource("TestCloudwatchAppender-smoketest.properties");
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
        CloudwatchAppender appender = (CloudwatchAppender)logger.getAppender("test");

        logger.debug("message 1");
        logger.debug("message 2");
        logger.debug("message 3");
        
        appender.lastRotationTimestamp = System.currentTimeMillis() - 86400000;
        
        logger.debug("message 4");
        logger.debug("message 5");
        
        LinkedHashSet<OutputLogEvent> messages0 = retrieveAllMessages("smoketest-0");
        assertEquals("number of messages in first stream", 3, messages0.size());
        
        LinkedHashSet<OutputLogEvent> messages1 = retrieveAllMessages("smoketest-1");
        assertEquals("number of messages in second stream", 2, messages1.size());
    }


    private OutputLogEvent lookForMessage(String logStreamName, String message) throws Exception
    {
        for (OutputLogEvent event : retrieveAllMessages(logStreamName))
        {
            if (event.getMessage().contains(message))
                return event;
        }
        return null;
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
