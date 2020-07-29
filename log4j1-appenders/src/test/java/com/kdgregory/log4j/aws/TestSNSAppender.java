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

package com.kdgregory.log4j.aws;

import static net.sf.kdgcommons.test.StringAsserts.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.helpers.LogLog;

import com.kdgregory.log4j.testhelpers.sns.TestableSNSAppender;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.testhelpers.sns.MockSNSWriter;


/**
 *  These tests exercise appender logic specific to SNSAppender, using a mock log-writer.
 */
public class TestSNSAppender
extends AbstractUnitTest<TestableSNSAppender>
{
    public TestSNSAppender()
    {
        super("TestSNSAppender/", "test");
    }


    @Before
    public void setUp()
    {
        LogManager.resetConfiguration();
        LogLog.setQuietMode(true);
    }


    @After
    public void tearDown()
    {
        LogLog.setQuietMode(false);
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testConfigurationByName() throws Exception
    {
        // note: this also tests non-default configuration
        initialize("testConfigurationByName");

        assertEquals("topicName",               "example",                      appender.getTopicName());
        assertEquals("topicArn",                null,                           appender.getTopicArn());

        assertEquals("subject",                 "This is a test",               appender.getSubject());
        assertTrue("autoCreate",                                                appender.getAutoCreate());
        assertEquals("batch delay",             1L,                             appender.getBatchDelay());
        assertFalse("truncate oversize messages",                               appender.getTruncateOversizeMessages());
        assertEquals("discard threshold",       123,                            appender.getDiscardThreshold());
        assertEquals("discard action",          "newest",                       appender.getDiscardAction());
        assertFalse("synchronous mode",                                         appender.getSynchronous());
        assertFalse("use shutdown hook",                                        appender.getUseShutdownHook());
        assertEquals("assumed role",            "AssumableRole",                appender.getAssumedRole());
        assertEquals("client factory",          "com.example.Foo.bar",          appender.getClientFactory());
        assertEquals("client region",           "us-west-1",                    appender.getClientRegion());
        assertEquals("client endpoint",         "sns.us-west-2.amazonaws.com",  appender.getClientEndpoint());
    }


    @Test
    public void testConfigurationByArn() throws Exception
    {
        // note: this also tests default configuration
        initialize("testConfigurationByArn");

        assertEquals("topicName",               null,                           appender.getTopicName());
        assertEquals("topicArn",                "arn-example",                  appender.getTopicArn());

        assertEquals("subject",                 null,                           appender.getSubject());
        assertFalse("autoCreate",                                               appender.getAutoCreate());
        assertTrue("truncate oversize messages",                                appender.getTruncateOversizeMessages());
        assertEquals("batch delay",             1L,                             appender.getBatchDelay());
        assertEquals("discard threshold",       1000,                           appender.getDiscardThreshold());
        assertEquals("discard action",          "oldest",                       appender.getDiscardAction());
        assertFalse("synchronous mode",                                         appender.getSynchronous());
        assertTrue("use shutdown hook",                                         appender.getUseShutdownHook());
        assertEquals("assumed role",            null,                           appender.getAssumedRole());
        assertEquals("client factory",          null,                           appender.getClientFactory());
        assertEquals("client region",           null,                           appender.getClientRegion());
        assertEquals("client endpoint",         null,                           appender.getClientEndpoint());
    }


    @Test
    public void testSynchronousConfiguration() throws Exception
    {
        initialize("testSynchronousConfiguration");

        // all we care about is the interaction between synchronous and batchDelay

        assertTrue("synchronous mode",                                      appender.getSynchronous());
        assertEquals("batch delay",         0L,                             appender.getBatchDelay());
    }


    @Test
    public void testWriterInitialization() throws Exception
    {
        // property has to be set before initialization
        System.setProperty("TestSNSAppender.testWriterInitialization", "example");

        initialize("testWriterInitialization");

        assertEquals("configured topicName",            "name-{date}",                                          appender.getTopicName());
        assertEquals("configured topicArn",             "arn-{date}",                                           appender.getTopicArn());
        assertEquals("configured subject",              "{sysprop:TestSNSAppender.testWriterInitialization}",   appender.getSubject());

        logger.debug("this triggers writer creation");

        MockSNSWriter writer = appender.getMockWriter();

        assertRegex("writer topicName",                 "name-20\\d{6}",                    writer.config.topicName);
        assertRegex("writer topicArn",                  "arn-20\\d{6}",                     writer.config.topicArn);
        assertEquals("writer subject",                  "example",                          writer.config.subject);
        assertTrue("writer autoCreate",                                                     writer.config.autoCreate);
        assertEquals("writer batch delay",              1L,                                 writer.config.batchDelay);
        assertEquals("writer discard threshold",        123,                                writer.config.discardThreshold);
        assertEquals("writer discard action",           DiscardAction.newest,               writer.config.discardAction);
        assertEquals("writer client factory method",    "com.example.Foo.bar",              writer.config.clientFactoryMethod);
        assertEquals("writer client endpoint",          "sns.us-east-2.amazonaws.com",      writer.config.clientEndpoint);
    }


    @Test
    public void testChangeSubject() throws Exception
    {
        initialize("testChangeSubject");

        logger.debug("this triggers writer creation");

        MockSNSWriter writer = appender.getMockWriter();

        assertEquals("initial subject",                 "First Subject",                    writer.config.subject);

        appender.setSubject("Second Subject");

        assertEquals("updated subject",                 "Second Subject",                   writer.config.subject);
    }
}
