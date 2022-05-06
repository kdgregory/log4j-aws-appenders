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

package com.kdgregory.logging.aws.facade.v2.internal;

import java.net.URI;

import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.ClassUtil;

import com.kdgregory.logging.common.util.ProxyUrl;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.client.builder.SdkSyncClientBuilder;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpClient.Builder;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;


public class TestClientBuilderUtils
{

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    @SuppressWarnings("unused")
    private static class TestableAwsClientBuilder
    implements AwsClientBuilder<TestableAwsClientBuilder,Object>, SdkSyncClientBuilder<TestableAwsClientBuilder,Object>
    {
        // exposed so that configuration calls can be verified
        public URI endpointOverride;
        public Region region;
        public SdkHttpClient httpClient;
        public Builder<?> httpClientBuilder;


        @Override
        public Object build()
        {
            return Boolean.TRUE;
        }

        @Override
        public TestableAwsClientBuilder overrideConfiguration(ClientOverrideConfiguration overrideConfiguration)
        {
            throw new UnsupportedOperationException("this method should not be called");
        }

        @Override
        public TestableAwsClientBuilder endpointOverride(URI value)
        {
            endpointOverride = value;
            return this;
        }

        @Override
        public TestableAwsClientBuilder credentialsProvider(AwsCredentialsProvider value)
        {
            throw new UnsupportedOperationException("we don't call this method during testing because AWS validates argument");
        }

        @Override
        public TestableAwsClientBuilder region(Region value)
        {
            this.region = value;
            return this;
        }

        @Override
        public TestableAwsClientBuilder httpClient(SdkHttpClient value)
        {
            this.httpClient = value;
            return this;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public TestableAwsClientBuilder httpClientBuilder(Builder value)
        {
            this.httpClientBuilder = value;
            return this;
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

        assertEquals("region set",                  Region.US_EAST_1, builder.region);
    }


    @Test
    public void testOptSetRegionOrEndpoint_endpoint() throws Exception
    {
        TestableAwsClientBuilder builder = new TestableAwsClientBuilder();
        ClientBuilderUtils.optSetRegionOrEndpoint(builder, "us-east-1", "https://www.example.com");

        assertEquals("region set",                  Region.US_EAST_1,                   builder.region);
        assertEquals("endpoint set",                new URI("https://www.example.com"), builder.endpointOverride);
    }


    @Test
    public void testOptSetRegionOrEndpoint_noop() throws Exception
    {
        // we can't intercept the builder's setter calls, so we'll set dummy
        // values and verify that they're still set after the call
        TestableAwsClientBuilder builder = new TestableAwsClientBuilder()
                                           .region(Region.US_EAST_1)
                                           .endpointOverride(new URI("https://www.example.com"));
        ClientBuilderUtils.optSetRegionOrEndpoint(builder, null, null);

        assertEquals("region was not changed",      Region.US_EAST_1,                   builder.region);
        assertEquals("endpoint was not changed",    new URI("https://www.example.com"), builder.endpointOverride);
    }


    @Test
    public void testOptSetProxy() throws Exception
    {
        TestableAwsClientBuilder builder = new TestableAwsClientBuilder();
        ClientBuilderUtils.optSetProxy(builder, new ProxyUrl("https://myuser:mypassword@proxy.example.com:3128"));

        // this is all implementation-dependent, may break at any time
        ApacheHttpClient.Builder clientBuilder = (ApacheHttpClient.Builder)builder.httpClientBuilder;
        ProxyConfiguration proxyConfig = ClassUtil.getFieldValue(clientBuilder, "proxyConfiguration", ProxyConfiguration.class);

        assertEquals("proxy scheme",                "https",                proxyConfig.scheme());
        assertEquals("proxy host",                  "proxy.example.com",    proxyConfig.host());
        assertEquals("proxy port",                  3128,                   proxyConfig.port());
        assertEquals("proxy host",                  "myuser",               proxyConfig.username());
        assertEquals("proxy host",                  "mypassword",           proxyConfig.password());
    }


    @Test
    public void testOptSetProxy_noop() throws Exception
    {
        TestableAwsClientBuilder builder = new TestableAwsClientBuilder();
        ClientBuilderUtils.optSetProxy(builder, new ProxyUrl(null));

        ApacheHttpClient.Builder clientBuilder = (ApacheHttpClient.Builder)builder.httpClientBuilder;

        assertNull("builder not configured with client builder",            clientBuilder);
    }

}
