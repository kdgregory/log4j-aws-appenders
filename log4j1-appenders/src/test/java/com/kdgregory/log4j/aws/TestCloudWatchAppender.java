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

import java.net.URL;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import static net.sf.kdgcommons.test.StringAsserts.*;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.helpers.LogLog;

import com.kdgregory.log4j.testhelpers.cloudwatch.TestableCloudWatchAppender;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchWriter;


/**
 *  These tests exercise appender logic specific to CloudWatchAppender, using a
 *  mock log-writer.
 */
public class TestCloudWatchAppender
{
    private Logger logger;
    private TestableCloudWatchAppender appender;


    private void initialize(String testName)
    throws Exception
    {
        String propsName = "TestCloudWatchAppender/" + testName + ".properties";
        URL config = ClassLoader.getSystemResource(propsName);
        assertNotNull("was able to retrieve config", config);
        PropertyConfigurator.configure(config);

        logger = Logger.getLogger(getClass());

        Logger rootLogger = Logger.getRootLogger();
        appender = (TestableCloudWatchAppender)rootLogger.getAppender("default");
    }

//----------------------------------------------------------------------------
//  JUnit stuff
//----------------------------------------------------------------------------

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
    public void testConfiguration() throws Exception
    {
        initialize("testConfiguration");

        assertEquals("log group name",      "argle",                        appender.getLogGroup());
        assertEquals("log stream name",     "bargle",                       appender.getLogStream());
        assertFalse("synchronous mode",                                     appender.getSynchronous());
        assertEquals("batch delay",         9876L,                          appender.getBatchDelay());
        assertEquals("sequence",            2,                              appender.getSequence());
        assertEquals("rotation mode",       "interval",                     appender.getRotationMode());
        assertEquals("rotation interval",   86400000L,                      appender.getRotationInterval());
        assertEquals("discard threshold",   12345,                          appender.getDiscardThreshold());
        assertEquals("discard action",      "newest",                       appender.getDiscardAction());
        assertEquals("client factory",      "com.example.Foo.bar",          appender.getClientFactory());
        assertEquals("client endpoint",     "logs.us-west-2.amazonaws.com", appender.getClientEndpoint());
        assertFalse("use shutdown hook",                                    appender.getUseShutdownHook());
    }


    @Test
    public void testDefaultConfiguration() throws Exception
    {
        initialize("testDefaultConfiguration");

        // note: this is allowed at time of configuration, would disable logger if we try to append
        assertNull("log group name",                                        appender.getLogGroup());

        assertEquals("log stream name",     "{startupTimestamp}",           appender.getLogStream());
        assertFalse("synchronous mode",                                     appender.getSynchronous());
        assertEquals("batch delay",         2000L,                          appender.getBatchDelay());
        assertEquals("sequence",            0,                              appender.getSequence());
        assertEquals("rotation mode",       "none",                         appender.getRotationMode());
        assertEquals("rotation interval",   -1,                             appender.getRotationInterval());
        assertEquals("discard threshold",   10000,                          appender.getDiscardThreshold());
        assertEquals("discard action",      "oldest",                       appender.getDiscardAction());
        assertEquals("client factory",      null,                           appender.getClientFactory());
        assertEquals("client endpoint",     null,                           appender.getClientEndpoint());
        assertTrue("use shutdown hook",                                     appender.getUseShutdownHook());
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
        System.setProperty("TestCloudWatchAppender.testWriterInitialization", "example");

        initialize("testWriterInitialization");

        assertEquals("configured log group name",   "MyLog-{sysprop:TestCloudWatchAppender.testWriterInitialization}",  appender.getLogGroup());
        assertEquals("configured log stream name",  "MyStream-{date}-{bogus}",                                          appender.getLogStream());

        logger.debug("this triggers writer creation");

        MockCloudWatchWriter writer = appender.getMockWriter();

        assertEquals("writer log group name",           "MyLog-example",                    writer.config.logGroupName);
        assertRegex("writer log stream name",           "MyStream-20\\d{6}-\\{bogus}",      writer.config.logStreamName);
        assertEquals("writer batch delay",              9876L,                              writer.config.batchDelay);
        assertEquals("writer discard threshold",        12345,                              writer.config.discardThreshold);
        assertEquals("writer discard action",           DiscardAction.newest,               writer.config.discardAction);
        assertEquals("writer client factory method",    "com.example.Foo.bar",              writer.config.clientFactoryMethod);
        assertEquals("writer client endpoint",          "logs.us-west-2.amazonaws.com",     writer.config.clientEndpoint);
    }
}
