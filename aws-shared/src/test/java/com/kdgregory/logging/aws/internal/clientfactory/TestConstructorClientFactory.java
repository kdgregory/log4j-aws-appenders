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

import java.net.URI;

import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.ClassUtil;
import net.sf.kdgcommons.lang.StringUtil;
import static net.sf.kdgcommons.test.StringAsserts.*;

import com.amazonaws.services.logs.AWSLogs;
import com.amazonaws.services.logs.AWSLogsClient;

import com.kdgregory.logging.common.factories.ClientFactoryException;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;


/**
 *  Tests the code that creates AWS clients using direct construction. Unlike
 *  the other client-factory tests, this one actually builds clients.
 */
public class TestConstructorClientFactory
{
    private TestableInternalLogger logger = new TestableInternalLogger();

//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

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
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testConstructor() throws Exception
    {
        // we're using an actual client, not a mock
        ConstructorClientFactory<AWSLogs> factory = new ConstructorClientFactory<AWSLogs>(AWSLogs.class, AWSLogsClient.class.getName(), null, null, logger);

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
    }


    @Test
    public void testConstructorWithExplicitRegion() throws Exception
    {
        // to be valid for the test, the region must have existed when SDK 1.11.0 came out
        final String region = "us-west-1";

        // we're using an actual client, not a mock
        ConstructorClientFactory<AWSLogs> factory = new ConstructorClientFactory<AWSLogs>(AWSLogs.class, AWSLogsClient.class.getName(), region, null, logger);

        AWSLogs client = factory.createClient();
        assertNotNull("actually created a client",  client);

        assertEndpointRegion(client, region);
        logger.assertInternalDebugLog("creating client via constructor",
                                      "setting region.*" + region);
        logger.assertInternalErrorLog();
    }


// will be removed
//    @Test
//    public void testConstructorWithBogusRegion() throws Exception
//    {
//        // this region didn't exist when SDK 1.11.0 came out
//        final String region = "eu-west-3";
//
//        // we're using an actual client, not a mock
//        ConstructorClientFactory<AWSLogs> factory = new ConstructorClientFactory<AWSLogs>(AWSLogs.class, AWSLogsClient.class.getName(), region, null, logger);
//
//        try
//        {
//            factory.createClient();
//            fail("did not throw when constructing client");
//        }
//        catch (ClientFactoryException ex)
//        {
//            assertRegex("failed to set region.*" + region, ex.getMessage());
//        }
//    }


    @Test
    public void testConstructorWithExplicitEndpoint() throws Exception
    {
        // this region didn't exist when SDK 1.11.0 came out
        final String region = "eu-west-3";
        final String endpoint = "logs." + region + ".amazonaws.com";

        // we're using an actual client, not a mock
        ConstructorClientFactory<AWSLogs> factory = new ConstructorClientFactory<AWSLogs>(AWSLogs.class, AWSLogsClient.class.getName(), null, endpoint, logger);

        AWSLogs client = factory.createClient();
        assertNotNull("actually created a client",  client);

        assertEndpointRegion(client, region);
        logger.assertInternalDebugLog("creating client via constructor",
                                      "setting endpoint.*" + endpoint);
        logger.assertInternalErrorLog();
    }
}
