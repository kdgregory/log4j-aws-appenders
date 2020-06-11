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

package com.kdgregory.logback.aws;

import static net.sf.kdgcommons.test.StringAsserts.*;

import java.util.concurrent.ConcurrentLinkedQueue;

import org.junit.Test;

import static org.junit.Assert.*;

import com.kdgregory.logback.testhelpers.cloudwatch.TestableCloudWatchAppender;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.common.util.InternalLogger;
import com.kdgregory.logging.common.util.RotationMode;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchWriter;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchWriterFactory;


/**
 *  These tests exercise appender logic specific to CloudWatchAppender, using a
 *  mock log-writer.
 */
public class TestCloudWatchAppender
extends AbstractUnitTest<TestableCloudWatchAppender>
{
    public TestCloudWatchAppender()
    {
        super("TestCloudWatchAppender/", "TEST");
    }


    @Test
    public void testConfiguration() throws Exception
    {
        initialize("testConfiguration");

        assertEquals("log group name",      "argle",                        appender.getLogGroup());
        assertEquals("log stream name",     "bargle",                       appender.getLogStream());
        assertEquals("retention period",    Integer.valueOf(7),             appender.getRetentionPeriod());
        assertTrue("dedicated writer",                                      appender.getDedicatedWriter());
        assertEquals("batch delay",         9876L,                          appender.getBatchDelay());
        assertEquals("sequence",            2,                              appender.getSequence());
        assertEquals("rotation mode",       "interval",                     appender.getRotationMode());
        assertEquals("rotation interval",   7200000L,                       appender.getRotationInterval());
        assertEquals("discard threshold",   12345,                          appender.getDiscardThreshold());
        assertEquals("discard action",      "newest",                       appender.getDiscardAction());
        assertFalse("synchronous mode",                                     appender.getSynchronous());
        assertFalse("use shutdown hook",                                    appender.getUseShutdownHook());
        assertEquals("assumed role",        "AssumableRole",                appender.getAssumedRole());
        assertEquals("client factory",      "com.example.Foo.bar",          appender.getClientFactory());
        assertEquals("client region",       "us-west-1",                    appender.getClientRegion());
        assertEquals("client endpoint",     "logs.us-west-2.amazonaws.com", appender.getClientEndpoint());
    }


    @Test
    public void testDefaultConfiguration() throws Exception
    {
        initialize("testDefaultConfiguration");

        // note: this is allowed at time of configuration, would disable logger if we try to append
        assertNull("log group name",                                        appender.getLogGroup());

        assertEquals("log stream name",     "{startupTimestamp}",           appender.getLogStream());
        assertEquals("retention period",    null,                           appender.getRetentionPeriod());
        assertFalse("dedicated writer",                                     appender.getDedicatedWriter());
        assertEquals("batch delay",         2000L,                          appender.getBatchDelay());
        assertEquals("sequence",            0,                              appender.getSequence());
        assertEquals("rotation mode",       "none",                         appender.getRotationMode());
        assertEquals("rotation interval",   -1,                             appender.getRotationInterval());
        assertEquals("discard threshold",   10000,                          appender.getDiscardThreshold());
        assertEquals("discard action",      "oldest",                       appender.getDiscardAction());
        assertFalse("synchronous mode",                                     appender.getSynchronous());
        assertTrue("use shutdown hook",                                     appender.getUseShutdownHook());
        assertEquals("assumed role",        null,                           appender.getAssumedRole());
        assertEquals("client factory",      null,                           appender.getClientFactory());
        assertEquals("client region",       null,                           appender.getClientRegion());
        assertEquals("client endpoint",     null,                           appender.getClientEndpoint());
    }


    @Test
    public void testInvalidRetentionPeriod() throws Exception
    {
        initialize("testInvalidRetentionPeriod");

        // retention period should retain its default value

        assertEquals("retention period",    null,                           appender.getRetentionPeriod());

        // everything else should be properly configured

        assertEquals("log group name",      "argle",                        appender.getLogGroup());
        assertEquals("log stream name",     "bargle",                       appender.getLogStream());
        assertTrue("dedicated writer",                                      appender.getDedicatedWriter());
        assertEquals("batch delay",         9876L,                          appender.getBatchDelay());
        assertEquals("sequence",            2,                              appender.getSequence());
        assertEquals("rotation mode",       "interval",                     appender.getRotationMode());
        assertEquals("rotation interval",   86400000L,                      appender.getRotationInterval());
        assertEquals("discard threshold",   12345,                          appender.getDiscardThreshold());
        assertEquals("discard action",      "newest",                       appender.getDiscardAction());
        assertEquals("client factory",      "com.example.Foo.bar",          appender.getClientFactory());
        assertEquals("client region",       "us-west-1",                    appender.getClientRegion());
        assertEquals("client endpoint",     "logs.us-west-2.amazonaws.com", appender.getClientEndpoint());
        assertFalse("synchronous mode",                                     appender.getSynchronous());
        assertFalse("use shutdown hook",                                    appender.getUseShutdownHook());
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


    @Test
    public void testExplicitRotation() throws Exception
    {
        initialize("testExplicitRotation");

        MockCloudWatchWriterFactory writerFactory = appender.getWriterFactory();

        logger.debug("first message");

        MockCloudWatchWriter writer0 = appender.getMockWriter();

        assertEquals("pre-rotate, writer factory calls",            1,          writerFactory.invocationCount);
        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.config.logStreamName);

        appender.rotate();

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertEquals("post-rotate, writer factory calls",           2,          writerFactory.invocationCount);
        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.config.logStreamName);

        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  0,          writer1.messages.size());

        // explicit rotation does not cause an internal log entry
        appenderInternalLogger.assertDebugLog();
    }


    @Test
    public void testCountedRotation() throws Exception
    {
        initialize("testCountedRotation");

        MockCloudWatchWriter writer0 = appender.getMockWriter();

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.config.logStreamName);

        // these messages should trigger rotation

        logger.debug("message 1");
        logger.debug("message 2");
        logger.debug("message 3");
        logger.debug("message 4");

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.config.logStreamName);
        assertEquals("post-rotate, messages passed to old writer",  3,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  1,          writer1.messages.size());

        // implicit rotation is logged internally
        appenderInternalLogger.assertDebugLog("rotating.*");
    }


    @Test
    public void testIntervalRotation() throws Exception
    {
        initialize("testIntervalRotation");

        MockCloudWatchWriter writer0 = appender.getMockWriter();

        logger.debug("first message");

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.config.logStreamName);

        appender.updateLastRotationTimestamp(-20000);

        logger.debug("second message");

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.config.logStreamName);
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  1,          writer1.messages.size());

        // implicit rotation is logged internally
        appenderInternalLogger.assertDebugLog("rotating.*");
    }


    @Test
    public void testHourlyRotation() throws Exception
    {
        initialize("testHourlyRotation");

        MockCloudWatchWriter writer0 = appender.getMockWriter();

        logger.debug("first message");

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.config.logStreamName);

        appender.updateLastRotationTimestamp(-3600000);

        logger.debug("second message");

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.config.logStreamName);
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  1,          writer1.messages.size());

        // implicit rotation is logged internally
        appenderInternalLogger.assertDebugLog("rotating.*");
    }


    @Test
    public void testDailyRotation() throws Exception
    {
        initialize("testDailyRotation");

        MockCloudWatchWriter writer0 = appender.getMockWriter();

        logger.debug("first message");

        assertEquals("pre-rotate, logstream name",                  "bargle-0", writer0.config.logStreamName);

        appender.updateLastRotationTimestamp(-86400000);

        logger.debug("second message");

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertNotSame("post-rotate, writer has been replaced",      writer0,    writer1);
        assertEquals("post-rotate, logstream name",                 "bargle-1", writer1.config.logStreamName);
        assertEquals("post-rotate, messages passed to old writer",  1,          writer0.messages.size());
        assertEquals("post-rotate, messages passed to new writer",  1,          writer1.messages.size());

        // implicit rotation is logged internally
        appenderInternalLogger.assertDebugLog("rotating.*");
    }


    @Test
    public void testInvalidRotationMode() throws Exception
    {
        initialize("testInvalidRotationMode");

        assertEquals("rotation mode", "none", appender.getRotationMode());
        appenderInternalLogger.assertErrorLog("invalid rotation mode.*bogus.*");
    }


    @Test
    public void testReconfigureRotation() throws Exception
    {
        initialize("testDailyRotation");

        // this message creates the writer
        logger.debug("first message");

        MockCloudWatchWriter writer0 = appender.getMockWriter();

        appender.updateLastRotationTimestamp(-7200000);

        // with daily rotation we should not rotate from this message
        logger.debug("second message");

        assertSame("still using original writer", writer0, appender.getMockWriter());
        appenderInternalLogger.assertDebugLog();

        appender.setRotationMode(RotationMode.hourly.toString());

        // this message should trigger rotation
        logger.debug("third message");

        appenderInternalLogger.assertDebugLog("rotating.*");

        MockCloudWatchWriter writer1 = appender.getMockWriter();

        assertNotSame("should be using new writer", writer0, writer1);

        assertEquals("messages passed to old writer",  2,          writer0.messages.size());
        assertEquals("messages passed to new writer",  1,          writer1.messages.size());
    }


    @Test
    public void testManyThreadsWithRotation() throws Exception
    {
        final int numThreads = 100;
        final int messagesPerThread = 1000;
        final int expectedTotalMessages = numThreads * messagesPerThread;
        final int rotationInterval = 3000;  // from config

        initialize("testManyThreadsWithRotation");

        // we need to capture new writers as they're created because we can't find them later

        final ConcurrentLinkedQueue<MockCloudWatchWriter> writers = new ConcurrentLinkedQueue<MockCloudWatchWriter>();
        appender.setWriterFactory(new MockCloudWatchWriterFactory()
        {
            @Override
            public LogWriter newLogWriter(CloudWatchWriterConfig config, CloudWatchWriterStatistics stats, InternalLogger ignored)
            {
                MockCloudWatchWriter newWriter = (MockCloudWatchWriter)super.newLogWriter(config, stats, ignored);
                writers.add(newWriter);
                return newWriter;
            }
        });

        // with logback, the first writer was created during initialization, so we need to capture it or our counts will be wrong

        writers.add(appender.getMockWriter());

        runLoggingThreads(numThreads, messagesPerThread);

        assertEquals("calls to append()", expectedTotalMessages, appender.appendInvocationCount.get());
        appenderInternalLogger.assertErrorLog();

        // note that we didn't count the initial writer invocation

        assertEquals("writer factory invocations", expectedTotalMessages / rotationInterval, appender.getWriterFactory().invocationCount);

        int actualTotalMessages = 0;
        for (MockCloudWatchWriter writer : writers)
        {
            actualTotalMessages += writer.messages.size();
        }

        assertEquals("total messages written", expectedTotalMessages, actualTotalMessages);
    }
}
