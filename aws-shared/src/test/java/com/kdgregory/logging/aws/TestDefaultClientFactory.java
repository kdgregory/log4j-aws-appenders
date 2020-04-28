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
import static net.sf.kdgcommons.test.StringAsserts.*;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.model.InvalidOperationException;

import com.kdgregory.logging.aws.common.DefaultClientFactory;
import com.kdgregory.logging.common.factories.ClientFactoryException;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchClient;


/**
 *  Verifies high-level operation of the client factory. This is a whitebox
 *  test: it overrides factory methods to ensure that they're called. Other
 *  than the constructor test (which is unavoidable), it does not invoke
 *  any AWS-specific methods.
 */
public class TestDefaultClientFactory
{
    private TestableInternalLogger logger = new TestableInternalLogger();

    // these variables track the order of calls to the tree creation mechanisms
    private AtomicInteger invocationCounter = new AtomicInteger(0);
    private AtomicInteger factoryCalledAt = new AtomicInteger(0);
    private AtomicInteger builderCalledAt = new AtomicInteger(0);
    private AtomicInteger constructorCalledAt = new AtomicInteger(0);

    // set by factory method tests
    private static AWSLogs clientFromFactory;
    private static String factoryParamAssumedRole;
    private static String factoryParamEndpoint;
    private static String factoryParamRegion;

    // set by MockClientBuilder (which we never see created, so can't examine directly)
    private static AWSLogs clientFromBuilder;
    private static String builderParamRegion;
    private static Object clientBuilderCredentialsProvider;

//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

    public static AWSLogs baseFactoryMethod()
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


    // a simulation of AWSLogsClientBuilder
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

        public void setCredentials(AWSCredentialsProvider credentialsProvider)
        {
            clientBuilderCredentialsProvider = credentialsProvider;
        }

        public void setRegion(String region)
        {
            builderParamRegion = region;
        }
    }


    // a variant for testing invalid region
    public static class MockClientBuilderForInvalidRegionTest
    extends MockClientBuilder
    {
        public static MockClientBuilderForInvalidRegionTest standard()
        {
            return new MockClientBuilderForInvalidRegionTest();
        }

        @Override
        public void setRegion(String region)
        {
            throw new IllegalArgumentException("invalid region");
        }
    }


    // this exists to provide objects tha can be compared based on identity
    public static class MockCredentialsProvider
    implements AWSCredentialsProvider
    {
        @Override
        public AWSCredentials getCredentials()
        {
            return null;
        }

        @Override
        public void refresh()
        {
            // nothing happening here
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

        factoryParamAssumedRole = null;
        factoryParamEndpoint = null;
        factoryParamRegion = null;
        builderParamRegion = null;
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testOrderOfOperations() throws Exception
    {
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, null, null, null, logger)
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
    public void testFactoryMethod() throws Exception
    {
        String factoryMethodName = getClass().getName() + ".baseFactoryMethod";

        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, factoryMethodName, null, null, null, logger)
        {
            @Override
            public AWSLogs tryBuilder()
            {
                throw new IllegalStateException("should not have called builder");
            }

            @Override
            protected AWSLogs tryConstructor()
            {
                throw new IllegalStateException("should not have called constructor");
            }
        };

        AWSLogs client = factory.createClient();
        assertNotNull("created a client",           client);
        assertSame("client created via factory",    client, clientFromFactory);

        logger.assertInternalDebugLog("creating client via factory.*" + factoryMethodName);
        logger.assertInternalErrorLog();
    }


    @Test
    public void testFactoryMethodWithConfig() throws Exception
    {
        String factoryMethodName = getClass().getName() + ".parameterizedFactoryMethod";
        String assumedRole = "arn:aws:iam::123456789012:role/AssumableRole";
        final String region = "eu-west-3";
        final String endpoint = "logs." + region + ".amazonaws.com";

        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, factoryMethodName, assumedRole, region, endpoint, logger)
        {
            @Override
            public AWSLogs tryBuilder()
            {
                throw new IllegalStateException("should not have called builder");
            }

            @Override
            protected AWSLogs tryConstructor()
            {
                throw new IllegalStateException("should not have called constructor");
            }
        };

        AWSLogs client = factory.createClient();
        assertNotNull("created a client",  client);
        assertSame("client created via factory",    client,         clientFromFactory);
        assertEquals("role provided",               assumedRole,    factoryParamAssumedRole);
        assertEquals("region provided",             region,         factoryParamRegion);
        assertEquals("endpoint provided",           endpoint,       factoryParamEndpoint);

        logger.assertInternalDebugLog("creating client via factory.*" + factoryMethodName);
        logger.assertInternalErrorLog();
    }


    @Test
    public void testFactoryMethodBogusName() throws Exception
    {
        String factoryMethodName = "completelybogus";
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, factoryMethodName, null, null, null, logger)
        {
            @Override
            public AWSLogs tryBuilder()
            {
                fail("should not have attempted to use builder");
                return null;
            }

            @Override
            protected AWSLogs tryConstructor()
            {
                fail("should not have attempted to use constructor");
                return null;
            }
        };

        try
        {
            factory.createClient();
            fail("should have thrown");
        }
        catch (ClientFactoryException ex)
        {
            assertEquals("exception message", "invalid factory method name: " + factoryMethodName, ex.getMessage());
        }

        logger.assertInternalDebugLog("creating client via factory.*" + factoryMethodName);
    }


    @Test
    public void testFactoryMethodNoSuchClass() throws Exception
    {
        String factoryMethodName = "com.example.bogus";
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, factoryMethodName, null, null, null, logger)
        {
            @Override
            public AWSLogs tryBuilder()
            {
                fail("should not have attempted to use builder");
                return null;
            }

            @Override
            protected AWSLogs tryConstructor()
            {
                fail("should not have attempted to use constructor");
                return null;
            }
        };

        try
        {
            factory.createClient();
            fail("should have thrown");
        }
        catch (ClientFactoryException ex)
        {
            assertEquals("exception message",   "failed to invoke factory method: " + factoryMethodName,    ex.getMessage());
            assertSame("wrapped exception",     ClassNotFoundException.class,                               ex.getCause().getClass());
        }

        logger.assertInternalDebugLog("creating client via factory.*" + factoryMethodName);
    }


    @Test
    public void testFactoryMethodNoSuchMethod() throws Exception
    {
        String factoryMethodName = getClass().getName() + ".bogus";
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, factoryMethodName, null, null, null, logger)
        {
            @Override
            public AWSLogs tryBuilder()
            {
                fail("should not have attempted to use builder");
                return null;
            }

            @Override
            protected AWSLogs tryConstructor()
            {
                fail("should not have attempted to use constructor");
                return null;
            }
        };

        try
        {
            factory.createClient();
            fail("should have thrown");
        }
        catch (ClientFactoryException ex)
        {
            assertEquals("exception message", "factory method does not exist: " + factoryMethodName, ex.getMessage());
        }

        logger.assertInternalDebugLog("creating client via factory.*" + factoryMethodName);
    }


    @Test
    public void testFactoryMethodException() throws Exception
    {
        // this test is alsmost identical to the previous, but has a different exception cause

        String factoryMethodName = getClass().getName() + ".throwingFactoryMethod";

        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, factoryMethodName, null, null, null, logger)
        {
            @Override
            public AWSLogs tryBuilder()
            {
                fail("should not have attempted to use builder");
                return null;
            }

            @Override
            protected AWSLogs tryConstructor()
            {
                fail("should not have attempted to use constructor");
                return null;
            }
        };

        try
        {
            factory.createClient();
            fail("should have thrown");
        }
        catch (ClientFactoryException ex)
        {
            assertEquals("exception message",   "failed to invoke factory method: " + factoryMethodName,    ex.getMessage());
            assertEquals("underlying cause",    InvalidOperationException.class,                            ex.getCause().getClass());
        }

        logger.assertInternalDebugLog("creating client via factory.*" + factoryMethodName);
        logger.assertInternalErrorLog();
    }


    @Test
    public void testClientBuilder() throws Exception
    {
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, null, null, null, logger)
        {
            // the AWS builder class isn't in our dependencies, so we'll replace it with our own
            {
                factoryClasses.put("com.amazonaws.services.logs.AWSLogs", MockClientBuilder.class.getName());
            }

            @Override
            protected AWSCredentialsProvider createDefaultCredentialsProvider()
            {
                return new MockCredentialsProvider();
            }

            @Override
            protected AWSCredentialsProvider createAssumedRoleCredentialsProvider()
            {
                throw new IllegalStateException("should not have called assumed-role credentials provider");
            }

            @Override
            protected AWSLogs tryConstructor()
            {
                throw new IllegalStateException("should not have called constructor");
            }
        };

        AWSLogs client = factory.createClient();
        assertNotNull("actually created a client",  client);
        assertSame("client created via build",      client, clientFromBuilder);

        logger.assertInternalDebugLog(
                "creating client via SDK builder",
                "using default credentials provider");
        logger.assertInternalErrorLog();
    }


    @Test
    public void testBuilderWithRegion() throws Exception
    {
        final String region = "us-west-1";

        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, null, region, null, logger)
        {
            // the AWS builder class isn't in our dependencies, so we'll replace it with our own
            {
                factoryClasses.put("com.amazonaws.services.logs.AWSLogs", MockClientBuilder.class.getName());
            }

            @Override
            protected AWSCredentialsProvider createDefaultCredentialsProvider()
            {
                return new MockCredentialsProvider();
            }

            @Override
            protected AWSCredentialsProvider createAssumedRoleCredentialsProvider()
            {
                throw new IllegalStateException("should not have called assumed-role credentials provider");
            }

            @Override
            protected AWSLogs tryConstructor()
            {
                throw new IllegalStateException("should not have called constructor");
            }
        };

        AWSLogs client = factory.createClient();
        assertNotNull("actually created a client",  client);
        assertSame("client created by builder",     client,     clientFromBuilder);
        assertSame("region set on builder",         region,     builderParamRegion);

        logger.assertInternalDebugLog(
                "creating client via SDK builder",
                "setting region.*" + region,
                "using default credentials provider");
        logger.assertInternalErrorLog();
    }


    @Test
    public void testBuilderWithInvalidRegion() throws Exception
    {
        final String region = "bogus";

        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, null, region, null, logger)
        {
            // the AWS builder class isn't in our dependencies, so we'll replace it with our own
            {
                factoryClasses.put("com.amazonaws.services.logs.AWSLogs", MockClientBuilderForInvalidRegionTest.class.getName());
            }

            @Override
            protected AWSCredentialsProvider createDefaultCredentialsProvider()
            {
                return new MockCredentialsProvider();
            }

            @Override
            protected AWSCredentialsProvider createAssumedRoleCredentialsProvider()
            {
                throw new IllegalStateException("should not have called assumed-role credentials provider");
            }

            @Override
            protected AWSLogs tryConstructor()
            {
                throw new IllegalStateException("should not have called constructor");
            }
        };

        try
        {
            factory.createClient();
            fail("was able to create client");
        }
        catch (ClientFactoryException ex)
        {
            assertRegex("exception message", "failed.*" + region + ".*", ex.getMessage());
        }

        logger.assertInternalDebugLog(
                "creating client via SDK builder",
                "setting region.*" + region);
        logger.assertInternalErrorLog();
    }


    @Test
    public void testBuilderWithAssumedRole() throws Exception
    {
        final String assumedRole = "Example";
        final AWSCredentialsProvider expectedARProvider = new MockCredentialsProvider();

        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, assumedRole, null, null, logger)
        {
            // we'll replace the AWS builder class with our own
            {
                factoryClasses.put("com.amazonaws.services.logs.AWSLogs", MockClientBuilder.class.getName());
            }

            @Override
            protected AWSCredentialsProvider createDefaultCredentialsProvider()
            {
                throw new IllegalStateException("should not have called default credentials provider");
            }

            @Override
            protected AWSCredentialsProvider createAssumedRoleCredentialsProvider()
            {
                return expectedARProvider;
            }

            @Override
            protected AWSLogs tryConstructor()
            {
                throw new IllegalStateException("should not have called constructor");
            }
        };

        AWSLogs client = factory.createClient();
        assertNotNull("actually created a client",  client);
        assertSame("client created via build",      client,                 clientFromBuilder);
        assertSame("credentials provider set",      expectedARProvider,     clientBuilderCredentialsProvider);

        logger.assertInternalDebugLog("creating client via SDK builder",
                                      "assuming role.*" + assumedRole);
        logger.assertInternalErrorLog();
    }


    @Test
    public void testBuilderExceptionWhileAssumingRole() throws Exception
    {
        final String assumedRole = "Example";

        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, assumedRole, null, null, logger)
        {
            // we'll replace the AWS builder class with our own
            {
                factoryClasses.put("com.amazonaws.services.logs.AWSLogs", MockClientBuilder.class.getName());
            }

            @Override
            protected AWSCredentialsProvider createDefaultCredentialsProvider()
            {
                throw new IllegalStateException("should not have called default credentials provider");
            }

            @Override
            protected AWSCredentialsProvider createAssumedRoleCredentialsProvider()
            {
                throw new RuntimeException("denied!");
            }

            @Override
            protected AWSLogs tryConstructor()
            {
                throw new IllegalStateException("should not have called constructor");
            }
        };

        try
        {
            factory.createClient();
            fail("able to create client when credentials provider threw");
        }
        catch (ClientFactoryException ex)
        {
            assertEquals("exception message", "failed to invoke builder",   ex.getMessage());
            assertEquals("wrapped exception", "denied!",                    ex.getCause().getMessage());
        }

        logger.assertInternalDebugLog("creating client via SDK builder",
                                      "assuming role.*" + assumedRole);
        logger.assertInternalErrorLog();
    }


    @Test
    public void testConstructor() throws Exception
    {
        // we're using an actual client, not a mock
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, null, null, null, logger);

        AWSLogs client = factory.createClient();
        assertNotNull("actually created a client",  client);

        // I have this envar defined by default; so need to test both ways
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
    public void testConstructorWithExplicitRegion() throws Exception
    {
        // to be valid for the test, the region must have existed when SDK 1.11.0 came out
        final String region = "us-west-1";

        // we're using an actual client, not a mock
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, null, region, null, logger);

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
    public void testConstructorWithBogusRegion() throws Exception
    {
        // this region didn't exist when SDK 1.11.0 came out
        final String region = "eu-west-3";

        // we're using an actual client, not a mock
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, null, region, null, logger);

        try
        {
            factory.createClient();
            fail("did not throw when constructing client");
        }
        catch (ClientFactoryException ex)
        {
            assertRegex(".*invalid region.*" + region, ex.getMessage());
        }
    }


    @Test
    public void testConstructorWithExplicitEndpoint() throws Exception
    {
        // this region didn't exist when SDK 1.11.0 came out
        final String region = "eu-west-3";
        final String endpoint = "logs." + region + ".amazonaws.com";

        // we're using an actual client, not a mock
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, null, null, endpoint, logger);

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
