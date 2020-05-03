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
import static net.sf.kdgcommons.test.StringAsserts.*;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.logs.AWSLogs;

import com.kdgregory.logging.common.factories.ClientFactoryException;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;


/**
 *  Tests high-level operation of using SDK builder classes to create a client. We
 *  can't test low-level operation because we're building against an SDK version that
 *  doesn't have builders.
 */
public class TestBuilderClientFactory
{
    private TestableInternalLogger logger = new TestableInternalLogger();

    // set by MockClientBuilder (which we never see created, so can't examine directly)
    private static AWSLogs createdClient;
    private static String actualRegion;
    private static Object actualCredentialsProvider;

    // this is used by any test that needs a credentials provider (only one is ever
    // returned); it won't be invoked
    private SelfMock<AWSCredentialsProvider> credentialsProviderMock = new SelfMock<AWSCredentialsProvider>(AWSCredentialsProvider.class) { /* empty */ };
    private AWSCredentialsProvider testCredentialsProvider = credentialsProviderMock.getInstance();

//----------------------------------------------------------------------------
//  Support code
//----------------------------------------------------------------------------

    /**
     *  A mock implementation of the AWS builder interface. Since we don't use
     *  a version of the SDK that actually has client builders, we can't use a
     *  "real" mock; instead this is basic class that has some matching methods.
     *  That's OK, because everything happens via reflection.
     */
    public static class MockClientBuilder
    {
        public static MockClientBuilder standard()
        {
            return new MockClientBuilder();
        }

        public AWSLogs build()
        {
            SelfMock<AWSLogs> mock = new SelfMock<AWSLogs>(AWSLogs.class) { /* empty */ };
            createdClient = mock.getInstance();
            return createdClient;
        }

        public void setCredentials(AWSCredentialsProvider value)
        {
            actualCredentialsProvider = value;
        }

        public void setRegion(String region)
        {
            actualRegion = region;
        }
    }


    /**
     *  A variant of the client builder that rejects any region it's given.
     */
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

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        // these may have been set by previous test
        createdClient = null;
        actualRegion = null;
        actualCredentialsProvider = null;
    }
//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testBasicOperation() throws Exception
    {
        BuilderClientFactory<AWSLogs> factory = new BuilderClientFactory<AWSLogs>(AWSLogs.class, MockClientBuilder.class.getName(), null, null, logger)
        {
            @Override
            protected AWSCredentialsProvider createDefaultCredentialsProvider()
            {
                return testCredentialsProvider;
            }

            @Override
            protected AWSCredentialsProvider createAssumedRoleCredentialsProvider()
            {
                throw new IllegalStateException("should not have asked for assumed-role credentials provider");
            }
        };

        AWSLogs client = factory.createClient();
        assertSame("created a client using mock",   client, createdClient);
        assertNull("did not set region",            actualRegion);
        assertSame("set credentials provider",      actualCredentialsProvider, testCredentialsProvider);

        logger.assertInternalDebugLog(
                "creating client via SDK builder",
                "using default credentials provider");
        logger.assertInternalErrorLog();
    }


    @Test
    public void testBuilderWithRegion() throws Exception
    {
        final String region = "us-west-1";

        BuilderClientFactory<AWSLogs> factory = new BuilderClientFactory<AWSLogs>(AWSLogs.class, MockClientBuilder.class.getName(), null, region, logger)
        {
            @Override
            protected AWSCredentialsProvider createDefaultCredentialsProvider()
            {
                return testCredentialsProvider;
            }

            @Override
            protected AWSCredentialsProvider createAssumedRoleCredentialsProvider()
            {
                throw new IllegalStateException("should not have asked for assumed-role credentials provider");
            }
        };

        AWSLogs client = factory.createClient();
        assertSame("client created by builder",     client,     createdClient);
        assertSame("region set on builder",         region,     actualRegion);

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

        BuilderClientFactory<AWSLogs> factory = new BuilderClientFactory<AWSLogs>(AWSLogs.class, MockClientBuilderForInvalidRegionTest.class.getName(), null, region, logger)
        {
            @Override
            protected AWSCredentialsProvider createDefaultCredentialsProvider()
            {
                return testCredentialsProvider;
            }

            @Override
            protected AWSCredentialsProvider createAssumedRoleCredentialsProvider()
            {
                throw new IllegalStateException("should not have asked for assumed-role credentials provider");
            }
        };

        try
        {
            factory.createClient();
            fail("was able to create client");
        }
        catch (ClientFactoryException ex)
        {
            assertRegex("exception message", "failed to set region: " + region, ex.getMessage());
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

        BuilderClientFactory<AWSLogs> factory = new BuilderClientFactory<AWSLogs>(AWSLogs.class, MockClientBuilderForInvalidRegionTest.class.getName(), assumedRole, null, logger)
        {
            @Override
            protected AWSCredentialsProvider createDefaultCredentialsProvider()
            {
                throw new IllegalStateException("should not have asked for default credentials provider");
            }

            @Override
            protected AWSCredentialsProvider createAssumedRoleCredentialsProvider()
            {
                return testCredentialsProvider;
            }
        };

        AWSLogs client = factory.createClient();
        assertSame("client created by builder",     client,                     createdClient);
        assertSame("set credentials provider",      actualCredentialsProvider,  testCredentialsProvider);

        logger.assertInternalDebugLog("creating client via SDK builder",
                                      "assuming role.*" + assumedRole);
        logger.assertInternalErrorLog();
    }


    @Test
    public void testBuilderExceptionWhileAssumingRole() throws Exception
    {
        final String assumedRole = "Example";

        BuilderClientFactory<AWSLogs> factory = new BuilderClientFactory<AWSLogs>(AWSLogs.class, MockClientBuilderForInvalidRegionTest.class.getName(), assumedRole, null, logger)
        {
            @Override
            protected AWSCredentialsProvider createDefaultCredentialsProvider()
            {
                throw new IllegalStateException("should not have asked for default credentials provider");
            }

            @Override
            protected AWSCredentialsProvider createAssumedRoleCredentialsProvider()
            {
                throw new RuntimeException("denied!");
            }
        };

        try
        {
            factory.createClient();
            fail("able to create client when credentials provider threw");
        }
        catch (ClientFactoryException ex)
        {
            assertEquals("exception message", "failed to set credentials provider", ex.getMessage());
            assertEquals("wrapped exception", "denied!",                            ex.getCause().getMessage());
        }

        logger.assertInternalDebugLog("creating client via SDK builder",
                                      "assuming role.*" + assumedRole);
        logger.assertInternalErrorLog();
    }
}
