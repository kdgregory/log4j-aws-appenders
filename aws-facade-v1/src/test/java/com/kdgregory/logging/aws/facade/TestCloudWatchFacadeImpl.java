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

package com.kdgregory.logging.aws.facade;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.test.StringAsserts;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.*;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.facade.v1.CloudWatchFacadeImpl;
import com.kdgregory.logging.aws.internal.facade.CloudWatchFacade;
import com.kdgregory.logging.aws.internal.facade.CloudWatchFacadeException;
import com.kdgregory.logging.aws.internal.facade.CloudWatchFacadeException.ReasonCode;
import com.kdgregory.logging.aws.testhelpers.MockCloudWatchClient;
import com.kdgregory.logging.common.LogMessage;


public class TestCloudWatchFacadeImpl
{
    private final static List<String> TEST_LOG_GROUPS = Arrays.asList("argle", "bargle", "wargle");
    private final static List<String> TEST_LOG_STREAMS = Arrays.asList("foo", "bar", "baz");

    private MockCloudWatchClient mock = new MockCloudWatchClient(TEST_LOG_GROUPS, TEST_LOG_STREAMS);

    // note: group/stream names are from the end of the above list to verify pagination
    private CloudWatchWriterConfig config = new CloudWatchWriterConfig()
                                            .setLogGroupName("wargle")
                                            .setLogStreamName("baz");

    // note: can update config or mock any time before making first call
    private CloudWatchFacade facade = new CloudWatchFacadeImpl(config)
    {
        @Override
        protected AWSLogs client()
        {
            if (client == null)
            {
                client = mock.createClient();
            }
            return client;
        }
    };


//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Verifies that an exception contains a properly structured message.
     */
    private void assertException(
        CloudWatchFacadeException ex,
        String expectedFunctionName, String expectedContainedMessage,
        ReasonCode expectedReason, Throwable expectedCause)
    {
        assertEquals("exception reason",  expectedReason, ex.getReason());

        assertTrue("exception identifies function (was: " + ex.getMessage() + ")",
                   ex.getMessage().contains(expectedFunctionName));

        assertTrue("exception contains expected message (was: " + ex.getMessage() + ")",
                   ex.getMessage().contains(expectedContainedMessage));

        assertTrue("exception identifies log group (was: " + ex.getMessage() + ")",
                   ex.getMessage().contains(config.getLogGroupName()));

        if (config.getLogStreamName() != null)
        {
            assertTrue("exception identifies log stream (was: " + ex.getMessage() + ")",
                       ex.getMessage().contains(config.getLogStreamName()));
        }

        if (expectedCause != null)
        {
            assertSame("exception contains cause", expectedCause, ex.getCause());
        }
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testFindLogGroupHappyPath() throws Exception
    {
        String result = facade.findLogGroup();

        StringAsserts.assertRegex("returned log group ARN (was: " + result + ")",
                                  "arn.*log-group:wargle",
                                  result);

        // common set of asserts; here it asserts we make no unnecessary calls
        assertEquals("calls to describeLogGroups",      1,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     0,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       0,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testFindLogGroupPaginated() throws Exception
    {
        mock = new MockCloudWatchClient(TEST_LOG_GROUPS, 2, TEST_LOG_STREAMS, 2);

        String result = facade.findLogGroup();

        StringAsserts.assertRegex("returned log group ARN (was: " + result + ")",
                                  "arn.*log-group:wargle",
                                  result);

        assertEquals("calls to describeLogGroups",      2,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     0,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       0,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testFindLogGroupNoSuchGroup() throws Exception
    {
        mock = new MockCloudWatchClient(Collections.emptyList(), Collections.emptyList());

        String result = facade.findLogGroup();

        assertNull("missing log group returns null",        result);

        assertEquals("calls to describeLogGroups",      1,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     0,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       0,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testFindLogGroupThrottled() throws Exception
    {
        mock = new MockCloudWatchClient(TEST_LOG_GROUPS, TEST_LOG_STREAMS)
        {
            @Override
            protected DescribeLogGroupsResult describeLogGroups(DescribeLogGroupsRequest request)
            {
                AWSLogsException ex = new AWSLogsException("message doesn't matter");
                ex.setErrorCode("ThrottlingException");  // copied from real life
                throw ex;
            }
        };

        String result = facade.findLogGroup();

        assertNull("throttling returns null",               result);

        assertEquals("calls to describeLogGroups",      1,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     0,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       0,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testFindLogGroupAborted() throws Exception
    {
        mock = new MockCloudWatchClient(TEST_LOG_GROUPS, TEST_LOG_STREAMS)
        {
            @Override
            protected DescribeLogGroupsResult describeLogGroups(DescribeLogGroupsRequest request)
            {
                throw new OperationAbortedException("message doesn't matter");
            }
        };

        String result = facade.findLogGroup();

        assertNull("aborted operation returns null",    result);

        assertEquals("calls to describeLogGroups",      1,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     0,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       0,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testFindLogGroupError() throws Exception
    {
        final RuntimeException cause = new RuntimeException();
        mock = new MockCloudWatchClient(TEST_LOG_GROUPS, TEST_LOG_STREAMS)
        {
            @Override
            protected DescribeLogGroupsResult describeLogGroups(DescribeLogGroupsRequest request)
            {
                throw cause;
            }
        };

        try
        {
            facade.findLogGroup();
            fail("should have thrown");
        }
        catch (CloudWatchFacadeException ex)
        {
            assertException(ex, "findLogGroup", "unexpected", ReasonCode.UNEXPECTED_EXCEPTION, cause);
        }

        assertEquals("calls to describeLogGroups",      1,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     0,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       0,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testCreateLogGroupHappyPath() throws Exception
    {
        facade.createLogGroup();

        assertEquals("calls to describeLogGroups",      0,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     0,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        1,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       0,  mock.createLogStreamInvocationCount);

        assertEquals("group name passed to create",     config.getLogGroupName(),   mock.createLogGroupGroupName);
    }


    @Test
    public void testCreateLogGroupAlreadyExists() throws Exception
    {
        mock = new MockCloudWatchClient(Collections.emptyList(), Collections.emptyList())
        {
            @Override
            protected CreateLogGroupResult createLogGroup(CreateLogGroupRequest request)
            {
                throw new ResourceAlreadyExistsException("message irrelevant");
            }
        };

        facade.createLogGroup();

        assertEquals("calls to describeLogGroups",      0,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     0,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        1,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       0,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testCreateLogGroupThrottled() throws Exception
    {
        mock = new MockCloudWatchClient(Collections.emptyList(), Collections.emptyList())
        {
            @Override
            protected CreateLogGroupResult createLogGroup(CreateLogGroupRequest request)
            {
                if (createLogGroupInvocationCount < 2)
                {
                    AWSLogsException ex = new AWSLogsException("message doesn't matter");
                    ex.setErrorCode("ThrottlingException");  // copied from real life
                    throw ex;
                }
                return super.createLogGroup(request);
            }
        };

        try
        {
            facade.createLogGroup();
            fail("should have thrown");
        }
        catch (CloudWatchFacadeException ex)
        {
            assertException(ex, "createLogGroup", "throttled", ReasonCode.THROTTLING, null);
        }

        assertEquals("calls to describeLogGroups",      0,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     0,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        1,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       0,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testCreateLogGroupAborted() throws Exception
    {
        mock = new MockCloudWatchClient(Collections.emptyList(), Collections.emptyList())
        {
            @Override
            protected CreateLogGroupResult createLogGroup(CreateLogGroupRequest request)
            {
                if (createLogGroupInvocationCount == 1)
                {
                    throw new OperationAbortedException("message irrelevant");
                }
                // in real life, this would be followed by ResourceAlreadyExistsException
                return super.createLogGroup(request);
            }
        };

        try
        {
            facade.createLogGroup();
            fail("should have thrown");
        }
        catch (CloudWatchFacadeException ex)
        {
            assertException(ex, "createLogGroup", "aborted", ReasonCode.ABORTED, null);
        }

        assertEquals("calls to describeLogGroups",      0,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     0,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        1,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       0,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testCreateLogGroupUnexpectedError() throws Exception
    {
        final RuntimeException cause = new RuntimeException();
        mock = new MockCloudWatchClient(Collections.emptyList(), Collections.emptyList())
        {
            @Override
            protected CreateLogGroupResult createLogGroup(CreateLogGroupRequest request)
            {
                throw cause;
            }
        };

        try
        {
            facade.createLogGroup();
            fail("should have thrown");
        }
        catch (CloudWatchFacadeException ex)
        {
            assertException(ex, "createLogGroup", "unexpected", ReasonCode.UNEXPECTED_EXCEPTION, null);
        }

        assertEquals("calls to describeLogGroups",      0,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     0,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        1,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       0,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testSetLogGroupRetentionHappyPath() throws Exception
    {
        config.setRetentionPeriod(Integer.valueOf(7));

        facade.setLogGroupRetention();

        assertEquals("calls to putRetentionPolicy",                 1,                          mock.putRetentionPolicyInvocationCount);
        assertEquals("log group name passed to putRetentionPolicy", config.getLogGroupName(),   mock.putRetentionPolicyGroupName);
        assertEquals("value passed to putRetentionPolicy",          Integer.valueOf(7),         mock.putRetentionPolicyValue);
    }


    @Test
    public void testSetLogGroupRetentionNoValue() throws Exception
    {
        facade.setLogGroupRetention();

        assertEquals("calls to putRetentionPolicy",     0,      mock.putRetentionPolicyInvocationCount);
    }


    @Test
    public void testSetLogGroupRetentionInvalidConfiguration() throws Exception
    {
        // setting the log group and stream name to values that I can then assert
        config.setLogGroupName("argle");
        config.setLogStreamName("bargle");
        config.setRetentionPeriod(Integer.valueOf(19));

        try
        {
            facade.setLogGroupRetention();
            fail("should have thrown");
        }
        catch (CloudWatchFacadeException ex)
        {
            assertException(ex, "setLogGroupRetention", "invalid retention period: 19", ReasonCode.INVALID_CONFIGURATION, null);
        }

        assertEquals("calls to putRetentionPolicy",                 1,                          mock.putRetentionPolicyInvocationCount);
        assertEquals("log group name passed to putRetentionPolicy", config.getLogGroupName(),   mock.putRetentionPolicyGroupName);
        assertEquals("value passed to putRetentionPolicy",          Integer.valueOf(19),         mock.putRetentionPolicyValue);
    }


    @Test
    public void testSetLogGroupRetentionUnexpectedError() throws Exception
    {
        final RuntimeException cause = new RuntimeException();
        mock = new MockCloudWatchClient(Collections.emptyList(), Collections.emptyList())
        {
            @Override
            protected PutRetentionPolicyResult putRetentionPolicy(PutRetentionPolicyRequest request)
            {
                throw cause;
            }
        };

        // setting the log group and stream name to values that I can then assert
        config.setLogGroupName("argle");
        config.setLogStreamName("bargle");
        config.setRetentionPeriod(Integer.valueOf(7));

        try
        {
            facade.setLogGroupRetention();
            fail("should have thrown");
        }
        catch (CloudWatchFacadeException ex)
        {
            assertException(ex, "setLogGroupRetention", "unexpected", ReasonCode.UNEXPECTED_EXCEPTION, cause);
        }

        assertEquals("calls to putRetentionPolicy",                 1,                          mock.putRetentionPolicyInvocationCount);
        assertEquals("log group name passed to putRetentionPolicy", config.getLogGroupName(),   mock.putRetentionPolicyGroupName);
        assertEquals("value passed to putRetentionPolicy",          Integer.valueOf(7),         mock.putRetentionPolicyValue);
    }


    @Test
    public void testCreateLogStreamHappyPath() throws Exception
    {
        facade.createLogStream();

        assertEquals("calls to describeLogGroups",      0,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     0,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       1,  mock.createLogStreamInvocationCount);

        assertEquals("group name passed to create",     config.getLogGroupName(),   mock.createLogStreamGroupName);
        assertEquals("stream name passed to create",    config.getLogStreamName(),  mock.createLogStreamStreamName);
    }


    @Test
    public void testCreateLogStreamAlreadyExists() throws Exception
    {
        mock = new MockCloudWatchClient(Collections.emptyList(), Collections.emptyList())
        {
            @Override
            protected CreateLogStreamResult createLogStream(CreateLogStreamRequest request)
            {
                throw new ResourceAlreadyExistsException("message irrelevant");
            }
        };

        facade.createLogStream();

        assertEquals("calls to describeLogGroups",      0,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     0,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       1,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testCreateLogStreamThrottled() throws Exception
    {
        mock = new MockCloudWatchClient(Collections.emptyList(), Collections.emptyList())
        {
            @Override
            protected CreateLogStreamResult createLogStream(CreateLogStreamRequest request)
            {
                if (createLogStreamInvocationCount < 2)
                {
                    AWSLogsException ex = new AWSLogsException("message doesn't matter");
                    ex.setErrorCode("ThrottlingException");  // copied from real life
                    throw ex;
                }
                return super.createLogStream(request);
            }
        };

        try
        {
            facade.createLogStream();
            fail("should have thrown");
        }
        catch (CloudWatchFacadeException ex)
        {
            assertException(ex, "createLogStream", "throttled", ReasonCode.THROTTLING, null);
        }

        assertEquals("calls to describeLogGroups",      0,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     0,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       1,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testCreateLogStreamAborted() throws Exception
    {
        mock = new MockCloudWatchClient(Collections.emptyList(), Collections.emptyList())
        {
            @Override
            protected CreateLogStreamResult createLogStream(CreateLogStreamRequest request)
            {
                if (createLogStreamInvocationCount < 2)
                {
                    throw new OperationAbortedException("message irrelevant");
                }
                // in real life, this would be followed by ResourceAlreadyExistsException
                return super.createLogStream(request);
            }
        };

        try
        {
            facade.createLogStream();
            fail("should have thrown");
        }
        catch (CloudWatchFacadeException ex)
        {
            assertException(ex, "createLogStream", "aborted", ReasonCode.ABORTED, null);
        }

        assertEquals("calls to describeLogGroups",      0,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     0,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       1,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testCreateLogStreamNoLogGroup() throws Exception
    {
        mock = new MockCloudWatchClient(Collections.emptyList(), Collections.emptyList())
        {
            @Override
            protected CreateLogStreamResult createLogStream(CreateLogStreamRequest request)
            {
                throw new ResourceNotFoundException("message irrelevant");
            }
        };

        try
        {
            facade.createLogStream();
            fail("should have thrown");
        }
        catch (CloudWatchFacadeException ex)
        {
            assertException(ex, "createLogStream", "missing", ReasonCode.MISSING_LOG_GROUP, null);
        }

        assertEquals("calls to describeLogGroups",      0,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     0,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       1,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testCreateLogStreamUnexpectedError() throws Exception
    {
        RuntimeException cause = new RuntimeException("message irrelevant");
        mock = new MockCloudWatchClient(Collections.emptyList(), Collections.emptyList())
        {
            @Override
            protected CreateLogStreamResult createLogStream(CreateLogStreamRequest request)
            {
                throw cause;
            }
        };

        try
        {
            facade.createLogStream();
            fail("should have thrown");
        }
        catch (CloudWatchFacadeException ex)
        {
            assertException(ex, "createLogStream", "unexpected", ReasonCode.UNEXPECTED_EXCEPTION, cause);
        }

        assertEquals("calls to describeLogGroups",      0,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     0,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       1,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testRetrieveSequenceTokenHappyPath() throws Exception
    {
        StringAsserts.assertNotEmpty("returned sequence token", facade.retrieveSequenceToken());

        assertEquals("calls to describeLogGroups",      0,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     1,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       0,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testRetrieveSequenceTokenPaginated() throws Exception
    {
        mock = new MockCloudWatchClient(TEST_LOG_GROUPS, 2, TEST_LOG_STREAMS, 2);

        StringAsserts.assertNotEmpty("returned sequence token", facade.retrieveSequenceToken());

        assertEquals("calls to describeLogGroups",      0,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     2,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       0,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testRetrieveSequenceTokenMissingStream() throws Exception
    {
        mock = new MockCloudWatchClient(TEST_LOG_GROUPS, Collections.emptyList());

        assertNull("returned sequence token", facade.retrieveSequenceToken());

        assertEquals("calls to describeLogGroups",      0,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     1,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       0,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testRetrieveSequenceTokenThrottled() throws Exception
    {
        mock = new MockCloudWatchClient(TEST_LOG_GROUPS, TEST_LOG_STREAMS)
        {
            @Override
            protected DescribeLogStreamsResult describeLogStreams(DescribeLogStreamsRequest request)
            {
                AWSLogsException ex = new AWSLogsException("message doesn't matter");
                ex.setErrorCode("ThrottlingException");  // copied from real life
                throw ex;
            }
        };

        assertNull("returned null sequence token",      facade.retrieveSequenceToken());

        assertEquals("calls to describeLogGroups",      0,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     1,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       0,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testRetrieveSequenceTokenAborted() throws Exception
    {
        mock = new MockCloudWatchClient(TEST_LOG_GROUPS, TEST_LOG_STREAMS)
        {
            @Override
            protected DescribeLogStreamsResult describeLogStreams(DescribeLogStreamsRequest request)
            {
                throw new OperationAbortedException("message doesn't matter");
            }
        };

        assertNull("returned null sequence token",      facade.retrieveSequenceToken());

        assertEquals("calls to describeLogGroups",      0,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     1,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       0,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testRetrieveSequenceTokenMissingLogGroup() throws Exception
    {
        mock = new MockCloudWatchClient(Collections.emptyList(), Collections.emptyList());

        assertNull("call returned null", facade.retrieveSequenceToken());

        assertEquals("calls to describeLogGroups",      0,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     1,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       0,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testRetrieveSequenceTokenUnexpectedError() throws Exception
    {
        RuntimeException cause = new RuntimeException();
        mock = new MockCloudWatchClient(TEST_LOG_GROUPS, TEST_LOG_STREAMS)
        {
            @Override
            protected DescribeLogStreamsResult describeLogStreams(DescribeLogStreamsRequest request)
            {
                throw cause;
            }
        };

        try
        {
            facade.retrieveSequenceToken();
            fail("should have thrown");
        }
        catch (CloudWatchFacadeException ex)
        {
            assertException(ex, "retrieveSequenceToken", "unexpected", ReasonCode.UNEXPECTED_EXCEPTION, null);
        }

        assertEquals("calls to describeLogGroups",      0,  mock.describeLogGroupsInvocationCount);
        assertEquals("calls to describeLogStreams",     1,  mock.describeLogStreamsInvocationCount);
        assertEquals("calls to createLogGroups",        0,  mock.createLogGroupInvocationCount);
        assertEquals("calls to createLogStreams",       0,  mock.createLogStreamInvocationCount);
    }


    @Test
    public void testSendMessagesHappyPath() throws Exception
    {
        long now = System.currentTimeMillis();

        LogMessage msg1 = new LogMessage(now - 10, "message 1");
        LogMessage msg2 = new LogMessage(now,      "message 2");
        LogMessage msg3 = new LogMessage(now + 10, "message 3");

        String sequenceToken = mock.getCurrentSequenceToken();
        List<LogMessage> messages = Arrays.asList(msg1, msg2, msg3);

        String newSequenceToken = facade.sendMessages(sequenceToken, messages);

        assertEquals("calls to putLogEvents",               1,                          mock.putLogEventsInvocationCount);
        assertEquals("group name passed to putLogEvents",   config.getLogGroupName(),   mock.putLogEventsGroupName);
        assertEquals("stream name passed to putLogEvents",  config.getLogStreamName(),  mock.putLogEventsStreamName);

        assertEquals("number of events passed to putLogEvents", 3,                          mock.putLogEventsEvents.size());
        assertEquals("event 0 timestamp",                       now - 10,                   mock.putLogEventsEvents.get(0).getTimestamp().longValue());
        assertEquals("event 0 message",                         "message 1",                mock.putLogEventsEvents.get(0).getMessage());
        assertEquals("event 1 timestamp",                       now,                        mock.putLogEventsEvents.get(1).getTimestamp().longValue());
        assertEquals("event 1 message",                         "message 2",                mock.putLogEventsEvents.get(1).getMessage());
        assertEquals("event 2 timestamp",                       now + 10,                   mock.putLogEventsEvents.get(2).getTimestamp().longValue());
        assertEquals("event 2 message",                         "message 3",                mock.putLogEventsEvents.get(2).getMessage());

        assertNotNull("returned sequence token",                                        newSequenceToken);
    }


    @Test
    public void testSendMessagesEmptyBatch() throws Exception
    {
        String sequenceToken = mock.getCurrentSequenceToken();
        List<LogMessage> messages = Arrays.asList();

        String newSequenceToken = facade.sendMessages(sequenceToken, messages);

        assertEquals("calls to putLogEvents",               0,                          mock.putLogEventsInvocationCount);
        assertEquals("returned sequence token",             sequenceToken,              newSequenceToken);
    }


    @Test
    public void testSendMessagesThrottling() throws Exception
    {
        mock = new MockCloudWatchClient(TEST_LOG_GROUPS, TEST_LOG_STREAMS)
        {
            @Override
            protected PutLogEventsResult putLogEvents(PutLogEventsRequest request)
            {
                AWSLogsException ex = new AWSLogsException("message doesn't matter");
                ex.setErrorCode("ThrottlingException");  // copied from real life
                throw ex;
            }
        };

        String sequenceToken = mock.getCurrentSequenceToken();
        List<LogMessage> messages = Arrays.asList(new LogMessage(0, "doesn't matter"));

        try
        {
            facade.sendMessages(sequenceToken, messages);
            fail("should have thrown");
        }
        catch (CloudWatchFacadeException ex)
        {
            assertException(ex, "sendMessages", "throttled", ReasonCode.THROTTLING, null);
        }

        assertEquals("calls to putLogEvents",                   1,                          mock.putLogEventsInvocationCount);
        assertEquals("group name passed to putLogEvents",       config.getLogGroupName(),   mock.putLogEventsGroupName);
        assertEquals("stream name passed to putLogEvents",      config.getLogStreamName(),  mock.putLogEventsStreamName);
        assertEquals("number of events passed to putLogEvents", 1,                          mock.putLogEventsEvents.size());
    }


    @Test
    public void testSendMessagesInvalidSequenceToken() throws Exception
    {
        String sequenceToken = "9999";
        List<LogMessage> messages = Arrays.asList(new LogMessage(0, "doesn't matter"));

        try
        {
            facade.sendMessages(sequenceToken, messages);
            fail("should have thrown");
        }
        catch (CloudWatchFacadeException ex)
        {
            assertException(ex, "sendMessages", "invalid sequence token: 9999", ReasonCode.INVALID_SEQUENCE_TOKEN, null);
        }

        assertEquals("calls to putLogEvents",                   1,                          mock.putLogEventsInvocationCount);
        assertEquals("group name passed to putLogEvents",       config.getLogGroupName(),   mock.putLogEventsGroupName);
        assertEquals("stream name passed to putLogEvents",      config.getLogStreamName(),  mock.putLogEventsStreamName);
        assertEquals("number of events passed to putLogEvents", 1,                          mock.putLogEventsEvents.size());
    }


    @Test
    public void testSendMessagesMissingLogGroup() throws Exception
    {
        mock = new MockCloudWatchClient(Collections.emptyList(), Collections.emptyList());

        String sequenceToken = mock.getCurrentSequenceToken();
        List<LogMessage> messages = Arrays.asList(new LogMessage(0, "doesn't matter"));

        try
        {
            facade.sendMessages(sequenceToken, messages);
            fail("should have thrown");
        }
        catch (CloudWatchFacadeException ex)
        {
            assertException(ex, "sendMessages", "missing log group", ReasonCode.MISSING_LOG_GROUP, null);
        }

        assertEquals("calls to putLogEvents",                   1,                          mock.putLogEventsInvocationCount);
        assertEquals("group name passed to putLogEvents",       config.getLogGroupName(),   mock.putLogEventsGroupName);
        assertEquals("stream name passed to putLogEvents",      config.getLogStreamName(),  mock.putLogEventsStreamName);
        assertEquals("number of events passed to putLogEvents", 1,                          mock.putLogEventsEvents.size());
    }


    @Test
    public void testSendMessagesDataAlreadyAccepted() throws Exception
    {
        mock = new MockCloudWatchClient(TEST_LOG_GROUPS, TEST_LOG_STREAMS)
        {
            @Override
            protected PutLogEventsResult putLogEvents(PutLogEventsRequest request)
            {
                throw new DataAlreadyAcceptedException("message irrelevant");
            }
        };

        String sequenceToken = mock.getCurrentSequenceToken();
        List<LogMessage> messages = Arrays.asList(new LogMessage(0, "doesn't matter"));

        try
        {
            facade.sendMessages(sequenceToken, messages);
            fail("should have thrown");
        }
        catch (CloudWatchFacadeException ex)
        {
            assertException(ex, "sendMessages", "already processed", ReasonCode.ALREADY_PROCESSED, null);
        }

        assertEquals("calls to putLogEvents",                   1,                          mock.putLogEventsInvocationCount);
        assertEquals("group name passed to putLogEvents",       config.getLogGroupName(),   mock.putLogEventsGroupName);
        assertEquals("stream name passed to putLogEvents",      config.getLogStreamName(),  mock.putLogEventsStreamName);
        assertEquals("number of events passed to putLogEvents", 1,                          mock.putLogEventsEvents.size());
    }


    @Test
    public void testSendMessagesUnexpectedException() throws Exception
    {
        RuntimeException cause = new RuntimeException("message irrelevant");
        mock = new MockCloudWatchClient(TEST_LOG_GROUPS, TEST_LOG_STREAMS)
        {
            @Override
            protected PutLogEventsResult putLogEvents(PutLogEventsRequest request)
            {
                throw cause;
            }
        };

        String sequenceToken = mock.getCurrentSequenceToken();
        List<LogMessage> messages = Arrays.asList(new LogMessage(0, "doesn't matter"));

        try
        {
            facade.sendMessages(sequenceToken, messages);
            fail("should have thrown");
        }
        catch (CloudWatchFacadeException ex)
        {
            assertException(ex, "sendMessages", "unexpected exception", ReasonCode.UNEXPECTED_EXCEPTION, cause);
        }

        assertEquals("calls to putLogEvents",                   1,                          mock.putLogEventsInvocationCount);
        assertEquals("group name passed to putLogEvents",       config.getLogGroupName(),   mock.putLogEventsGroupName);
        assertEquals("stream name passed to putLogEvents",      config.getLogStreamName(),  mock.putLogEventsStreamName);
        assertEquals("number of events passed to putLogEvents", 1,                          mock.putLogEventsEvents.size());
    }


    @Test
    public void testShutdown() throws Exception
    {
        facade.shutdown();

        assertEquals("calls to shutdown",                       1,                          mock.shutdownInvocationCount);
    }
}
