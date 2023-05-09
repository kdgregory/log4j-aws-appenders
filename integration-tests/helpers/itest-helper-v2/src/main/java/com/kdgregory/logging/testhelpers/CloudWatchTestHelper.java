// Copyright (c) Keith D Gregory
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.kdgregory.logging.testhelpers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static org.junit.Assert.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.*;


/**
 *  A collection of utility methods to support integration tests. This is an
 *  instantiable class, preserving the AWS client.
 *  <p>
 *  This is not intended for production use outside of this library.
 */
public class CloudWatchTestHelper
{
    private final static long WAIT_FOR_READY_TIMEOUT_MS     = 60000;
    private final static long WAIT_FOR_DELETED_TIMEOUT_MS   = 60000;

    private final static int RETRIEVE_RETRY_COUNT           = 5;

    private Logger localLogger = LoggerFactory.getLogger(getClass());

    private CloudWatchLogsClient client;
    private String logGroupName;


    public CloudWatchTestHelper(CloudWatchLogsClient client, String baseLogGroupName, String testName)
    {
        this.client = client;
        this.logGroupName = baseLogGroupName + "-" + testName;
    }


    /**
     *  Returns the constructed log group name.
     */
    public String getLogGroupName()
    {
        return logGroupName;
    }


    /**
     *  Asserts that the stream contains the expected number of messages, and that
     *  they're in order (including case where writes are from different threads,
     *  as long as they follow the standard pattern).
     *  <p>
     *  Message count assertion is approximate, +/- 5 messages, to compensate for
     *  logstream rotation testing. This function returns the number of messages
     *  from the given stream so that they can be aggregated in multi-thread tests.
     */
    public int assertMessages(String logStreamName, int expectedMessageCount) throws Exception
    {
        List<OutputLogEvent> events = retrieveAllMessages(logStreamName, expectedMessageCount);

        assertEquals("number of events in " + logStreamName, expectedMessageCount, events.size());

        Map<Integer,Integer> lastMessageByThread = new HashMap<Integer,Integer>();
        for (OutputLogEvent event : events)
        {
            String message = event.message().trim();
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
        return events.size();
    }


    /**
     *  Reads all messages from a stream.
     *  <p>
     *  To work around eventual consistency, you can pass in an expected message
     *  count, and the function will make several attempts to read that number of
     *  messages, with a sleep in the middle. To read whatever's available, pass 0.
     */
    public List<OutputLogEvent> retrieveAllMessages(String logStreamName, int expectedMessageCount)
    throws Exception
    {
        List<OutputLogEvent> result = new ArrayList<OutputLogEvent>();

        localLogger.debug("retrieving messages from {}", logStreamName);

        ensureLogStreamAvailable(logStreamName);

        for (int retry = 0 ; retry < RETRIEVE_RETRY_COUNT ; retry++)
        {
            result.clear();

            GetLogEventsRequest request = GetLogEventsRequest.builder()
                                          .logGroupName(logGroupName)
                                          .logStreamName(logStreamName)
                                          .startFromHead(Boolean.TRUE)
                                          .build();
            for (OutputLogEvent event : client.getLogEventsPaginator(request).events())
            {
                result.add(event);
            }

            if ((expectedMessageCount == 0) || (result.size() >= expectedMessageCount))
                break;

            Thread.sleep(2000);
        }

        localLogger.debug("retrieved {} messages from {}", result.size(), logStreamName);
        return result;
    }


    /**
     *  Returns a description of the log group, rhtowing if it doesn't exist.
     */
    public LogGroup describeLogGroup()
    throws Exception
    {
        LogGroup group = describeLogGroupIfAvailable();
        if (group != null)
            return group;
        else
            throw new IllegalStateException("log group does not exist: " + logGroupName);
    }


    /**
     *  Returns a description of the log group, null if it's not available.
     */
    public LogGroup describeLogGroupIfAvailable()
    throws Exception
    {
        DescribeLogGroupsRequest request = DescribeLogGroupsRequest.builder()
                                           .logGroupNamePrefix(logGroupName)
                                           .build();
        for (LogGroup group : client.describeLogGroupsPaginator(request).logGroups())
        {
            if (group.logGroupName().equals(logGroupName))
                return group;
        }
        return null;
    }


    /**
     *  Creates the log group and waits for it to become available.
     */
    public void createLogGroup()
    throws Exception
    {
        localLogger.debug("creating log group {}", logGroupName);

        CreateLogGroupRequest request = CreateLogGroupRequest.builder()
                                        .logGroupName(logGroupName)
                                        .build();
        client.createLogGroup(request);

        localLogger.debug("waiting for group {} to be available", logGroupName);

        long timeoutAt = System.currentTimeMillis() + WAIT_FOR_READY_TIMEOUT_MS;
        while (System.currentTimeMillis() < timeoutAt)
        {
            LogGroup group = describeLogGroupIfAvailable();
            if (group != null)
                return;
            Thread.sleep(100);
        }
        fail("group \"" + logGroupName + "\" wasn't ready within " + WAIT_FOR_READY_TIMEOUT_MS/1000 + " seconds");
    }


    /**
     *  We leave the log group for post-mortem analysis, but want to ensure
     *  that it's gone before starting a new test.
     */
    public void deleteLogGroupIfExists()
    throws Exception
    {
        localLogger.debug("deleting log group {}", logGroupName);

        try
        {
            DeleteLogGroupRequest request = DeleteLogGroupRequest.builder().logGroupName(logGroupName).build();
            client.deleteLogGroup(request);
        }
        catch (ResourceNotFoundException ignored)
        {
            // this gets thrown if we deleted a non-existent log group; that's OK
            return;
        }

        long timeoutAt = System.currentTimeMillis() + WAIT_FOR_DELETED_TIMEOUT_MS;
        while (System.currentTimeMillis() < timeoutAt)
        {
            DescribeLogGroupsRequest request = DescribeLogGroupsRequest.builder().logGroupNamePrefix(logGroupName).build();
            DescribeLogGroupsResponse response = client.describeLogGroups(request);
            if ((response.logGroups() == null) || (response.logGroups().isEmpty()))
            {
                return;
            }
            Thread.sleep(1000);
        }
        fail("log group\"" + logGroupName + "\" still exists after " + WAIT_FOR_DELETED_TIMEOUT_MS / 1000 + " seconds");
    }


    /**
     *  Determines whether the named log stream is available.
     */
    public boolean isLogStreamAvailable(String logStreamName)
    {
        List<LogStream> streams = null;
        try
        {
            DescribeLogStreamsRequest reqest = DescribeLogStreamsRequest.builder()
                                               .logGroupName(logGroupName)
                                               .logStreamNamePrefix(logStreamName)
                                               .build();
            DescribeLogStreamsResponse response = client.describeLogStreams(reqest);
            streams = response.logStreams();
        }
        catch (ResourceNotFoundException ignored)
        {
            // this indicates that the log group isn't available, so fall through
        }

        return ((streams != null) && (streams.size() > 0));
    }


    /**
     *  Waits until the named logstream is available, throwing if it isn't available
     *  after one minute. If the log group isn't available, that's considered equal
     *  to the stream not being ready.
     */
    public void ensureLogStreamAvailable(String logStreamName)
    throws Exception
    {
        localLogger.debug("waiting for stream {} to be available", logStreamName);

        long timeoutAt = System.currentTimeMillis() + WAIT_FOR_READY_TIMEOUT_MS;
        while (System.currentTimeMillis() < timeoutAt)
        {
            if (isLogStreamAvailable(logStreamName))
                return;

            Thread.sleep(1000);
        }
        fail("stream \"" + logGroupName + "/" + logStreamName + "\" wasn't ready within " + WAIT_FOR_READY_TIMEOUT_MS/1000 + " seconds");
    }


    /**
     *  Creates the named log stream, waiting for it to be available.
     */
    public void createLogStream(String logStreamName)
    throws Exception
    {
        localLogger.debug("creating log stream {}", logStreamName);

        CreateLogStreamRequest request = CreateLogStreamRequest.builder()
                                         .logGroupName(logGroupName)
                                         .logStreamName(logStreamName)
                                         .build();
        client.createLogStream(request);
        ensureLogStreamAvailable(logStreamName);
    }


    /**
     *  Deletes the log stream, waiting for it to be gone.
     */
    public void deleteLogStream(String logStreamName)
    throws Exception
    {
        localLogger.debug("deleting log stream {}", logStreamName);

        DeleteLogStreamRequest request = DeleteLogStreamRequest.builder()
                                         .logGroupName(logGroupName)
                                         .logStreamName(logStreamName)
                                         .build();
        client.deleteLogStream(request);

        boolean stillExists = true;
        long timeoutAt = System.currentTimeMillis() + WAIT_FOR_DELETED_TIMEOUT_MS;
        while (stillExists && (System.currentTimeMillis() < timeoutAt))
        {
            stillExists = isLogStreamAvailable(logStreamName);
        }
        assertFalse("stream was removed", stillExists);
    }
}
