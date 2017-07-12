// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.cloudwatch;

import java.net.URL;

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
    private final static long ASSERTION_TIMEOUT = 2000;

    private AWSLogsClient client;


    private OutputLogEvent lookForMessage(String logStreamName, String message) throws Exception
    {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() < start + ASSERTION_TIMEOUT)
        {
            GetLogEventsRequest request = new GetLogEventsRequest()
                                          .withLogGroupName(LOGGROUP_NAME)
                                          .withLogStreamName(logStreamName);
            try
            {
                GetLogEventsResult logEvents = client.getLogEvents(request);
                for (OutputLogEvent event : logEvents.getEvents())
                {
                    if (event.getMessage().contains(message))
                        return event;
                }
            }
            catch (ResourceNotFoundException ignored)
            {
                // eventual consistency means maybe the stream isn't readable yet
            }
            Thread.sleep(100);
        }
        return null;
    }


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

        String message = "can you hear me now?";
        logger.debug(message);

        assertNotNull("unable to find message before timeout",
                      lookForMessage("smoketest", message));
    }

}
