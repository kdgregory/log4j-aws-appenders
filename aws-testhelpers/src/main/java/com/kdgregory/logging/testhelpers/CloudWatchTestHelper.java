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

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import static org.junit.Assert.*;

import net.sf.kdgcommons.collections.CollectionUtil;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;


/**
 *  A collection of utility methods to support integration tests. This is an
 *  instantiable class, preserving the AWS client.
 *  <p>
 *  This is not intended for production use outside of this library.
 */
public class CloudWatchTestHelper
{
    private AWSLogs client;
    private String logGroupName;


    public CloudWatchTestHelper(AWSLogs client, String logGroupName)
    {
        this.client = client;
        this.logGroupName = logGroupName;
    }


    /**
     *  Asserts that the stream contains the expected number of messages, and that
     *  they're in order. Properly handles multi-threaded writes.
     */
    public void assertMessages(String logStreamName, int expectedMessageCount) throws Exception
    {
        LinkedHashSet<OutputLogEvent> events = retrieveAllMessages(logStreamName);
        assertEquals("number of events in " + logStreamName, expectedMessageCount, events.size());

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
    public LinkedHashSet<OutputLogEvent> retrieveAllMessages(String logStreamName)
    throws Exception
    {
        LinkedHashSet<OutputLogEvent> result = new LinkedHashSet<OutputLogEvent>();

        ensureLogStreamAvailable(logStreamName);
        GetLogEventsRequest request = new GetLogEventsRequest()
                              .withLogGroupName(logGroupName)
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
    public void ensureLogStreamAvailable(String logStreamName)
    throws Exception
    {
        for (int ii = 0 ; ii < 60 ; ii++)
        {
            try
            {
                DescribeLogStreamsRequest reqest = new DescribeLogStreamsRequest()
                                                   .withLogGroupName(logGroupName)
                                                   .withLogStreamNamePrefix(logStreamName);
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
        fail("stream \"" + logGroupName + "/" + logStreamName + "\" wasn't ready within 60 seconds");
    }


    /**
     *  We leave the log group for post-mortem analysis, but want to ensure
     *  that it's gone before starting a new test.
     */
    public void deleteLogGroupIfExists()
    throws Exception
    {
        try
        {
            client.deleteLogGroup(new DeleteLogGroupRequest().withLogGroupName(logGroupName));
        }
        catch (ResourceNotFoundException ignored)
        {
            // this gets thrown if we deleted a non-existent log group; that's OK
            return;
        }

        for (int ii = 0 ; ii < 60 ; ii++)
        {
            DescribeLogGroupsRequest request = new DescribeLogGroupsRequest().withLogGroupNamePrefix(logGroupName);
            DescribeLogGroupsResult response = client.describeLogGroups(request);
            if ((response.getLogGroups() == null) || (response.getLogGroups().size() == 0))
            {
                return;
            }
            Thread.sleep(1000);
        }
        fail("log group\"" + logGroupName + "\" still exists after 60 seconds");
    }


    /**
     *  Deletes the log stream, waiting for it to be gone.
     */
    public void deleteLogStream(String logStreamName)
    throws Exception
    {
        client.deleteLogStream(new DeleteLogStreamRequest().withLogGroupName(logGroupName).withLogStreamName(logStreamName));
        boolean stillExists = true;
        for (int ii = 0 ; ii < 60 && stillExists ; ii++)
        {
            DescribeLogStreamsResult describeResult = client.describeLogStreams(
                                                            new DescribeLogStreamsRequest()
                                                            .withLogGroupName(logGroupName)
                                                            .withLogStreamNamePrefix(logStreamName));
            stillExists = CollectionUtil.isNotEmpty(describeResult.getLogStreams());
        }
        assertFalse("stream was removed", stillExists);
    }
}
