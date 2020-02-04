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

import java.net.URI;

import org.junit.Test;
import static org.junit.Assert.*;

import static net.sf.kdgcommons.test.StringAsserts.*;

import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import com.kdgregory.log4j2.testhelpers.TestableCloudWatchAppender;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchWriter;


public class TestCloudWatchAppender
{
    private Logger logger;
    private TestableCloudWatchAppender appender;


    private void initialize(String testName)
    throws Exception
    {
        String propsName = "TestCloudWatchAppender/" + testName + ".xml";
        URI config = ClassLoader.getSystemResource(propsName).toURI();
        assertNotNull("was able to retrieve config", config);

        LoggerContext context = LoggerContext.getContext();
        context.setConfigLocation(config);

        logger = context.getLogger(getClass().getName());
        appender = (TestableCloudWatchAppender)logger.getAppenders().get("CLOUDWATCH");
        assertNotNull("was able to retrieve appender", appender);
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testConfiguration() throws Exception
    {
        initialize("testConfiguration");

        System.out.println("this is success!");

        assertEquals("log group name",      "argle",                        appender.getConfig().getLogGroup());
        assertEquals("log stream name",     "bargle",                       appender.getConfig().getLogStream());
        assertTrue("dedicated writer",                                      appender.getConfig().isDedicatedWriter());
        assertEquals("batch delay",         9876L,                          appender.getConfig().getBatchDelay());
        assertEquals("sequence",            2,                              appender.getConfig().getSequence());
        assertEquals("rotation mode",       "interval",                     appender.getConfig().getRotationMode());
        assertEquals("rotation interval",   86400000L,                      appender.getConfig().getRotationInterval());
        assertEquals("discard threshold",   12345,                          appender.getConfig().getDiscardThreshold());
        assertEquals("discard action",      "newest",                       appender.getConfig().getDiscardAction());
        assertEquals("client factory",      "com.example.Foo.bar",          appender.getConfig().getClientFactory());
        assertEquals("client region",       "us-west-1",                    appender.getConfig().getClientRegion());
        assertEquals("client endpoint",     "logs.us-west-2.amazonaws.com", appender.getConfig().getClientEndpoint());
        assertFalse("synchronous mode",                                     appender.getConfig().isSynchronous());
        assertFalse("use shutdown hook",                                    appender.getConfig().isUseShutdownHook());

        // the appender holds retention period separate from configuration, so check it separately

        assertEquals("retention period",    Integer.valueOf(7),             appender.getRetentionPeriod());
    }


    @Test
    public void testDefaultConfiguration() throws Exception
    {
        initialize("testDefaultConfiguration");

        // note: this is allowed at time of configuration, would disable logger if we try to append
        assertNull("log group name",                                        appender.getConfig().getLogGroup());

        assertEquals("log stream name",     "{startupTimestamp}",           appender.getConfig().getLogStream());
        assertFalse("dedicated writer",                                     appender.getConfig().isDedicatedWriter());
        assertEquals("batch delay",         2000L,                          appender.getConfig().getBatchDelay());
        assertEquals("sequence",            0,                              appender.getConfig().getSequence());
        assertEquals("rotation mode",       "none",                         appender.getConfig().getRotationMode());
        assertEquals("rotation interval",   -1,                             appender.getConfig().getRotationInterval());
        assertEquals("discard threshold",   10000,                          appender.getConfig().getDiscardThreshold());
        assertEquals("discard action",      "oldest",                       appender.getConfig().getDiscardAction());
        assertEquals("client factory",      null,                           appender.getConfig().getClientFactory());
        assertEquals("client region",       null,                           appender.getConfig().getClientRegion());
        assertEquals("client endpoint",     null,                           appender.getConfig().getClientEndpoint());
        assertFalse("synchronous mode",                                     appender.getConfig().isSynchronous());
        assertTrue("use shutdown hook",                                     appender.getConfig().isUseShutdownHook());

        // the appender holds retention period separate from configuration, so check it separately

        assertEquals("retention period",    null,                           appender.getRetentionPeriod());
    }


    @Test
    public void testInvalidRetentionPeriod() throws Exception
    {
        initialize("testInvalidRetentionPeriod");

        // retention period should retain its default value

        assertEquals("retention period",    null,                           appender.getRetentionPeriod());

        // everything else should be properly configured

        assertEquals("log group name",      "argle",                        appender.getConfig().getLogGroup());
        assertEquals("log stream name",     "bargle",                       appender.getConfig().getLogStream());
        assertTrue("dedicated writer",                                      appender.getConfig().isDedicatedWriter());
        assertEquals("batch delay",         9876L,                          appender.getConfig().getBatchDelay());
        assertEquals("sequence",            2,                              appender.getConfig().getSequence());
        assertEquals("rotation mode",       "interval",                     appender.getConfig().getRotationMode());
        assertEquals("rotation interval",   86400000L,                      appender.getConfig().getRotationInterval());
        assertEquals("discard threshold",   12345,                          appender.getConfig().getDiscardThreshold());
        assertEquals("discard action",      "newest",                       appender.getConfig().getDiscardAction());
        assertEquals("client factory",      "com.example.Foo.bar",          appender.getConfig().getClientFactory());
        assertEquals("client region",       "us-west-1",                    appender.getConfig().getClientRegion());
        assertEquals("client endpoint",     "logs.us-west-2.amazonaws.com", appender.getConfig().getClientEndpoint());
        assertFalse("synchronous mode",                                     appender.getConfig().isSynchronous());
        assertFalse("use shutdown hook",                                    appender.getConfig().isUseShutdownHook());
    }


    @Test
    public void testSynchronousConfiguration() throws Exception
    {
        initialize("testSynchronousConfiguration");

        // all we care about is the interaction between synchronous and batchDelay, so no need to check anything else

        assertTrue("synchronous mode",                                      appender.getConfig().isSynchronous());
        assertEquals("batch delay",         0L,                             appender.getConfig().getBatchDelay());
    }


    @Test
    public void testWriterInitialization() throws Exception
    {
        // property has to be set before initialization
        System.setProperty("TestCloudWatchAppender.testWriterInitialization", "example");

        initialize("testWriterInitialization");

        assertEquals("configured log group name",   "MyLog-{sysprop:TestCloudWatchAppender.testWriterInitialization}",  appender.getConfig().getLogGroup());
        assertEquals("configured log stream name",  "MyStream-{date}-{bogus}",                                          appender.getConfig().getLogStream());

        MockCloudWatchWriter writer = appender.getMockWriter();

        assertEquals("writer log group name",           "MyLog-example",                    writer.config.logGroupName);
        assertRegex("writer log stream name",           "MyStream-20\\d{6}-\\{bogus}",      writer.config.logStreamName);
        assertEquals("writer retention period",         Integer.valueOf(14),                writer.config.retentionPeriod);
        assertEquals("writer batch delay",              9876L,                              writer.config.batchDelay);
        assertEquals("writer discard threshold",        12345,                              writer.config.discardThreshold);
        assertEquals("writer discard action",           DiscardAction.newest,               writer.config.discardAction);
        assertEquals("writer client factory method",    "com.example.Foo.bar",              writer.config.clientFactoryMethod);
        assertEquals("writer client endpoint",          "logs.us-west-2.amazonaws.com",     writer.config.clientEndpoint);
    }
}
