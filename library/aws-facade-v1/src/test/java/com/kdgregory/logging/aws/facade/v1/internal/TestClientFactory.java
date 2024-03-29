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

package com.kdgregory.logging.aws.facade.v1.internal;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import static net.sf.kdgcommons.test.StringAsserts.*;

import com.amazonaws.ClientConfigurationFactory;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.internal.AbstractWriterConfig;
import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;


public class TestClientFactory
{
//----------------------------------------------------------------------------
//  A configuration that's not tied to any SDK client
//----------------------------------------------------------------------------

    private static class TestWriterConfig
    extends AbstractWriterConfig<TestWriterConfig>
    {
        public TestWriterConfig()
        {
            super(60000);   // an arbitrary initialization timeout
        }
    }

//----------------------------------------------------------------------------
//  Static factory methods -- note that they don't have to return AWS clients!
//----------------------------------------------------------------------------

    private static boolean factoryMethodCalled;
    private static RuntimeException factoryException;


    public static Boolean simpleFactory()
    {
        factoryMethodCalled = true;
        return Boolean.TRUE;
    }


    public static Map<String,String> parameterizedFactory(String role, String region, String endpoint)
    {
        factoryMethodCalled = true;
        Map<String,String> result = new HashMap<>();
        result.put("role", role);
        result.put("region", region);
        result.put("endpoint", endpoint);
        return result;
    }


    public static Boolean throwingFactory()
    {
        factoryMethodCalled = true;
        factoryException = new RuntimeException("factory threw");
        throw factoryException;
    }

//----------------------------------------------------------------------------
//  Our own client builder, which doesn't actually build a client
//----------------------------------------------------------------------------

    private static class TestableAwsClientBuilder
    extends AwsClientBuilder<TestableAwsClientBuilder,Object>
    {
        public TestableAwsClientBuilder()
        {
            super(new ClientConfigurationFactory());
        }

        @Override
        public Object build()
        {
            return Boolean.TRUE;
        }
    }

//----------------------------------------------------------------------------
//  A testable implemntation of ClientFactory
//----------------------------------------------------------------------------

    private static class TestableClientFactory
    extends ClientFactory<Object>
    {
        public boolean tryInstantiateFactoryCalled;
        public boolean createClientBuilderCalled;

        public TestableAwsClientBuilder clientBuilder;

        public TestableClientFactory(TestWriterConfig config)
        {
            super(Object.class, config);
            clientBuilder = new TestableAwsClientBuilder();
        }

        @Override
        protected Object tryInstantiateFromFactory()
        {
            tryInstantiateFactoryCalled = true;
            return super.tryInstantiateFromFactory();
        }

        @Override
        protected AwsClientBuilder<?,?> createClientBuilder()
        {
            createClientBuilderCalled = true;
            return clientBuilder;
        }

        @Override
        protected void setAssumedRoleCredentialsProvider(AwsClientBuilder<?,?> builder, String roleToAssume)
        {
            fail("this method should not be called unless role configured");
        }
    }

//----------------------------------------------------------------------------
//  JUnit scaffolding
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        factoryMethodCalled = false;
        factoryException = null;
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testInternalsCreateClientBuilder() throws Exception
    {
        ClientFactory<Object> cwFactory = new ClientFactory<>(Object.class, new CloudWatchWriterConfig());
        Object cwClientBuilder = cwFactory.createClientBuilder();
        assertTrue("CloudWatch client builder (was: " + cwClientBuilder + ")",
                   cwClientBuilder instanceof AWSLogsClientBuilder);

        ClientFactory<Object> kinesisFactory = new ClientFactory<>(Object.class, new KinesisWriterConfig());
        Object kinesisClientBuilder = kinesisFactory.createClientBuilder();
        assertTrue("Kinesis client builder (was: " + kinesisClientBuilder + ")",
                   kinesisClientBuilder instanceof AmazonKinesisClientBuilder);

        ClientFactory<Object> snsFactory = new ClientFactory<>(Object.class, new SNSWriterConfig());
        Object snsClientBuilder = snsFactory.createClientBuilder();
        assertTrue("CloudWatch client builder (was: " + snsClientBuilder + ")",
                   snsClientBuilder instanceof AmazonSNSClientBuilder);

        try
        {
            ClientFactory<Object> bogusFactory = new ClientFactory<>(Object.class, new TestWriterConfig());
            bogusFactory.createClientBuilder();
            fail("should have thrown");
        }
        catch (Exception ex)
        {
            assertRegex("exception message describes problem (was: " + ex.getMessage() + ")",
                        "unsupported configuration type.*TestWriterConfig",
                        ex.getMessage());
        }
    }


    @Test
    public void testCreateViaSimpleFactoryMethod() throws Exception
    {
        TestWriterConfig config = new TestWriterConfig()
                                  .setClientFactoryMethod(getClass().getName() + ".simpleFactory");

        TestableClientFactory factory = new TestableClientFactory(config);
        Object value = factory.create();

        assertTrue("tryInstantiateFactory called",                          factory.tryInstantiateFactoryCalled);
        assertTrue("factory method check variable",                         factoryMethodCalled);
        assertFalse("client builder not created",                           factory.createClientBuilderCalled);

        assertEquals("returned expected value",         Boolean.TRUE,       value);
    }


    @Test
    public void testCreateViaParameterizedFactoryMethod() throws Exception
    {
        TestWriterConfig config = new TestWriterConfig()
                                  .setClientFactoryMethod(getClass().getName() + ".parameterizedFactory")
                                  .setAssumedRole("roleArn")
                                  .setClientRegion("na-something-1")
                                  .setClientEndpoint("api.mycompany.com");

        TestableClientFactory factory = new TestableClientFactory(config);
        Map<String,String> value = (Map<String,String>)factory.create(); // casting is a hack to keep definitions simple

        assertTrue("tryInstantiateFactory called",                          factory.tryInstantiateFactoryCalled);
        assertTrue("factory method check variable",                         factoryMethodCalled);
        assertFalse("client builder not created",                           factory.createClientBuilderCalled);

        assertEquals("returned passed role",        "roleArn",              value.get("role"));
        assertEquals("returned passed region",      "na-something-1",       value.get("region"));
        assertEquals("returned passed endpoint",    "api.mycompany.com",    value.get("endpoint"));
    }


    @Test
    public void testMissingFactoryMethod() throws Exception
    {
        TestWriterConfig config = new TestWriterConfig()
                                  .setClientFactoryMethod(getClass().getName() + ".bogusFactory");

        try
        {
            TestableClientFactory factory = new TestableClientFactory(config);
            factory.create();
            fail("should have thrown");
        }
        catch (Exception ex)
        {
            assertRegex("exception message describes problem (was: " + ex.getMessage() + ")",
                        "invalid factory method.*bogusFactory",
                        ex.getMessage());
        }
    }


    @Test
    public void testExceptionInFactoryMethod() throws Exception
    {
        TestWriterConfig config = new TestWriterConfig()
                                  .setClientFactoryMethod(getClass().getName() + ".throwingFactory");

        try
        {
            TestableClientFactory factory = new TestableClientFactory(config);
            factory.create();
            fail("should have thrown");
        }
        catch (Exception ex)
        {
            assertRegex("exception message describes problem (was: " + ex.getMessage() + ")",
                        "exception invoking factory.*throwingFactory",
                        ex.getMessage());

            assertSame("propagated exception cause", factoryException, ex.getCause());
        }
    }


    @Test
    public void testCreateViaBuilderNoConfig() throws Exception
    {
        TestWriterConfig config = new TestWriterConfig();

        TestableClientFactory factory = new TestableClientFactory(config);
        Object value = factory.create();

        assertTrue("tryInstantiateFactory called",                          factory.tryInstantiateFactoryCalled);
        assertFalse("factory method check variable",                        factoryMethodCalled);
        assertTrue("client builder created",                                factory.createClientBuilderCalled);

        assertEquals("returned expected value",             Boolean.TRUE,   value);
    }


    @Test
    public void testCreateViaBuilderConfigureRegion() throws Exception
    {
        TestWriterConfig config = new TestWriterConfig()
                                  .setClientRegion("ca-central-1"); // have to use a valid, non-default region

        TestableClientFactory factory = new TestableClientFactory(config);
        Object value = factory.create();

        assertTrue("tryInstantiateFactory called",                              factory.tryInstantiateFactoryCalled);
        assertFalse("factory method check variable",                            factoryMethodCalled);
        assertTrue("client builder created",                                    factory.createClientBuilderCalled);
        assertEquals("builder configured with region",      "ca-central-1",     factory.clientBuilder.getRegion());

        assertEquals("returned expected value",             Boolean.TRUE,       value);
    }


    @Test
    public void testCreateViaBuilderConfigureEndpointWithRegion() throws Exception
    {
        TestWriterConfig config = new TestWriterConfig()
                                  .setClientRegion("ca-central-1")
                                  .setClientEndpoint("www.example.com");

        TestableClientFactory factory = new TestableClientFactory(config);
        Object value = factory.create();

        assertTrue("tryInstantiateFactory called",                                  factory.tryInstantiateFactoryCalled);
        assertFalse("factory method check variable",                                factoryMethodCalled);
        assertTrue("client builder created",                                        factory.createClientBuilderCalled);
        assertEquals("builder configured with endpoint",        "www.example.com",  factory.clientBuilder.getEndpoint().getServiceEndpoint());
        assertEquals("builder configured with signing region",  "ca-central-1",     factory.clientBuilder.getEndpoint().getSigningRegion());
        assertEquals("builder not configured with region",      null,               factory.clientBuilder.getRegion());

        assertEquals("returned expected value",                 Boolean.TRUE,       value);
    }


    @Test
    public void testCreateViaBuilderConfigureEndpointWithoutRegion() throws Exception
    {
        // from testing, you can omit signing region if using an AWS endpoint; using a
        // custom endpoint as shown here, however, isn't likely to work

        TestWriterConfig config = new TestWriterConfig()
                                  .setClientEndpoint("www.example.com");

        TestableClientFactory factory = new TestableClientFactory(config);
        Object value = factory.create();

        assertTrue("tryInstantiateFactory called",                                      factory.tryInstantiateFactoryCalled);
        assertFalse("factory method check variable",                                    factoryMethodCalled);
        assertTrue("client builder created",                                            factory.createClientBuilderCalled);
        assertEquals("builder configured with endpoint",            "www.example.com",  factory.clientBuilder.getEndpoint().getServiceEndpoint());
        assertEquals("builder not configured with signing region",  null,               factory.clientBuilder.getEndpoint().getSigningRegion());
        assertEquals("builder not configured with region",          null,               factory.clientBuilder.getRegion());

        assertEquals("create() returned expected value",            Boolean.TRUE,       value);
    }


    @Test
    public void testCreateViaBuilderConfigureAssumedRole() throws Exception
    {
        // it doesn't matter whether we use a name or ARN, because we're mocking out the code that uses it
        String testRoleArn = "arn:aws:iam::123456789012:role/AssumableRole";

        final AtomicBoolean setterWasCalled = new AtomicBoolean(false);

        TestWriterConfig config = new TestWriterConfig().setAssumedRole(testRoleArn);
        TestableClientFactory factory = new TestableClientFactory(config)
        {
            @Override
            protected void setAssumedRoleCredentialsProvider(AwsClientBuilder<?,?> builder, String roleToAssume)
            {
                setterWasCalled.set(true);
                assertEquals("roleToAssume passed to provider provider", testRoleArn, roleToAssume);
            }
        };
        Object value = factory.create();

        assertTrue("tryInstantiateFactory called",                                      factory.tryInstantiateFactoryCalled);
        assertFalse("factory method check variable",                                    factoryMethodCalled);
        assertTrue("client builder created",                                            factory.createClientBuilderCalled);
        assertTrue("assumed role setter was called",                                    setterWasCalled.get());
        assertEquals("create() returned expected value",            Boolean.TRUE,       value);
    }
}
