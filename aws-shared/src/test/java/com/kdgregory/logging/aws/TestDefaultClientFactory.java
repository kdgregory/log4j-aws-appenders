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

package com.kdgregory.logging.aws;

import java.net.URI;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.lang.StringUtil;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.InvalidOperationException;

import com.kdgregory.logging.aws.common.DefaultClientFactory;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchClient;


/**
 *  Verifies operation of the client factory. This is a whitebox test: it
 *  overrides various factory methods to record their invocation. It relies
 *  on being built with SDK version 1.11.0; changing the build version may
 *  break tests.
 *  <p>
 *  Note: most of the tests use MockCloudWatchClient, but the constructor
 *  tests need to use an actual client because the factory calls methods
 *  that aren't implemented by the interface (and therefore can't be mocked
 *  via proxy).
 */
public class TestDefaultClientFactory
{
    private TestableInternalLogger logger = new TestableInternalLogger();

    // these variables track the order of calls to the tree creation mechanisms
    private AtomicInteger invocationCounter = new AtomicInteger(0);
    private AtomicInteger factoryCalledAt = new AtomicInteger(0);
    private AtomicInteger builderCalledAt = new AtomicInteger(0);
    private AtomicInteger constructorCalledAt = new AtomicInteger(0);

    // set by createMockClient()
    private static AWSLogs clientFromFactory;

    // set by MockClientBuilder
    private static AWSLogs clientFromBuilder;
    private static String clientBuilderRegion;

//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

    // used by factory method tests
    public static AWSLogs createMockClient()
    {
        try
        {
            clientFromFactory = new MockCloudWatchClient().createClient();
            return clientFromFactory;
        }
        catch (Exception ex)
        {
            fail("exception in static factory: " + ex);
            return null;
        }
    }


    public static AWSLogs throwingFactoryMethod()
    {
        throw new InvalidOperationException("me no work");
    }


    // a simulation of AWSLogsClientBuilder, since the real thing doesn't exist
    public static class MockClientBuilder
    {
        public static MockClientBuilder standard()
        {
            return new MockClientBuilder();
        }

        public AWSLogs build()
        {
            clientFromBuilder = new MockCloudWatchClient().createClient();
            return clientFromBuilder;
        }

        public void setRegion(String region)
        {
            clientBuilderRegion = region;
        }
    }


    /**
     *  Digs into the actual client object to retrieve its endpoint, and
     *  asserts the region. This relies on knowing the implementation of
     *  the client object.
     */
    private void assertEndpointRegion(Object client, String expectedRegion)
    throws Exception
    {
        String actualEndpoint = ClassUtil.getFieldValue(client, "endpoint", URI.class).toString();
        String expectedEndpoint = "https://logs." + expectedRegion + ".amazonaws.com";
        assertEquals("client endpoint", expectedEndpoint, actualEndpoint);
    }

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        // these may have been set by previous test
        clientFromFactory = null;
        clientFromBuilder = null;
        clientBuilderRegion = null;
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testOrderOfOperations() throws Exception
    {
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, null, null, logger)
        {
            @Override
            protected AWSLogs tryFactory()
            {
                factoryCalledAt.set(invocationCounter.incrementAndGet());
                return null;
            }

            @Override
            public AWSLogs tryBuilder()
            {
                builderCalledAt.set(invocationCounter.incrementAndGet());
                return null;
            }

            @Override
            protected AWSLogs tryConstructor()
            {
                constructorCalledAt.set(invocationCounter.incrementAndGet());
                return null;
            }
        };

        AWSLogs client = factory.createClient();
        assertNull("didn't actually create a client", client);

        assertEquals("tried all mechanisms",    3,  invocationCounter.get());
        assertEquals("factory tried first",     1,  factoryCalledAt.get());
        assertEquals("builder tried second",    2,  builderCalledAt.get());
        assertEquals("constructor tried third", 3,  constructorCalledAt.get());

        logger.assertInternalDebugLog();
        logger.assertInternalErrorLog();
    }


    @Test
    public void testStaticClientFactory() throws Exception
    {
        String factoryMethodName = getClass().getName() + ".createMockClient";

        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, factoryMethodName, null, null, logger)
        {
            @Override
            public AWSLogs tryBuilder()
            {
                throw new IllegalStateException("should not have gotten here");
            }

            @Override
            protected AWSLogs tryConstructor()
            {
                throw new IllegalStateException("should not have gotten here");
            }
        };

        AWSLogs client = factory.createClient();
        assertNotNull("actually created a client",  client);
        assertSame("client created via factory",    client, clientFromFactory);

        logger.assertInternalDebugLog("creating client via factory.*" + factoryMethodName);
        logger.assertInternalErrorLog();
    }


    @Test
    public void testBogusFactoryMethodName() throws Exception
    {
        String factoryMethodName = "com.example.Bogus.bogus";
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, factoryMethodName, null, null, logger)
        {
            // our goal is to cleanly recover from bad method name, not actually create a client, so other mechanisms just record invocation

            @Override
            public AWSLogs tryBuilder()
            {
                builderCalledAt.set(invocationCounter.incrementAndGet());
                return null;
            }

            @Override
            protected AWSLogs tryConstructor()
            {
                constructorCalledAt.set(invocationCounter.incrementAndGet());
                return null;
            }
        };

        AWSLogs client = factory.createClient();
        assertNull("didn't actually create a client", client);
        assertTrue("called builder mechanism", builderCalledAt.get() > 0);
        assertTrue("called constructor mechanism", constructorCalledAt.get() > 0);

        logger.assertInternalDebugLog("creating client via factory.*" + factoryMethodName);
        logger.assertInternalErrorLog("failed to create client.*" + factoryMethodName);
    }


    @Test
    public void testThrowingClientFactory() throws Exception
    {
        String factoryMethodName = getClass().getName() + ".throwingFactoryMethod";

        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, factoryMethodName, null, null, logger)
        {
            // again, we just want to cleanly recover, so will verify that other mechanisms were tried

            @Override
            public AWSLogs tryBuilder()
            {
                builderCalledAt.set(invocationCounter.incrementAndGet());
                return null;
            }

            @Override
            protected AWSLogs tryConstructor()
            {
                constructorCalledAt.set(invocationCounter.incrementAndGet());
                return null;
            }
        };

        AWSLogs client = factory.createClient();
        assertNull("didn't actually create a client", client);
        assertTrue("called builder mechanism", builderCalledAt.get() > 0);
        assertTrue("called constructor mechanism", constructorCalledAt.get() > 0);

        logger.assertInternalDebugLog("creating client via factory.*" + factoryMethodName);
        logger.assertInternalErrorLog("failed to create client.*" + factoryMethodName);
    }


    @Test
    public void testClientBuilder() throws Exception
    {
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, null, null, logger)
        {
            // for this test we want to exercise the full builder code path
            {
                factoryClasses.put("com.amazonaws.services.logs.AWSLogs", MockClientBuilder.class.getName());
            }

            @Override
            protected AWSLogs tryConstructor()
            {
                throw new IllegalStateException("should not have gotten here");
            }
        };

        AWSLogs client = factory.createClient();
        assertNotNull("actually created a client",  client);
        assertSame("client created via build",      client, clientFromBuilder);

        logger.assertInternalDebugLog("creating client via SDK builder");
        logger.assertInternalErrorLog();
    }


    @Test
    public void testClientBuilderWithRegion() throws Exception
    {
        final String region = "us-west-1";

        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, region, null, logger)
        {
            // for this test we want to exercise the full builder code path
            {
                factoryClasses.put("com.amazonaws.services.logs.AWSLogs", MockClientBuilder.class.getName());
            }

            @Override
            protected AWSLogs tryConstructor()
            {
                throw new IllegalStateException("should not have gotten here");
            }
        };

        AWSLogs client = factory.createClient();
        assertNotNull("actually created a client",  client);
        assertSame("client created via build",      client,     clientFromBuilder);
        assertSame("region set on builder",         region,     clientBuilderRegion);

        logger.assertInternalDebugLog("creating client via SDK builder",
                                      "setting region.*" + region);
        logger.assertInternalErrorLog();
    }


    @Test
    public void testClientConstructor() throws Exception
    {
        // we're using an actual client, not a mock
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, null, null, logger);

        AWSLogs client = factory.createClient();
        assertNotNull("actually created a client",  client);

        // I have this envar defined by default; need to test both ways
        if (StringUtil.isBlank(System.getenv("AWS_REGION")))
        {
            assertEndpointRegion(client, "us-east-1");  // this is the default region for 1.11.0
            logger.assertInternalDebugLog("creating client via constructor");
            logger.assertInternalErrorLog();
        }
        else
        {
            assertEndpointRegion(client, System.getenv("AWS_REGION"));
            logger.assertInternalDebugLog("creating client via constructor",
                                          "setting region.*" + System.getenv("AWS_REGION"));
            logger.assertInternalErrorLog();
        }

        // this shouldn't be necessary, but also shouldn't hurt
        client.shutdown();
    }


    @Test
    public void testClientConstructorWithExplicitRegion() throws Exception
    {
        // to be valid for the test, the region must have existed when SDK 1.11.0 came out
        final String region = "us-west-1";

        // we're using an actual client, not a mock
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, region, null, logger);

        AWSLogs client = factory.createClient();
        assertNotNull("actually created a client",  client);

        assertEndpointRegion(client, region);
        logger.assertInternalDebugLog("creating client via constructor",
                                      "setting region.*" + region);
        logger.assertInternalErrorLog();

        // this shouldn't be necessary, but also shouldn't hurt
        client.shutdown();
    }


    @Test
    public void testClientConstructorWithBogusRegion() throws Exception
    {
        // this region didn't exist when SDK 1.11.0 came out
        final String region = "eu-west-3";

        // we're using an actual client, not a mock
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, region, null, logger);

        AWSLogs client = factory.createClient();
        assertNotNull("actually created a client",  client);

        // again, we need to test both ways
        if (StringUtil.isBlank(System.getenv("AWS_REGION")))
        {
            assertEndpointRegion(client, "us-east-1");  // should revert to default
            logger.assertInternalDebugLog("creating client via constructor");
            logger.assertInternalErrorLog();
        }
        else
        {
            assertEndpointRegion(client, System.getenv("AWS_REGION"));
            logger.assertInternalDebugLog("creating client via constructor",
                                          "setting region.*" + System.getenv("AWS_REGION"));
            logger.assertInternalErrorLog("unsupported/invalid region.*" + region);
        }

        // this shouldn't be necessary, but also shouldn't hurt
        client.shutdown();
    }


    @Test
    public void testClientConstructorWithExplicitEndpoint() throws Exception
    {
        // this region didn't exist when SDK 1.11.0 came out
        final String region = "eu-west-3";
        final String endpoint = "logs." + region + ".amazonaws.com";

        // we're using an actual client, not a mock
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, null, endpoint, logger);

        AWSLogs client = factory.createClient();
        assertNotNull("actually created a client",  client);

        assertEndpointRegion(client, region);
        logger.assertInternalDebugLog("creating client via constructor",
                                      "setting endpoint.*" + endpoint);
        logger.assertInternalErrorLog();

        // this shouldn't be necessary, but also shouldn't hurt
        client.shutdown();
    }
}
