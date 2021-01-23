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

package com.kdgregory.logging.aws.facade.v2;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.test.StringAsserts;
import static net.sf.kdgcommons.test.StringAsserts.*;

import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.*;

import com.kdgregory.logging.aws.facade.KinesisFacade;
import com.kdgregory.logging.aws.facade.KinesisFacadeException;
import com.kdgregory.logging.aws.facade.KinesisFacadeException.ReasonCode;
import com.kdgregory.logging.aws.facade.v2.KinesisFacadeImpl;
import com.kdgregory.logging.aws.kinesis.KinesisConstants.StreamStatus;
import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;
import com.kdgregory.logging.aws.testhelpers.KinesisClientMock;

import com.kdgregory.logging.common.LogMessage;


public class TestKinesisFacadeImpl
{
    private final static String DEFAULT_STREAM_NAME = "biff";
    private final static String DEFAULT_PARTITION_KEY = "fixed";

    // need to explicitly configure for each test
    KinesisWriterConfig config = new KinesisWriterConfig();

    // each test will also create its own mock
    private KinesisClientMock mock;

    // lazily instantiated, just like the real thing; both config and mock can be changed before first call
    private KinesisFacade facade = new KinesisFacadeImpl(config)
    {
        private KinesisClient client;

        @Override
        protected KinesisClient client()
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
            KinesisFacadeException ex,
            String expectedFunctionName, String expectedContainedMessage,
            ReasonCode expectedReason, boolean expectedRetryable, Throwable expectedCause)
    {
        assertEquals("exception reason",  expectedReason, ex.getReason());

        assertRegex("exception message (was: " + ex.getMessage() + ")",
                    expectedFunctionName + ".*" + config.getStreamName() + ".*"
                                         + expectedContainedMessage,
                    ex.getMessage());

        assertEquals("retryable", expectedRetryable, ex.isRetryable());

        if (expectedCause != null)
        {
            assertSame("exception contains cause", expectedCause, ex.getCause());
        }
    }


    /**
     *  Verifies that a record passed to the SDK contains the expected content.
     */
    private void assertPutRecordsRequestEntry(
            String message, String expectedPartitionKey, String expectedMessage,
            PutRecordsRequestEntry entry)
    {
        StringAsserts.assertRegex(message + " partition key",
                     expectedPartitionKey,
                     entry.partitionKey());

        assertEquals(message + " content",
                     expectedMessage,
                     entry.data().asUtf8String());
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testRetrieveStatusHappyPath() throws Exception
    {
        mock = new KinesisClientMock(DEFAULT_STREAM_NAME);
        config.setStreamName(DEFAULT_STREAM_NAME);

        assertEquals("retrieved status", StreamStatus.ACTIVE, facade.retrieveStreamStatus());

        // a standard set of assertions to verify the APIs that we've called

        assertEquals("describeStream invocation count",             1,      mock.describeStreamSummaryInvocationCount);
        assertEquals("createStream invocation count",               0,      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords invocation count",                 0,      mock.putRecordsInvocationCount);
        assertEquals("shutdown invocation count",                   0,      mock.closeInvocationCount);
    }


    @Test
    public void testRetrieveStatusNoStream() throws Exception
    {
        mock = new KinesisClientMock();
        config.setStreamName(DEFAULT_STREAM_NAME);

        assertEquals("retrieved status", StreamStatus.DOES_NOT_EXIST, facade.retrieveStreamStatus());

        assertEquals("describeStream invocation count",             1,      mock.describeStreamSummaryInvocationCount);
        assertEquals("createStream invocation count",               0,      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords invocation count",                 0,      mock.putRecordsInvocationCount);
        assertEquals("shutdown invocation count",                   0,      mock.closeInvocationCount);
    }


    @Test
    public void testRetrieveStatusThrottling() throws Exception
    {
        mock = new KinesisClientMock()
        {
            @Override
            protected DescribeStreamSummaryResponse describeStreamSummary(DescribeStreamSummaryRequest request)
            {
                throw LimitExceededException.builder().message("message irrelevant").build();
            }
        };
        config.setStreamName(DEFAULT_STREAM_NAME);

        assertEquals("retrieved status",                            null,   facade.retrieveStreamStatus());

        assertEquals("describeStream invocation count",             1,      mock.describeStreamSummaryInvocationCount);
        assertEquals("createStream invocation count",               0,      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords invocation count",                 0,      mock.putRecordsInvocationCount);
        assertEquals("shutdown invocation count",                   0,      mock.closeInvocationCount);
    }


    @Test
    public void testRetrieveStatusUnexpectedException() throws Exception
    {
        final RuntimeException cause = new RuntimeException("test");
        mock = new KinesisClientMock()
        {
            @Override
            protected DescribeStreamSummaryResponse describeStreamSummary(DescribeStreamSummaryRequest request)
            {
                throw cause;
            }
        };
        config.setStreamName(DEFAULT_STREAM_NAME);

        try
        {
            facade.retrieveStreamStatus();
            fail("should have thrown");
        }
        catch (KinesisFacadeException ex)
        {
            assertException(ex, "retrieveStreamStatus", "unexpected exception: test", ReasonCode.UNEXPECTED_EXCEPTION, false, cause);
        }

        assertEquals("describeStream invocation count",             1,      mock.describeStreamSummaryInvocationCount);
        assertEquals("createStream invocation count",               0,      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords invocation count",                 0,      mock.putRecordsInvocationCount);
        assertEquals("shutdown invocation count",                   0,      mock.closeInvocationCount);
    }


    @Test
    public void tesCreateHappyPath() throws Exception
    {
        mock = new KinesisClientMock();
        config.setStreamName(DEFAULT_STREAM_NAME).setShardCount(3);

        facade.createStream();

        assertEquals("stream name passed to function",              DEFAULT_STREAM_NAME,    mock.createStreamStreamName);
        assertEquals("shard count passed to function",              3,                      mock.createStreamShardCount.intValue());

        assertEquals("describeStream invocation count",             0,      mock.describeStreamSummaryInvocationCount);
        assertEquals("createStream invocation count",               1,      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords invocation count",                 0,      mock.putRecordsInvocationCount);
        assertEquals("shutdown invocation count",                   0,      mock.closeInvocationCount);
    }


    @Test
    public void tesCreateStreamAlreadyExists() throws Exception
    {
        mock = new KinesisClientMock()
        {
            @Override
            protected CreateStreamResponse createStream(CreateStreamRequest request)
            {
                throw ResourceInUseException.builder().message("message irrelevant").build();
            }
        };
        config.setStreamName(DEFAULT_STREAM_NAME).setShardCount(3);

        // no exception == success
        facade.createStream();

        assertEquals("describeStream invocation count",             0,      mock.describeStreamSummaryInvocationCount);
        assertEquals("createStream invocation count",               1,      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords invocation count",                 0,      mock.putRecordsInvocationCount);
        assertEquals("shutdown invocation count",                   0,      mock.closeInvocationCount);
    }


    @Test
    public void tesCreateThrottled() throws Exception
    {
        mock = new KinesisClientMock()
        {
            @Override
            protected CreateStreamResponse createStream(CreateStreamRequest request)
            {
                // this exception indicates both throttling and exceeding some quota
                throw LimitExceededException.builder().message("message irrelevant").build();
            }
        };
        config.setStreamName(DEFAULT_STREAM_NAME).setShardCount(3);

        try
        {
            facade.createStream();
            fail("should have thrown");
        }
        catch (KinesisFacadeException ex)
        {
            assertException(ex, "createStream", "limit exceeded", ReasonCode.LIMIT_EXCEEDED, true, null);
        }

        assertEquals("describeStream invocation count",             0,      mock.describeStreamSummaryInvocationCount);
        assertEquals("createStream invocation count",               1,      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords invocation count",                 0,      mock.putRecordsInvocationCount);
        assertEquals("shutdown invocation count",                   0,      mock.closeInvocationCount);
    }


    @Test
    public void tesCreateUnexpectedException() throws Exception
    {
        final RuntimeException cause = new RuntimeException("test");
        mock = new KinesisClientMock()
        {

            @Override
            protected CreateStreamResponse createStream(CreateStreamRequest request)
            {
                throw cause;
            }
        };
        config.setStreamName(DEFAULT_STREAM_NAME).setShardCount(3);

        try
        {
            facade.createStream();
            fail("should have thrown");
        }
        catch (KinesisFacadeException ex)
        {
            assertException(ex, "createStream", "unexpected exception: test", ReasonCode.UNEXPECTED_EXCEPTION, false, cause);
        }

        assertEquals("describeStream invocation count",             0,      mock.describeStreamSummaryInvocationCount);
        assertEquals("createStream invocation count",               1,      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords invocation count",                 0,      mock.putRecordsInvocationCount);
        assertEquals("shutdown invocation count",                   0,      mock.closeInvocationCount);
    }


    @Test
    public void tesSetRetentionPeriodHappyPath() throws Exception
    {
        mock = new KinesisClientMock(DEFAULT_STREAM_NAME);
        config.setStreamName(DEFAULT_STREAM_NAME).setRetentionPeriod(48);

        facade.setRetentionPeriod();

        assertEquals("stream name passed to function",              DEFAULT_STREAM_NAME,    mock.increaseRetentionPeriodStreamName);
        assertEquals("retention period passed to function",         48,                     mock.increaseRetentionPeriodHours.intValue());

        assertEquals("describeStream invocation count",             0,      mock.describeStreamSummaryInvocationCount);
        assertEquals("createStream invocation count",               0,      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    1,      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords invocation count",                 0,      mock.putRecordsInvocationCount);
        assertEquals("shutdown invocation count",                   0,      mock.closeInvocationCount);
    }


    @Test
    public void tesSetRetentionPeriodNoConfiguration() throws Exception
    {
        mock = new KinesisClientMock(DEFAULT_STREAM_NAME);
        config.setStreamName(DEFAULT_STREAM_NAME);

        facade.setRetentionPeriod();

        assertEquals("describeStream invocation count",             0,      mock.describeStreamSummaryInvocationCount);
        assertEquals("createStream invocation count",               0,      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords invocation count",                 0,      mock.putRecordsInvocationCount);
        assertEquals("shutdown invocation count",                   0,      mock.closeInvocationCount);
    }


    @Test
    public void testSetRetentionPeriodStreamNotReady() throws Exception
    {
        mock = new KinesisClientMock(DEFAULT_STREAM_NAME)
        {
            @Override
            protected IncreaseStreamRetentionPeriodResponse increaseStreamRetentionPeriod(
                IncreaseStreamRetentionPeriodRequest request)
            {
                throw ResourceInUseException.builder().message("message irrelevant").build();
            }
        };
        config.setStreamName(DEFAULT_STREAM_NAME).setRetentionPeriod(48);

        try
        {
            facade.setRetentionPeriod();
            fail("should have thrown");
        }
        catch (KinesisFacadeException ex)
        {
            assertException(ex, "setRetentionPeriod", "stream not active", ReasonCode.INVALID_STATE, true, null);
        }

        assertEquals("describeStream invocation count",             0,      mock.describeStreamSummaryInvocationCount);
        assertEquals("createStream invocation count",               0,      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    1,      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords invocation count",                 0,      mock.putRecordsInvocationCount);
        assertEquals("shutdown invocation count",                   0,      mock.closeInvocationCount);
    }


    @Test
    public void testSetRetentionPeriodUnexpectedException() throws Exception
    {
        final RuntimeException cause = new RuntimeException("test");
        mock = new KinesisClientMock(DEFAULT_STREAM_NAME)
        {
            @Override
            protected IncreaseStreamRetentionPeriodResponse increaseStreamRetentionPeriod(
                IncreaseStreamRetentionPeriodRequest request)
            {
                throw cause;
            }
        };
        config.setStreamName(DEFAULT_STREAM_NAME).setRetentionPeriod(48);

        try
        {
            facade.setRetentionPeriod();
            fail("should have thrown");
        }
        catch (KinesisFacadeException ex)
        {
            assertException(ex, "setRetentionPeriod", "unexpected exception: test", ReasonCode.UNEXPECTED_EXCEPTION, false, cause);
        }

        assertEquals("describeStream invocation count",             0,      mock.describeStreamSummaryInvocationCount);
        assertEquals("createStream invocation count",               0,      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    1,      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords invocation count",                 0,      mock.putRecordsInvocationCount);
        assertEquals("shutdown invocation count",                   0,      mock.closeInvocationCount);
    }


    @Test
    public void testPutRecordsHappyPath() throws Exception
    {
        final String message1 = "message 1";
        final String message2 = "message \u0392";   // verifies UTF-8 translation

        mock = new KinesisClientMock(DEFAULT_STREAM_NAME);
        config.setStreamName(DEFAULT_STREAM_NAME).setPartitionKey(DEFAULT_PARTITION_KEY);

        long now = System.currentTimeMillis();
        List<LogMessage> batch = Arrays.asList(
                                    new LogMessage(now,     message1),
                                    new LogMessage(now + 1, message2));

        List<LogMessage> remaining = facade.putRecords(batch);

        assertEquals("passed stream name to client",                DEFAULT_STREAM_NAME,    mock.putRecordsStreamName);
        assertEquals("number of records passed to client",          batch.size(),           mock.putRecordsSourceRecords.size());

        assertPutRecordsRequestEntry("first record",    DEFAULT_PARTITION_KEY, message1,    mock.putRecordsSourceRecords.get(0));
        assertPutRecordsRequestEntry("second record",   DEFAULT_PARTITION_KEY, message2,    mock.putRecordsSourceRecords.get(1));

        assertEquals("number of rejected records",                  0,                      remaining.size());

        assertEquals("describeStream invocation count",             0,                      mock.describeStreamSummaryInvocationCount);
        assertEquals("createStream invocation count",               0,                      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,                      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords invocation count",                 1,                      mock.putRecordsInvocationCount);
        assertEquals("shutdown invocation count",                   0,                      mock.closeInvocationCount);
    }


    @Test
    public void testPutRecordsPartialFailure() throws Exception
    {
        mock = new KinesisClientMock(DEFAULT_STREAM_NAME)
        {
            @Override
            protected PutRecordsResultEntry processRequestEntry(int index, PutRecordsRequestEntry entry)
            {
                if (index % 2 == 0)
                    return PutRecordsResultEntry.builder().errorCode("ProvisionedThroughputExceededException").build();
                else
                    return super.processRequestEntry(index, entry);
            }
        };
        config.setStreamName(DEFAULT_STREAM_NAME).setPartitionKey(DEFAULT_PARTITION_KEY);

        long now = System.currentTimeMillis();
        List<LogMessage> batch = Arrays.asList(
                                    new LogMessage(now,     "message 1"),
                                    new LogMessage(now + 1, "message 2"),
                                    new LogMessage(now + 2, "message 3"),
                                    new LogMessage(now + 3, "message 4"));

        List<LogMessage> remaining = facade.putRecords(batch);

        assertPutRecordsRequestEntry("put - first record",          DEFAULT_PARTITION_KEY, "message 1",     mock.putRecordsSourceRecords.get(0));
        assertPutRecordsRequestEntry("put - second record",         DEFAULT_PARTITION_KEY, "message 2",     mock.putRecordsSourceRecords.get(1));
        assertPutRecordsRequestEntry("put - third record",          DEFAULT_PARTITION_KEY, "message 3",     mock.putRecordsSourceRecords.get(2));
        assertPutRecordsRequestEntry("put - fourth record",         DEFAULT_PARTITION_KEY, "message 4",     mock.putRecordsSourceRecords.get(3));

        assertEquals("number of rejected records",                  2,                      remaining.size());

        assertEquals("remaining - first record",                    "message 1",            remaining.get(0).getMessage());
        assertEquals("remaining - second record",                   "message 3",            remaining.get(1).getMessage());

        assertEquals("describeStream invocation count",             0,                      mock.describeStreamSummaryInvocationCount);
        assertEquals("createStream invocation count",               0,                      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,                      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords invocation count",                 1,                      mock.putRecordsInvocationCount);
        assertEquals("shutdown invocation count",                   0,                      mock.closeInvocationCount);
    }


    @Test
    public void testPutRecordsRandomPartitionKeys() throws Exception
    {
        mock = new KinesisClientMock(DEFAULT_STREAM_NAME);
        config.setStreamName(DEFAULT_STREAM_NAME).setPartitionKey("");

        long now = System.currentTimeMillis();
        List<LogMessage> batch = Arrays.asList(
                                    new LogMessage(now,     "message 1"),
                                    new LogMessage(now + 1, "message 2"));

        List<LogMessage> remaining = facade.putRecords(batch);

        assertEquals("passed stream name to client",                DEFAULT_STREAM_NAME,    mock.putRecordsStreamName);
        assertEquals("number of records passed to client",          batch.size(),           mock.putRecordsSourceRecords.size());

        assertPutRecordsRequestEntry("first record",                "\\d{6}", "message 1",  mock.putRecordsSourceRecords.get(0));
        assertPutRecordsRequestEntry("second record",               "\\d{6}", "message 2",  mock.putRecordsSourceRecords.get(1));

        assertEquals("number of rejected records",                  0,                      remaining.size());

        String p1 = mock.putRecordsSourceRecords.get(0).partitionKey();
        String p2 = mock.putRecordsSourceRecords.get(1).partitionKey();

        // as implemented, this is unlikely to fail
        assertTrue("partition keys are different", ! p1.equals(p2));

        assertEquals("describeStream invocation count",             0,                      mock.describeStreamSummaryInvocationCount);
        assertEquals("createStream invocation count",               0,                      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,                      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords invocation count",                 1,                      mock.putRecordsInvocationCount);
        assertEquals("shutdown invocation count",                   0,                      mock.closeInvocationCount);
    }


    @Test
    public void testPutRecordsThrottling() throws Exception
    {
        mock = new KinesisClientMock(DEFAULT_STREAM_NAME)
        {
            @Override
            protected PutRecordsResponse putRecords(PutRecordsRequest request)
            {
                throw ProvisionedThroughputExceededException.builder().message("message irrelevant").build();
            }
        };
        config.setStreamName(DEFAULT_STREAM_NAME).setPartitionKey(DEFAULT_PARTITION_KEY);

        long now = System.currentTimeMillis();
        List<LogMessage> batch = Arrays.asList(
                                    new LogMessage(now,     "message 1"),
                                    new LogMessage(now + 1, "message 2"));

        try
        {
            facade.putRecords(batch);
            fail("should have thrown");
        }
        catch (KinesisFacadeException ex)
        {
            assertException(ex, "putRecords", "throttled", ReasonCode.THROTTLING, true, null);
        }

        assertEquals("describeStream invocation count",             0,                      mock.describeStreamSummaryInvocationCount);
        assertEquals("createStream invocation count",               0,                      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,                      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords invocation count",                 1,                      mock.putRecordsInvocationCount);
        assertEquals("shutdown invocation count",                   0,                      mock.closeInvocationCount);
    }


    @Test
    public void testPutRecordsUnexpectedException() throws Exception
    {
        final RuntimeException cause = new RuntimeException("test");
        mock = new KinesisClientMock(DEFAULT_STREAM_NAME)
        {
            @Override
            protected PutRecordsResponse putRecords(PutRecordsRequest request)
            {
                throw cause;
            }
        };
        config.setStreamName(DEFAULT_STREAM_NAME).setPartitionKey(DEFAULT_PARTITION_KEY);

        long now = System.currentTimeMillis();
        List<LogMessage> batch = Arrays.asList(
                                    new LogMessage(now,     "message 1"),
                                    new LogMessage(now + 1, "message 2"));

        try
        {
            facade.putRecords(batch);
            fail("should have thrown");
        }
        catch (KinesisFacadeException ex)
        {
            assertException(ex, "putRecords", "unexpected exception: test", ReasonCode.UNEXPECTED_EXCEPTION, false, cause);
        }

        assertEquals("describeStream invocation count",             0,                      mock.describeStreamSummaryInvocationCount);
        assertEquals("createStream invocation count",               0,                      mock.createStreamInvocationCount);
        assertEquals("increaseRetentionPeriod invocation count",    0,                      mock.increaseRetentionPeriodInvocationCount);
        assertEquals("putRecords invocation count",                 1,                      mock.putRecordsInvocationCount);
        assertEquals("shutdown invocation count",                   0,                      mock.closeInvocationCount);
    }


    @Test
    public void testShutdown() throws Exception
    {
         mock = new KinesisClientMock();

        facade.shutdown();

        assertEquals("calls to close",                              1,                          mock.closeInvocationCount);
    }
}
