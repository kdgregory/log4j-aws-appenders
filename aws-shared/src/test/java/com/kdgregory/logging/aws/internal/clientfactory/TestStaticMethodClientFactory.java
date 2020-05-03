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

package com.kdgregory.logging.aws.internal.clientfactory;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import net.sf.kdgcommons.test.SelfMock;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.InvalidOperationException;

import com.kdgregory.logging.common.factories.ClientFactoryException;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;


public class TestStaticMethodClientFactory
{
    private TestableInternalLogger logger = new TestableInternalLogger();

    // set by factory-method tests
    private static AWSLogs clientFromFactory;
    private static String factoryParamAssumedRole;
    private static String factoryParamEndpoint;
    private static String factoryParamRegion;

//----------------------------------------------------------------------------
//  Factory method implementations
//----------------------------------------------------------------------------

    public static AWSLogs baseFactoryMethod()
    {
        SelfMock<AWSLogs> mock = new SelfMock<AWSLogs>(AWSLogs.class) { /* empty */ };
        clientFromFactory = mock.getInstance();
        return clientFromFactory;
    }


    public static AWSLogs parameterizedFactoryMethod(String assumedRole, String region, String endpoint)
    {
        factoryParamAssumedRole = assumedRole;
        factoryParamEndpoint = endpoint;
        factoryParamRegion = region;
        return baseFactoryMethod();
    }


    public static AWSLogs throwingFactoryMethod()
    {
        throw new InvalidOperationException("not today, not ever");
    }

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        // these may have been set by previous test
        clientFromFactory = null;
        factoryParamAssumedRole = null;
        factoryParamEndpoint = null;
        factoryParamRegion = null;
    }
//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testFactoryMethod() throws Exception
    {
        String factoryMethodName = getClass().getName() + ".baseFactoryMethod";

        StaticMethodClientFactory<AWSLogs> factory = new StaticMethodClientFactory<>(AWSLogs.class, factoryMethodName, null, null, null, logger);

        AWSLogs client = factory.createClient();
        assertNotNull("created a client",           client);
        assertSame("client created via factory",    client, clientFromFactory);

        logger.assertInternalDebugLog(".*factory.*" + factoryMethodName);
        logger.assertInternalErrorLog();
    }


    @Test
    public void testFactoryMethodWithConfig() throws Exception
    {
        String factoryMethodName = getClass().getName() + ".parameterizedFactoryMethod";
        String assumedRole = "arn:aws:iam::123456789012:role/AssumableRole";
        final String region = "eu-west-3";
        final String endpoint = "logs." + region + ".amazonaws.com";

        StaticMethodClientFactory<AWSLogs> factory = new StaticMethodClientFactory<>(AWSLogs.class, factoryMethodName, assumedRole, region, endpoint, logger);

        AWSLogs client = factory.createClient();
        assertNotNull("created a client",  client);
        assertSame("client created via factory",    client,         clientFromFactory);
        assertEquals("role provided",               assumedRole,    factoryParamAssumedRole);
        assertEquals("region provided",             region,         factoryParamRegion);
        assertEquals("endpoint provided",           endpoint,       factoryParamEndpoint);

        logger.assertInternalDebugLog(".*factory.*" + factoryMethodName);
        logger.assertInternalErrorLog();
    }


    @Test
    public void testFactoryMethodBogusName() throws Exception
    {
        String factoryMethodName = "completelybogus";

        StaticMethodClientFactory<AWSLogs> factory = new StaticMethodClientFactory<>(AWSLogs.class, factoryMethodName, null, null, null, logger);

        try
        {
            factory.createClient();
            fail("should have thrown");
        }
        catch (ClientFactoryException ex)
        {
            assertEquals("exception message", "invalid factory method: " + factoryMethodName, ex.getMessage());
        }

        logger.assertInternalDebugLog(".*factory.*" + factoryMethodName);
        logger.assertInternalErrorLog();
    }


    @Test
    public void testFactoryMethodNoSuchClass() throws Exception
    {
        String factoryMethodName = "com.example.bogus";

        StaticMethodClientFactory<AWSLogs> factory = new StaticMethodClientFactory<>(AWSLogs.class, factoryMethodName, null, null, null, logger);

        try
        {
            factory.createClient();
            fail("should have thrown");
        }
        catch (ClientFactoryException ex)
        {
            assertEquals("exception message",   "invalid factory method: " + factoryMethodName,     ex.getMessage());
            assertSame("wrapped exception",     ClassNotFoundException.class,                       ex.getCause().getClass());
        }

        logger.assertInternalDebugLog(".*factory.*" + factoryMethodName);
        logger.assertInternalErrorLog();
    }


    @Test
    public void testFactoryMethodNoSuchMethod() throws Exception
    {
        String factoryMethodName = getClass().getName() + ".bogus";

        StaticMethodClientFactory<AWSLogs> factory = new StaticMethodClientFactory<>(AWSLogs.class, factoryMethodName, null, null, null, logger);

        try
        {
            factory.createClient();
            fail("should have thrown");
        }
        catch (ClientFactoryException ex)
        {
            assertEquals("exception message", "invalid factory method: " + factoryMethodName, ex.getMessage());
        }

        logger.assertInternalDebugLog(".*factory.*" + factoryMethodName);
        logger.assertInternalErrorLog();
    }


    @Test
    public void testFactoryMethodException() throws Exception
    {
        // this test is alsmost identical to the previous, but has a different exception cause

        String factoryMethodName = getClass().getName() + ".throwingFactoryMethod";

        StaticMethodClientFactory<AWSLogs> factory = new StaticMethodClientFactory<>(AWSLogs.class, factoryMethodName, null, null, null, logger);

        try
        {
            factory.createClient();
            fail("should have thrown");
        }
        catch (ClientFactoryException ex)
        {
            assertEquals("exception message",   "factory method error: " + factoryMethodName,   ex.getMessage());
            assertEquals("underlying cause",    InvalidOperationException.class,                ex.getCause().getClass());
        }

        logger.assertInternalDebugLog(".*factory.*" + factoryMethodName);
        logger.assertInternalErrorLog();
    }
}
