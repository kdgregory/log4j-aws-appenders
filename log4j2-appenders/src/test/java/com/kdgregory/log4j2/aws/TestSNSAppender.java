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

package com.kdgregory.log4j2.aws;

import org.junit.Test;
import static org.junit.Assert.*;

import static net.sf.kdgcommons.test.StringAsserts.*;

import com.kdgregory.log4j2.aws.SNSAppender.SNSAppenderBuilder;
import com.kdgregory.log4j2.testhelpers.TestableSNSAppender;
import com.kdgregory.log4j2.testhelpers.TestableSNSAppender.TestableSNSAppenderBuilder;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.testhelpers.sns.MockSNSWriter;


public class TestSNSAppender
extends AbstractUnitTest<TestableSNSAppender>
{
    public TestSNSAppender()
    {
        super("TestSNSAppender/", "TEST");
    }


    @Test
    public void testConfigurationByName() throws Exception
    {
        // note: this also tests non-default configuration
        initialize("testConfigurationByName");

        assertEquals("topicName",               "example",                      appender.getConfig().getTopicName());
        assertEquals("topicArn",                null,                           appender.getConfig().getTopicArn());

        assertEquals("subject",                 "This is a test",               appender.getConfig().getSubject());
        assertTrue("autoCreate",                                                appender.getConfig().isAutoCreate());
        assertEquals("batch delay",             1L,                             appender.getConfig().getBatchDelay());
        assertTrue("truncate oversize messages",                                appender.getConfig().getTruncateOversizeMessages());
        assertEquals("discard threshold",       123,                            appender.getConfig().getDiscardThreshold());
        assertEquals("discard action",          "newest",                       appender.getConfig().getDiscardAction());
        assertFalse("use shutdown hook",                                        appender.getConfig().isUseShutdownHook());
        assertFalse("synchronous mode",                                         appender.getConfig().isSynchronous());
        assertEquals("assumed role",            "AssumableRole",                appender.getConfig().getAssumedRole());
        assertEquals("client factory",          "com.example.Foo.bar",          appender.getConfig().getClientFactory());
        assertEquals("client region",           "us-west-1",                    appender.getConfig().getClientRegion());
        assertEquals("client endpoint",         "sns.us-west-2.amazonaws.com",  appender.getConfig().getClientEndpoint());
    }


    @Test
    public void testConfigurationByArn() throws Exception
    {
        // note: this also tests default configuration
        initialize("testConfigurationByArn");

        assertEquals("topicName",               null,                           appender.getConfig().getTopicName());
        assertEquals("topicArn",                "arn-example",                  appender.getConfig().getTopicArn());

        assertEquals("subject",                 null,                           appender.getConfig().getSubject());
        assertFalse("autoCreate",                                               appender.getConfig().isAutoCreate());
        assertEquals("batch delay",             1L,                             appender.getConfig().getBatchDelay());
        assertFalse("truncate oversize messages",                               appender.getConfig().getTruncateOversizeMessages());
        assertEquals("discard threshold",       1000,                           appender.getConfig().getDiscardThreshold());
        assertEquals("discard action",          "oldest",                       appender.getConfig().getDiscardAction());
        assertTrue("use shutdown hook",                                         appender.getConfig().isUseShutdownHook());
        assertFalse("synchronous mode",                                         appender.getConfig().isSynchronous());
        assertEquals("assumed role",            null,                           appender.getConfig().getAssumedRole());
        assertEquals("client factory",          null,                           appender.getConfig().getClientFactory());
        assertEquals("client region",           null,                           appender.getConfig().getClientRegion());
        assertEquals("client endpoint",         null,                           appender.getConfig().getClientEndpoint());
    }


    @Test
    public void testSynchronousConfiguration() throws Exception
    {
        initialize("testSynchronousConfiguration");

        // all we care about is the interaction between synchronous and batchDelay

        assertTrue("synchronous mode",                                          appender.getConfig().isSynchronous());
        assertEquals("batch delay",         0L,                                 appender.getConfig().getBatchDelay());
    }


    @Test
    public void testManualConfiguration() throws Exception
    {
        SNSAppenderBuilder builder = new TestableSNSAppenderBuilder()
                                     .setName("test")
                                     .setTopicName("example")
                                     .setTopicArn("arn:example")
                                     .setSubject("This is a test")
                                     .setAutoCreate(true)
                                     .setBatchDelay(9876L)                      // this is ignored
                                     .setDiscardThreshold(123)
                                     .setDiscardAction(DiscardAction.newest.name())
                                     .setClientFactory("com.example.Foo.bar")
                                     .setClientRegion("us-west-1")
                                     .setClientEndpoint("sns.us-west-2.amazonaws.com")
                                     .setSynchronous(false)
                                     .setUseShutdownHook(false);


        appender = (TestableSNSAppender)builder.build();

        assertEquals("appender name",       "test",                         appender.getName());

        assertEquals("topicName",           "example",                      appender.getConfig().getTopicName());
        assertEquals("topicArn",            "arn:example",                  appender.getConfig().getTopicArn());

        assertEquals("subject",             "This is a test",               appender.getConfig().getSubject());
        assertTrue("autoCreate",                                            appender.getConfig().isAutoCreate());
        assertEquals("batch delay",         1L,                             appender.getConfig().getBatchDelay());
        assertEquals("discard threshold",   123,                            appender.getConfig().getDiscardThreshold());
        assertEquals("discard action",      "newest",                       appender.getConfig().getDiscardAction());
        assertEquals("client factory",      "com.example.Foo.bar",          appender.getConfig().getClientFactory());
        assertEquals("client region",       "us-west-1",                    appender.getConfig().getClientRegion());
        assertEquals("client endpoint",     "sns.us-west-2.amazonaws.com",  appender.getConfig().getClientEndpoint());
        assertFalse("use shutdown hook",                                    appender.getConfig().isUseShutdownHook());
        assertFalse("synchronous mode",                                     appender.getConfig().isSynchronous());
    }


    @Test
    public void testWriterInitialization() throws Exception
    {
        // property has to be set before initialization
        System.setProperty("TestSNSAppender.testWriterInitialization", "example");

        initialize("testWriterInitialization");

        assertEquals("configured topicName",            "name-{date}",                                          appender.getConfig().getTopicName());
        assertEquals("configured topicArn",             "arn-{date}",                                           appender.getConfig().getTopicArn());
        assertEquals("configured subect",               "{sysprop:TestSNSAppender.testWriterInitialization}",   appender.getConfig().getSubject());

        logger.debug("this triggers writer creation");

        MockSNSWriter writer = appender.getMockWriter();

        assertRegex("writer topicName",                 "name-20\\d{6}",                    writer.config.topicName);
        assertRegex("writer topicArn",                  "arn-20\\d{6}",                     writer.config.topicArn);
        assertEquals("writer subect",                   "example",                          writer.config.subject);
        assertTrue("writer autoCreate",                                                     writer.config.autoCreate);
        assertEquals("writer batch delay",              1L,                                 writer.config.batchDelay);
        assertEquals("writer discard threshold",        123,                                writer.config.discardThreshold);
        assertEquals("writer discard action",           DiscardAction.newest,               writer.config.discardAction);
        assertEquals("writer client factory method",    "com.example.Foo.bar",              writer.config.clientFactoryMethod);
        assertEquals("writer client endpoint",          "sns.us-east-2.amazonaws.com",      writer.config.clientEndpoint);
    }


    @Test
    public void testWriterInitializationWithLookups() throws Exception
    {
        // property has to be set before initialization
        System.setProperty("TestSNSAppender.testWriterInitialization", "example");

        initialize("testWriterInitializationWithLookups");

        assertEquals("configured topicName",            "name-${date:yyyyMMdd}-{date}",                     appender.getConfig().getTopicName());
        assertEquals("configured topicArn",             "arn-${awslogs:pid}-{pid}",                         appender.getConfig().getTopicArn());
        assertEquals("configured subect",               "${sys:TestSNSAppender.testWriterInitialization}",  appender.getConfig().getSubject());

        logger.debug("this triggers writer creation");

        MockSNSWriter writer = appender.getMockWriter();

        assertRegex("writer topicName",                 "name-20\\d{6}-20\\d{6}",           writer.config.topicName);
        assertRegex("writer topicArn",                  "arn-[0-9]{1,5}-[0-9]{1,5}",        writer.config.topicArn);
        assertEquals("writer subect",                   "example",                          writer.config.subject);
    }
}
