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

import static net.sf.kdgcommons.test.StringAsserts.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.*;

import com.kdgregory.logging.aws.facade.v1.SNSFacadeImpl;
import com.kdgregory.logging.aws.internal.facade.SNSFacade;
import com.kdgregory.logging.aws.internal.facade.SNSFacadeException;
import com.kdgregory.logging.aws.internal.facade.SNSFacadeException.ReasonCode;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;
import com.kdgregory.logging.aws.testhelpers.SNSClientMock;
import com.kdgregory.logging.common.LogMessage;


public class TestSNSFacadeImpl
{
    private final static String DEFAULT_TOPIC_NAME = "bargle";
    private final static String DEFAULT_TOPIC_ARN = SNSClientMock.ARN_PREFIX + DEFAULT_TOPIC_NAME;
    private final static String DEFAULT_SUBJECT = "argle";

    // note that the default name is at the end, to test pagination
    private final static List<String> TEST_TOPICS = Arrays.asList("foo", "bar", "baz", "argle", "bargle");

    // need to explicitly configure for each test
    private SNSWriterConfig config = new SNSWriterConfig();

    // each test will also create its own mock
    private SNSClientMock mock;

    // lazily instantiated, just like the real thing; both config and mock can be changed before first call
    private SNSFacade facade = new SNSFacadeImpl(config)
    {
        private AmazonSNS client;

        @Override
        protected AmazonSNS client()
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
            SNSFacadeException ex,
            String expectedFunctionName, String expectedContainedMessage,
            ReasonCode expectedReason, boolean expectedRetryable, Throwable expectedCause)
    {
        String nameOrArn = (config.getTopicArn() != null) ? config.getTopicArn()
                         : (config.getTopicName() != null) ? config.getTopicName()
                         : "";  // this case is only for invalid configuration

        assertEquals("exception reason",  expectedReason, ex.getReason());

        assertRegex("exception message (was: " + ex.getMessage() + ")",
                    expectedFunctionName + ".*" + nameOrArn+ ".*"
                                         + expectedContainedMessage,
                    ex.getMessage());

        assertEquals("retryable", expectedRetryable, ex.isRetryable());

        if (expectedCause != null)
        {
            assertSame("exception contains cause", expectedCause, ex.getCause());
        }
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testLookupByNameHappyPath() throws Exception
    {
        mock = new SNSClientMock(TEST_TOPICS);
        config.setTopicName(DEFAULT_TOPIC_NAME);

        assertEquals("found topic", DEFAULT_TOPIC_ARN, facade.lookupTopic());

        // boilerplate asserts to see what client methods we called

        assertEquals("listTopics() invocation count",   1,      mock.listTopicsInvocationCount);
        assertEquals("createTopic() invocation count",  0,      mock.createTopicInvocationCount);
        assertEquals("publish() invocation count",      0,      mock.publishInvocationCount);
        assertEquals("shutdown() invocation count",     0,      mock.shutdownInvocationCount);
    }


    @Test
    public void testLookupByArnHappyPath() throws Exception
    {
        mock = new SNSClientMock(TEST_TOPICS);
        config.setTopicArn(DEFAULT_TOPIC_ARN);

        assertEquals("found topic", DEFAULT_TOPIC_ARN, facade.lookupTopic());

        // boilerplate asserts to see what client methods we called

        assertEquals("listTopics() invocation count",   1,      mock.listTopicsInvocationCount);
        assertEquals("createTopic() invocation count",  0,      mock.createTopicInvocationCount);
        assertEquals("publish() invocation count",      0,      mock.publishInvocationCount);
        assertEquals("shutdown() invocation count",     0,      mock.shutdownInvocationCount);
    }


    @Test
    public void testLookupPaginated() throws Exception
    {
        mock = new SNSClientMock(TEST_TOPICS, 3);
        config.setTopicName(DEFAULT_TOPIC_NAME);

        assertEquals("found topic", DEFAULT_TOPIC_ARN, facade.lookupTopic());

        assertEquals("listTopics() invocation count",   2,      mock.listTopicsInvocationCount);
        assertEquals("createTopic() invocation count",  0,      mock.createTopicInvocationCount);
        assertEquals("publish() invocation count",      0,      mock.publishInvocationCount);
        assertEquals("shutdown() invocation count",     0,      mock.shutdownInvocationCount);
    }


    @Test
    public void testLookupNoSuchTopic() throws Exception
    {
        mock = new SNSClientMock(Collections.emptyList());
        config.setTopicName(DEFAULT_TOPIC_NAME);

        assertNull("did not find topic", facade.lookupTopic());

        assertEquals("listTopics() invocation count",   1,      mock.listTopicsInvocationCount);
        assertEquals("createTopic() invocation count",  0,      mock.createTopicInvocationCount);
        assertEquals("publish() invocation count",      0,      mock.publishInvocationCount);
        assertEquals("shutdown() invocation count",     0,      mock.shutdownInvocationCount);
    }


    @Test
    public void testLookupThrottling() throws Exception
    {
        mock = new SNSClientMock(TEST_TOPICS)
        {
            @Override
            protected ListTopicsResult listTopics(ListTopicsRequest request)
            {
                // exception determined experimentally
                AmazonSNSException ex = new AmazonSNSException("Rate exceeded");
                ex.setErrorCode("Throttling");
                throw ex;
            }
        };
        config.setTopicName(DEFAULT_TOPIC_NAME);

        try
        {
            facade.lookupTopic();
            fail("should have thrown");
        }
        catch (SNSFacadeException ex)
        {
            assertException(ex, "lookupTopic", "request throttled", ReasonCode.THROTTLING, true, null);
        }

        assertEquals("listTopics() invocation count",   1,      mock.listTopicsInvocationCount);
        assertEquals("createTopic() invocation count",  0,      mock.createTopicInvocationCount);
        assertEquals("publish() invocation count",      0,      mock.publishInvocationCount);
        assertEquals("shutdown() invocation count",     0,      mock.shutdownInvocationCount);
    }


    @Test
    public void testLookupException() throws Exception
    {
        final RuntimeException cause = new RuntimeException("test");
        mock = new SNSClientMock(TEST_TOPICS)
        {
            @Override
            protected ListTopicsResult listTopics(ListTopicsRequest request)
            {
                throw cause;
            }
        };
        config.setTopicName(DEFAULT_TOPIC_NAME);

        try
        {
            facade.lookupTopic();
            fail("should have thrown");
        }
        catch (SNSFacadeException ex)
        {
            assertException(ex, "lookupTopic", "unexpected exception: test", ReasonCode.UNEXPECTED_EXCEPTION, false, cause);
        }

        assertEquals("listTopics() invocation count",   1,      mock.listTopicsInvocationCount);
        assertEquals("createTopic() invocation count",  0,      mock.createTopicInvocationCount);
        assertEquals("publish() invocation count",      0,      mock.publishInvocationCount);
        assertEquals("shutdown() invocation count",     0,      mock.shutdownInvocationCount);
    }


    @Test
    public void testCreateHappyPath() throws Exception
    {
        mock = new SNSClientMock(Collections.emptyList());
        config.setTopicName(DEFAULT_TOPIC_NAME);

        assertEquals("returned ARN", DEFAULT_TOPIC_ARN, facade.createTopic());

        assertEquals("listTopics() invocation count",   0,      mock.listTopicsInvocationCount);
        assertEquals("createTopic() invocation count",  1,      mock.createTopicInvocationCount);
        assertEquals("publish() invocation count",      0,      mock.publishInvocationCount);
        assertEquals("shutdown() invocation count",     0,      mock.shutdownInvocationCount);
    }


    @Test
    public void testCreateException() throws Exception
    {
        final RuntimeException cause = new RuntimeException("test");
        mock = new SNSClientMock(Collections.emptyList())
        {
            @Override
            protected CreateTopicResult createTopic(String topicName)
            {
                throw cause;
            }
        };
        config.setTopicName(DEFAULT_TOPIC_NAME);

        try
        {
            facade.createTopic();
            fail("should have thrown");
        }
        catch (SNSFacadeException ex)
        {
            assertException(ex, "createTopic", "unexpected exception: test", ReasonCode.UNEXPECTED_EXCEPTION, false, cause);
        }

        assertEquals("listTopics() invocation count",   0,      mock.listTopicsInvocationCount);
        assertEquals("createTopic() invocation count",  1,      mock.createTopicInvocationCount);
        assertEquals("publish() invocation count",      0,      mock.publishInvocationCount);
        assertEquals("shutdown() invocation count",     0,      mock.shutdownInvocationCount);
    }


    @Test
    public void testPublishHappyPath() throws Exception
    {
        mock = new SNSClientMock(TEST_TOPICS);
        config.setTopicArn(DEFAULT_TOPIC_ARN).setSubject(DEFAULT_SUBJECT);

        facade.publish(new LogMessage(123456789L, "test message"));

        assertEquals("publish ARN",                     DEFAULT_TOPIC_ARN,      mock.publishArn);
        assertEquals("publish subject",                 DEFAULT_SUBJECT,        mock.publishSubject);
        assertEquals("publish message",                 "test message",         mock.publishMessage);

        assertEquals("listTopics() invocation count",   0,      mock.listTopicsInvocationCount);
        assertEquals("createTopic() invocation count",  0,      mock.createTopicInvocationCount);
        assertEquals("publish() invocation count",      1,      mock.publishInvocationCount);
        assertEquals("shutdown() invocation count",     0,      mock.shutdownInvocationCount);
    }


    @Test
    public void testPublishThrottling() throws Exception
    {
        mock = new SNSClientMock(TEST_TOPICS)
        {
            @Override
            protected PublishResult publish(PublishRequest request)
            {
                AmazonSNSException ex = new AmazonSNSException("Rate exceeded");
                ex.setErrorCode("Throttling");
                throw ex;
            }
        };
        config.setTopicArn(DEFAULT_TOPIC_ARN).setSubject(DEFAULT_SUBJECT);

        try
        {
            facade.publish(new LogMessage(123456789L, "test message"));
            fail("should have thrown");
        }
        catch (SNSFacadeException ex)
        {
            assertException(ex, "publish", "request throttled", ReasonCode.THROTTLING, true, null);
        }

        assertEquals("listTopics() invocation count",   0,      mock.listTopicsInvocationCount);
        assertEquals("createTopic() invocation count",  0,      mock.createTopicInvocationCount);
        assertEquals("publish() invocation count",      1,      mock.publishInvocationCount);
        assertEquals("shutdown() invocation count",     0,      mock.shutdownInvocationCount);
    }


    @Test
    public void testPublishException() throws Exception
    {
        final RuntimeException cause = new RuntimeException("test");
        mock = new SNSClientMock(TEST_TOPICS)
        {
            @Override
            protected PublishResult publish(PublishRequest request)
            {
                throw cause;
            }
        };
        config.setTopicArn(DEFAULT_TOPIC_ARN).setSubject(DEFAULT_SUBJECT);

        try
        {
            facade.publish(new LogMessage(123456789L, "test message"));
            fail("should have thrown");
        }
        catch (SNSFacadeException ex)
        {
            assertException(ex, "publish", "unexpected exception: test", ReasonCode.UNEXPECTED_EXCEPTION, false, cause);
        }

        assertEquals("listTopics() invocation count",   0,      mock.listTopicsInvocationCount);
        assertEquals("createTopic() invocation count",  0,      mock.createTopicInvocationCount);
        assertEquals("publish() invocation count",      1,      mock.publishInvocationCount);
        assertEquals("shutdown() invocation count",     0,      mock.shutdownInvocationCount);
    }


    @Test
    public void testPublishInvalidArn() throws Exception
    {
        mock = new SNSClientMock(TEST_TOPICS);
        // leave config empty

        try
        {
            facade.publish(new LogMessage(123456789L, "test message"));
            fail("should have thrown");
        }
        catch (SNSFacadeException ex)
        {
            assertException(ex, "publish", "ARN not configured", ReasonCode.INVALID_CONFIGURATION, false, null);
        }

        assertEquals("listTopics() invocation count",   0,      mock.listTopicsInvocationCount);
        assertEquals("createTopic() invocation count",  0,      mock.createTopicInvocationCount);
        assertEquals("publish() invocation count",      0,      mock.publishInvocationCount);
        assertEquals("shutdown() invocation count",     0,      mock.shutdownInvocationCount);
    }


    @Test
    public void testPublishNoSuchTopic() throws Exception
    {
        mock = new SNSClientMock(Collections.emptyList());
        config.setTopicArn(DEFAULT_TOPIC_ARN);

        try
        {
            facade.publish(new LogMessage(123456789L, "test message"));
            fail("should have thrown");
        }
        catch (SNSFacadeException ex)
        {
            assertException(ex, "publish", "topic does not exist", ReasonCode.MISSING_TOPIC, false, null);
        }

        assertEquals("listTopics() invocation count",   0,      mock.listTopicsInvocationCount);
        assertEquals("createTopic() invocation count",  0,      mock.createTopicInvocationCount);
        assertEquals("publish() invocation count",      1,      mock.publishInvocationCount);
        assertEquals("shutdown() invocation count",     0,      mock.shutdownInvocationCount);
    }
}
