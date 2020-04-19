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

import junit.framework.AssertionFailedError;

import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import net.sf.kdgcommons.lang.ClassUtil;

import com.kdgregory.logging.aws.kinesis.KinesisLogWriter;
import com.kdgregory.logging.aws.kinesis.KinesisWriterStatistics;
import com.kdgregory.logging.test.AbstractKinesisAppenderIntegrationTest;
import com.kdgregory.logging.testhelpers.CommonTestHelper;
import com.kdgregory.logging.testhelpers.KinesisTestHelper;
import com.kdgregory.logging.testhelpers.RoleTestHelper;

import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;

import com.kdgregory.log4j2.aws.testhelpers.MessageWriter;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;


public class KinesisAppenderIntegrationTest
extends AbstractKinesisAppenderIntegrationTest
{

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Holds a logger instance and related objects, to support assertions.
     */
    public static class LoggerInfo
    implements LoggerAccessor
    {
        public Logger logger;
        public KinesisAppender appender;
        public KinesisWriterStatistics stats;

        public LoggerInfo(String loggerName, String appenderName)
        {
            LoggerContext context = LoggerContext.getContext();
            logger = context.getLogger(loggerName);
            appender = (KinesisAppender)logger.getAppenders().get(appenderName);
            stats = appender.getAppenderStatistics();
        }

        @Override
        public MessageWriter newMessageWriter(int numMessages)
        {
            return new MessageWriter(logger, numMessages);
        }

        @Override
        public KinesisLogWriter getWriter()
        throws Exception
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

        String propsName = "KinesisAppenderIntegrationTest/" + testName + ".xml";
        URI config = ClassLoader.getSystemResource(propsName).toURI();
        assertNotNull("was able to retrieve config", config);

        LoggerContext context = LoggerContext.getContext();
        context.setConfigLocation(config);

        // must reload after configuration
        localLogger = LoggerFactory.getLogger(getClass());
    }

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    {
        AbstractKinesisAppenderIntegrationTest.beforeClass();
    }


    @After
    @Override
    public void tearDown()
    {
        super.tearDown();
        MDC.clear();
    }


    @AfterClass
    public static void afterClass()
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
        altClient = AmazonKinesisClientBuilder.standard().withRegion("us-east-2").build();
        KinesisTestHelper altTestHelper = new KinesisTestHelper(altClient, "testAlternateRegion");

        // have to delete any eisting stream before initializing logger
        altTestHelper.deleteStreamIfExists();

        init("testAlternateRegion");
        super.testAlternateRegion(new LoggerInfo("TestLogger", "test"), altTestHelper);
    }


    @Test
    public void testAssumedRole() throws Exception
    {
        // we can't change the config, so will have to pass a role name that's unlikely
        // to be used by the user; also have to create it before loading config
        final String roleName = "KinesisAppenderIntegrationTest-Log4J2";

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
