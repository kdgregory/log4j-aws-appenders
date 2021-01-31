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

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;

import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import net.sf.kdgcommons.lang.ClassUtil;

import com.amazonaws.services.identitymanagement.model.Role;

import com.kdgregory.logback.aws.testhelpers.MessageWriter;
import com.kdgregory.logging.aws.sns.SNSLogWriter;
import com.kdgregory.logging.aws.sns.SNSWriterStatistics;
import com.kdgregory.logging.test.AbstractSNSAppenderIntegrationTest;
import com.kdgregory.logging.testhelpers.SNSTestHelper;
import com.kdgregory.logging.testhelpers.CommonTestHelper;
import com.kdgregory.logging.testhelpers.RoleTestHelper;


public class SNSAppenderIntegrationTest
extends AbstractSNSAppenderIntegrationTest
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
        public SNSAppender<ILoggingEvent> appender;
        public SNSWriterStatistics stats;

        public LoggerInfo(String loggerName, String appenderName)
        {
            logger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(loggerName);
            appender = (SNSAppender<ILoggingEvent>)logger.getAppender(appenderName);
            stats = appender.getAppenderStatistics();
        }

        @Override
        public MessageWriter newMessageWriter(int numMessages)
        {
            return new MessageWriter(logger, numMessages);
        }

        @Override
        public String waitUntilWriterInitialized()
        throws Exception
        {
            CommonTestHelper.waitUntilWriterInitialized(appender, SNSLogWriter.class, 30000);
            return stats.getLastErrorMessage();
        }

        @Override
        public SNSLogWriter getWriter() throws Exception
        {
            return ClassUtil.getFieldValue(appender, "writer", SNSLogWriter.class);
        }

        @Override
        public SNSWriterStatistics getStats()
        {
            return stats;
        }
    }


    /**
     *  Loads the test-specific Log4J configuration and resets the environment.
     */
    public void init(String testName, boolean createTopic) throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        testHelper = new SNSTestHelper(helperSNSclient, helperSQSclient);

        // if we're going to create the topic we must do it before initializing the logging system
        if (createTopic)
        {
            testHelper.createTopicAndQueue();
        }

        String propertiesName = "SNSAppenderIntegrationTest/" + testName + ".xml";
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
        AbstractSNSAppenderIntegrationTest.beforeClass();
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
        AbstractSNSAppenderIntegrationTest.afterClass();
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void smoketestByArn() throws Exception
    {
        init("smoketestByArn", true);
        super.smoketestByArn(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void smoketestByName() throws Exception
    {
        init("smoketestByName", true);
        super.smoketestByName(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testTopicMissingAutoCreate() throws Exception
    {
        init("testTopicMissingAutoCreate", false);
        super.testTopicMissingAutoCreate(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testTopicMissingNoAutoCreate() throws Exception
    {
        init("testTopicMissingNoAutoCreate", false);
        LoggerInfo loggerInfo = new LoggerInfo("TestLogger", "test");

        // initializing the logging framework triggers logger creation, no need to send a message
        super.testTopicMissingNoAutoCreate(loggerInfo);
    }


    @Test
    public void testMultiThread() throws Exception
    {
        init("testMultiThread", true);
        super.testMultiThread(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testMultiAppender() throws Exception
    {
        init("testMultiAppender", true);
        super.testMultiAppender(
            new LoggerInfo("TestLogger", "test1"),
            new LoggerInfo("TestLogger", "test2"));
    }


    @Test
    public void testFactoryMethod() throws Exception
    {
        init("testFactoryMethod", true);
        super.testFactoryMethod(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testAlternateRegion() throws Exception
    {
        init("testAlternateRegion", false);
        super.testAlternateRegion(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testAlternateEndpoint() throws Exception
    {
        init("testAlternateEndpoint", false);
        super.testAlternateRegion(new LoggerInfo("TestLogger", "test"));
    }


    @Test
    public void testAssumedRole() throws Exception
    {
        // we can't change the config, so will have to pass a role name that's unlikely
        // to be used by the user; also have to create it before loading config
        final String roleName = "SNSAppenderIntegrationTest-Logback";

        RoleTestHelper roleHelper = new RoleTestHelper();
        try
        {
            Role role = roleHelper.createRole(roleName, "arn:aws:iam::aws:policy/AmazonSNSFullAccess");
            roleHelper.waitUntilRoleAssumable(role.getArn(), 60);
            init("testAssumedRole", true);
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
