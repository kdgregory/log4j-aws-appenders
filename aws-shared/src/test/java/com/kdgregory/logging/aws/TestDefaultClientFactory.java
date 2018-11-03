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

import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.StringUtil;

import com.amazonaws.services.logs.AWSLogs;

import com.kdgregory.logging.aws.common.DefaultClientFactory;
import com.kdgregory.logging.testhelpers.TestableInternalLogger;
import com.kdgregory.logging.testhelpers.cloudwatch.MockCloudWatchClient;


/**
 *  An extremely limited test of <code>DefaultClientFactory</code>. We are unable
 *  test the SDK factory method because we're building with an older SDK, and unable
 *  to check endpoint configuration because the AWS clients don't expose that info.
 */
public class TestDefaultClientFactory
{
    private TestableInternalLogger logger = new TestableInternalLogger();

    private static AWSLogs clientFromFactory;

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


    @Test
    public void testStaticClientFactory() throws Exception
    {
        String factoryMethod = getClass().getName() + ".createMockClient";

        clientFromFactory = null;
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, factoryMethod, null, logger);
        AWSLogs client = factory.createClient();

        assertSame("client created via local factory method", clientFromFactory, client);
    }


    @Test
    public void testDefaultClientConstructor() throws Exception
    {
        clientFromFactory = null;
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, null, logger);
        factory.createClient();

        assertNull("client not created via local factory method", clientFromFactory);

        // I have this defined by default, but we want the tests to run either way
        String regionEnvar = System.getenv("AWS_REGION");
        if (StringUtil.isEmpty(regionEnvar))
        {
            logger.assertInternalDebugLog();
            logger.assertInternalErrorLog();
        }
        else
        {
            logger.assertInternalDebugLog(".*" + regionEnvar + ".*");
            logger.assertInternalErrorLog();
        }
    }


    @Test
    public void testClientConstructorWithEndpointConfig() throws Exception
    {
        String endpoint = "logs.us-west-1.amazonaws.com";

        clientFromFactory = null;
        DefaultClientFactory<AWSLogs> factory = new DefaultClientFactory<AWSLogs>(AWSLogs.class, null, endpoint, logger);
        factory.createClient();

        assertNull("client not created via local factory method",   clientFromFactory);

        logger.assertInternalDebugLog(".*" + endpoint + ".*");
        logger.assertInternalErrorLog();
    }
}
