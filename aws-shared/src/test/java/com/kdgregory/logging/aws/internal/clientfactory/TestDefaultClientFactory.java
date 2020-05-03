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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.test.SelfMock;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClient;

import com.kdgregory.logging.aws.common.DefaultClientFactory;
import com.kdgregory.logging.aws.internal.clientfactory.BuilderClientFactory;
import com.kdgregory.logging.aws.internal.clientfactory.ConstructorClientFactory;
import com.kdgregory.logging.aws.internal.clientfactory.StaticMethodClientFactory;
import com.kdgregory.logging.common.factories.ClientFactory;
import com.kdgregory.logging.common.factories.ClientFactoryException;
import com.kdgregory.logging.common.util.InternalLogger;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;


/**
 *  Verifies high-level operation of the client factory. It depends on
 *  purpose-built client factories, which are tested by on their own.
 */
public class TestDefaultClientFactory
{
    private TestableInternalLogger logger = new TestableInternalLogger();

//----------------------------------------------------------------------------
//  These classes substitute for the internal ClientFactory implementations
//----------------------------------------------------------------------------

    /**
     *  This is the basic client factory, returning (and retaining) a mock client.
     */
    public static class MockClientFactory<T>
    implements ClientFactory<T>
    {
        private SelfMock<T> mock;

        public T client;

        public MockClientFactory(Class<T> clientType)
        {
            mock = new SelfMock<T>(clientType) { /* empty */ };
        }

        @Override
        public T createClient()
        {
            if (client != null)
                throw new IllegalStateException("this factory was already called");

            client = mock.getInstance();
            return client;
        }
    }


    /**
     *  This factory always returns null.
     */
    public static class NullReturningClientFactory<T>
    implements ClientFactory<T>
    {
        @Override
        public T createClient()
        {
            return null;
        }
    }


    /**
     *  This ClientFactory implementation records its invocation but doesn't
     *  create a client. It's used for the order-of-operations test.
     */
    public static class OrderTrackingClientFactory<T>
    implements ClientFactory<T>
    {
        private AtomicInteger callRecorder = new AtomicInteger();

        public int calledAt;

        public OrderTrackingClientFactory(AtomicInteger callRecorder)
        {
            this.callRecorder = callRecorder;
        }

        @Override
        public T createClient()
        {
            calledAt = callRecorder.incrementAndGet();
            return null;
        }
    }


    /**
     *  This ClientFactory implementation throws IllegalStateException when called:
     *  it's used to verify that we don't call factories after creating a client.
     */
    public static class IllegalStateClientFactory<T>
    implements ClientFactory<T>
    {
        private String factoryType;

        public IllegalStateClientFactory(String factoryType)
        {
            this.factoryType = factoryType;
        }

        @Override
        public T createClient()
        {
            throw new IllegalStateException("should not have called " + factoryType + " factory");
        }
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testChildFactoryInitialization() throws Exception
    {
        // note: all of these values are bogus; shouldn't affect child factory construction
        final String factoryMethodName = "bogus";
        final String assumedRole = "bogus";
        final String region = "bogus";
        final String endpoint = "bogus";

        // these values are provided by DefaultClientFactory, specified here for consistency in assertions
        final String builderClassName = "com.amazonaws.services.logs.AWSLogsClientBuilder";
        final Class<?> constructedClass = AWSLogsClient.class;

        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, factoryMethodName, assumedRole, region, endpoint, logger);

        StaticMethodClientFactory<?> staticMethodClientFactory  = ClassUtil.getFieldValue(factory, "staticMethodClientFactory", StaticMethodClientFactory.class);
        BuilderClientFactory<?> builderClientFactory            = ClassUtil.getFieldValue(factory, "builderClientFactory", BuilderClientFactory.class);
        ConstructorClientFactory<?> constructorClientFactory    = ClassUtil.getFieldValue(factory, "constructorClientFactory", ConstructorClientFactory.class);

        assertNotNull("static method factory created",      staticMethodClientFactory);
        assertEquals("static method factory: client type",  AWSLogs.class,      ClassUtil.getFieldValue(staticMethodClientFactory, "clientType", Class.class));
        assertEquals("static method factory: method name",  factoryMethodName,  ClassUtil.getFieldValue(staticMethodClientFactory, "fullyQualifiedMethodName", String.class));
        assertEquals("static method factory: assumed role", assumedRole,        ClassUtil.getFieldValue(staticMethodClientFactory, "assumedRole", String.class));
        assertEquals("static method factory: region",       region,             ClassUtil.getFieldValue(staticMethodClientFactory, "region", String.class));
        assertEquals("static method factory: endpoint",     endpoint,           ClassUtil.getFieldValue(staticMethodClientFactory, "endpoint", String.class));
        assertEquals("static method factory: logger",       logger,             ClassUtil.getFieldValue(staticMethodClientFactory, "logger", InternalLogger.class));

        assertNotNull("builder factory created",            builderClientFactory);
        assertEquals("builder factory: client type",        AWSLogs.class,      ClassUtil.getFieldValue(builderClientFactory, "clientType", Class.class));
        assertEquals("builder factory: builder class",      builderClassName,   ClassUtil.getFieldValue(builderClientFactory, "builderClassName", String.class));
        assertEquals("builder factory: assumed role",       assumedRole,        ClassUtil.getFieldValue(builderClientFactory, "assumedRole", String.class));
        assertEquals("builder factory: region",             region,             ClassUtil.getFieldValue(builderClientFactory, "region", String.class));
        assertEquals("builder factory: logger",             logger,             ClassUtil.getFieldValue(builderClientFactory, "logger", InternalLogger.class));

        assertNotNull("constructor factory created", constructorClientFactory);
        assertEquals("constructor factory: client type",    AWSLogs.class,      ClassUtil.getFieldValue(constructorClientFactory, "clientType", Class.class));
        assertEquals("constructor factory: client class",   constructedClass,   ClassUtil.getFieldValue(constructorClientFactory, "clientClass", Class.class));
        assertEquals("constructor factory: region",         region,             ClassUtil.getFieldValue(constructorClientFactory, "region", String.class));
        assertEquals("constructor factory: endpoint",       endpoint,           ClassUtil.getFieldValue(constructorClientFactory, "endpoint", String.class));
        assertEquals("constructor factory: logger",         logger,             ClassUtil.getFieldValue(constructorClientFactory, "logger", InternalLogger.class));
    }


    @Test
    public void testOrderOfOperations() throws Exception
    {
        final AtomicInteger callOrder = new AtomicInteger();
        final OrderTrackingClientFactory<AWSLogs> staticMethodFactory = new OrderTrackingClientFactory<>(callOrder);
        final OrderTrackingClientFactory<AWSLogs> builderFactory      = new OrderTrackingClientFactory<>(callOrder);
        final OrderTrackingClientFactory<AWSLogs> constructorFactory  = new OrderTrackingClientFactory<>(callOrder);

        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, null, null, null, logger)
        {
            {
                staticMethodClientFactory = staticMethodFactory;
                builderClientFactory      = builderFactory;
                constructorClientFactory  = constructorFactory;
            }
        };

        try
        {
            factory.createClient();
            fail("should have thrown");
        }
        catch (ClientFactoryException ex)
        {
            // we'll call this success, without checking error log
        }

        assertEquals("tried all mechanisms",    3,  callOrder.get());
        assertEquals("factory tried first",     1,  staticMethodFactory.calledAt);
        assertEquals("builder tried second",    2,  builderFactory.calledAt);
        assertEquals("constructor tried third", 3,  constructorFactory.calledAt);

        logger.assertInternalDebugLog();
        logger.assertInternalErrorLog();
    }


    @Test
    public void testFactoryMethodSucceeds() throws Exception
    {
        final MockClientFactory<AWSLogs> invokedChildFactory = new MockClientFactory<>(AWSLogs.class);

        // we don't care about any parameters -- another test will verify that they're set
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, null, null, null, logger)
        {
            {
                staticMethodClientFactory = invokedChildFactory;
                builderClientFactory      = new IllegalStateClientFactory<>("builder");
                constructorClientFactory  = new IllegalStateClientFactory<>("constructor");
            }
        };

        AWSLogs client = factory.createClient();
        assertSame("factory was invoked", client, invokedChildFactory.client);

        logger.assertInternalDebugLog();
        logger.assertInternalErrorLog();
    }


    @Test
    public void testBuilderSucceeds() throws Exception
    {
        final MockClientFactory<AWSLogs> invokedChildFactory = new MockClientFactory<>(AWSLogs.class);

        // we don't care about any parameters -- another test will verify that they're set
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, null, null, null, logger)
        {
            {
                staticMethodClientFactory = new NullReturningClientFactory<>();
                builderClientFactory      = invokedChildFactory;
                constructorClientFactory  = new IllegalStateClientFactory<>("constructor");
            }
        };

        AWSLogs client = factory.createClient();
        assertSame("factory was invoked", client, invokedChildFactory.client);

        logger.assertInternalDebugLog();
        logger.assertInternalErrorLog();
    }


    @Test
    public void testConstructorSucceeds() throws Exception
    {
        final MockClientFactory<AWSLogs> invokedChildFactory = new MockClientFactory<>(AWSLogs.class);

        // we don't care about any parameters -- another test will verify that they're set
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, null, null, null, logger)
        {
            {
                staticMethodClientFactory = new NullReturningClientFactory<>();
                builderClientFactory      = new NullReturningClientFactory<>();
                constructorClientFactory  = invokedChildFactory;
            }
        };

        AWSLogs client = factory.createClient();
        assertSame("factory was invoked", client, invokedChildFactory.client);

        logger.assertInternalDebugLog();
        logger.assertInternalErrorLog();
    }

}
