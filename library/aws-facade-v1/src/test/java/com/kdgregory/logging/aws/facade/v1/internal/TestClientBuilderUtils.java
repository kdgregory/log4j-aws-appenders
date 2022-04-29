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

import org.junit.Test;
import static org.junit.Assert.*;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.ClientConfigurationFactory;
import com.amazonaws.Protocol;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;

import com.kdgregory.logging.common.util.ProxyUrl;


public class TestClientBuilderUtils
{

//----------------------------------------------------------------------------
//  Helpers
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
            throw new RuntimeException("these tests should never try to build a client");
        }
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testOptSetRegionOrEndpoint_region() throws Exception
    {
        TestableAwsClientBuilder builder = new TestableAwsClientBuilder();
        ClientBuilderUtils.optSetRegionOrEndpoint(builder, "us-east-1", null);

        assertEquals("region set",  "us-east-1", builder.getRegion());
    }


    @Test
    public void testOptSetRegionOrEndpoint_endpoint() throws Exception
    {
        TestableAwsClientBuilder builder = new TestableAwsClientBuilder();
        ClientBuilderUtils.optSetRegionOrEndpoint(builder, "us-east-1", "https://www.example.com");

        assertNull("region not set",                                    builder.getRegion());
        assertEquals("endpoint set",        "https://www.example.com",  builder.getEndpoint().getServiceEndpoint());
        assertEquals("signing region set",  "us-east-1",                builder.getEndpoint().getSigningRegion());
    }


    @Test
    public void testOptSetRegionOrEndpoint_noop() throws Exception
    {
        // we can't intercept the builder's setter calls, so we'll set dummy
        // values and verify that they're still set after the call
        TestableAwsClientBuilder builder = new TestableAwsClientBuilder()
                                           .withRegion("us-east-1")
                                           .withEndpointConfiguration(
                                               new EndpointConfiguration("https://www.example.com", "us-east-2"));

        ClientBuilderUtils.optSetRegionOrEndpoint(builder, null, null);
        assertEquals("region was not changed",      "us-east-1",                builder.getRegion());
        assertEquals("endpoint was not changed",    "https://www.example.com",  builder.getEndpoint().getServiceEndpoint());
        assertEquals("endpoint was not changed",    "us-east-2",                builder.getEndpoint().getSigningRegion());
    }


    @Test
    public void testOptSetProxy() throws Exception
    {
        TestableAwsClientBuilder builder = new TestableAwsClientBuilder();

        ClientBuilderUtils.optSetProxy(builder, new ProxyUrl("https://myuser:mypassword@proxy.example.com:3128"));
        ClientConfiguration clientConfig = builder.getClientConfiguration();

        assertNotNull("builder configured with ClientConfiguration",                        clientConfig);
        assertEquals("proxy protocol",                              Protocol.HTTPS,         clientConfig.getProxyProtocol());
        assertEquals("proxy host",                                  "proxy.example.com",    clientConfig.getProxyHost());
        assertEquals("proxy port",                                  3128,                   clientConfig.getProxyPort());
        assertEquals("proxy host",                                  "myuser",               clientConfig.getProxyUsername());
        assertEquals("proxy host",                                  "mypassword",           clientConfig.getProxyPassword());
    }


    @Test
    public void testOptSetProxy_noop() throws Exception
    {
        TestableAwsClientBuilder builder = new TestableAwsClientBuilder();

        ClientBuilderUtils.optSetProxy(builder, new ProxyUrl(null));
        ClientConfiguration clientConfig = builder.getClientConfiguration();

        assertNull("builder not configured with ClientConfiguration",                       clientConfig);
    }
}
