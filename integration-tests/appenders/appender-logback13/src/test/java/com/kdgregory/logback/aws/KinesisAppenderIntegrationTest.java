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

import java.net.URL;

import junit.framework.AssertionFailedError;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;

import com.kdgregory.logback.aws.testhelpers.MessageWriter;
import com.kdgregory.logging.aws.kinesis.KinesisLogWriter;
import com.kdgregory.logging.aws.kinesis.KinesisWriterStatistics;
import com.kdgregory.logging.test.AbstractKinesisAppenderIntegrationTest;
import com.kdgregory.logging.testhelpers.KinesisTestHelper;
import com.kdgregory.logging.testhelpers.RoleTestHelper;
import com.kdgregory.logging.testhelpers.CommonTestHelper;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;

import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import net.sf.kdgcommons.lang.ClassUtil;


public class KinesisAppenderIntegrationTest
extends AbstractKinesisAppenderIntegrationTest
{

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Retrieves and holds a logger instance and related objects.
     */
    public static class LoggerInfo
    implements LoggerAccessor
    {
        public ch.qos.logback.classic.Logger logger;
        public KinesisAppender<ILoggingEvent> appender;
        public KinesisWriterStatistics stats;

        public LoggerInfo(String loggerName, String appenderName)
        {
            logger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(loggerName);
            appender = (KinesisAppender<ILoggingEvent>)logger.getAppender(appenderName);
            stats = appender.getAppenderStatistics();
        }

        @Override
        public MessageWriter newMessageWriter(int numMessages)
        {
            return new MessageWriter(logger, numMessages);
        }

        @Override
        public KinesisLogWriter getWriter() throws Exception
        {
            return ClassUtil.getFieldValue(appender, "writer", KinesisLogWriter.class);
        }

        @Override
        public KinesisWriterStatistics getStats()
        {
            return stats;
        }

        @Override
        public String waitUntilWriterInitialized() throws Exception
        {
            CommonTestHelper.waitUntilWriterInitialized(appender, KinesisLogWriter.class, 10000);
            return stats.getLastErrorMessage();
        }
    }


    /**
     *  Loads the test-specific Logback configuration and resets the environment.
     */
    public void init(String testName) throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        testHelper = new KinesisTestHelper(helperClient, testName);

        // this has to happen before the logger is initialized or we have a race condition
        testHelper.deleteStreamIfExists();

        String propertiesName = "KinesisAppenderIntegrationTest/" + testName + ".xml";
        URL config = ClassLoader.getSystemResource(propertiesName);
        assertNotNull("missing configuration: " + propertiesName, config);

        LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
        context.reset();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(config);

        localLogger = LoggerFactory.getLogger(getClass());
    }

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    throws Exception
    {
        AbstractKinesisAppenderIntegrationTest.beforeClass();
    }


    @After
    @Override
    public void tearDown()
    throws Exception
    {
        super.tearDown();
        MDC.clear();
    }


    @AfterClass
    public static void afterClass()
    throws Exception
    {
        AbstractKinesisAppenderIntegrationTest.afterClass();
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void smoketest() throws Exception
    {
        init("smoketest");
        super.smoketest(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testMultipleThreadsSingleAppender() throws Exception
    {
        init("testMultipleThreadsSingleAppender");
        super.testMultipleThreadsSingleAppender(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testMultipleThreadsMultipleAppendersDistinctPartitions() throws Exception
    {
        init("testMultipleThreadsMultipleAppendersDistinctPartitions");
        super.testMultipleThreadsMultipleAppendersDistinctPartitions(
            new LoggerInfo("TestLogger1", "test1"),
            new LoggerInfo("TestLogger2", "test2"),
            new LoggerInfo("TestLogger3", "test3"));
    }


    @Test
    public void testRandomPartitionKeys() throws Exception
    {
        init("testRandomPartitionKeys");
        super.testRandomPartitionKeys(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testFailsIfNoStreamPresent() throws Exception
    {
        init("testFailsIfNoStreamPresent");
        super.testFailsIfNoStreamPresent(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testFactoryMethod() throws Exception
    {
        init("testFactoryMethod");
        super.testFactoryMethod(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testAlternateRegion() throws Exception
    {
        // BEWARE: my default region is us-east-1, so I use us-east-2 as the alternate
        //         if that is your default, then the test will fail
        altClient = AmazonKinesisClientBuilder.standard().withRegion(Regions.US_EAST_2).build();
        KinesisTestHelper altTestHelper = new KinesisTestHelper(altClient, "testAlternateRegion");

        // have to delete any eisting stream before initializing logger
        altTestHelper.deleteStreamIfExists();

        init("testAlternateRegion");
        super.testAlternateRegion(new LoggerInfo("TestLogger", "test"), altTestHelper);
    }


    @Test
    public void testAlternateEndpoint() throws Exception
    {
        // BEWARE: my default region is us-east-1, so I use us-east-2 as the alternate
        //         if that is your default, then the test will fail
        altClient = AmazonKinesisClientBuilder.standard().withRegion(Regions.US_EAST_2).build();
        KinesisTestHelper altTestHelper = new KinesisTestHelper(altClient, "testAlternateEndpoint");

        // have to delete any eisting stream before initializing logger
        altTestHelper.deleteStreamIfExists();

        init("testAlternateEndpoint");
        super.testAlternateRegion(new LoggerInfo("TestLogger", "test"), altTestHelper);
    }


    @Test
    public void testAssumedRole() throws Exception
    {
        // we can't change the config, so will have to pass a role name that's unlikely
        // to be used by the user; also have to create it before loading config
        final String roleName = "KinesisAppenderIntegrationTest-Logback";

        RoleTestHelper roleHelper = new RoleTestHelper();
        try
        {
            Role role = roleHelper.createRole(roleName, "arn:aws:iam::aws:policy/AmazonKinesisFullAccess");
            roleHelper.waitUntilRoleAssumable(role.getArn(), 60);
            init("testAssumedRole");
            super.testAssumedRole(new LoggerInfo("TestLogger", "test"));
        }
        catch (AssertionFailedError ex)
        {
            // the next catch clause will swallow the assertion if we didn't do this
            throw ex;
        }
        catch (Exception ex)
        {
            localLogger.warn("unexpected exception", ex);
            fail("unexpected exception: " + ex.getMessage());
        }
        finally
        {
            roleHelper.deleteRole(roleName);
            roleHelper.shutdown();
        }
    }
}