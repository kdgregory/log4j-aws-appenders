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

import com.amazonaws.ClientConfiguration;
import com.amazonaws.Protocol;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;

import com.kdgregory.logging.common.util.ProxyUrl;


/**
 *  Utility functions for modifying client builders.
 */
public class ClientBuilderUtils
{

    /**
     *  Optionally configures the builder to use non-default region or endpoint.
     *  No-op if passed values are null or empty.
     */
    public static void optSetRegionOrEndpoint(AwsClientBuilder<?,?> builder, String region, String endpoint)
    {
        if ((endpoint != null) && ! endpoint.isEmpty())
        {
            builder.setEndpointConfiguration(
                new EndpointConfiguration(endpoint, region));
        }
        else if ((region != null) && ! region.isEmpty())
        {
            builder.setRegion(region);
        }
    }


    /**
     *  Optionally configures the builder to use a proxy. No-op if passed proxy
     *  config is not valid (ie, underlying environment variable wasn't set).
     */
    public static void optSetProxy(AwsClientBuilder<?,?> builder, ProxyUrl proxyUrl)
    {
        if (proxyUrl.isValid())
        {
            ClientConfiguration clientConfig = new ClientConfiguration()
                   .withProxyProtocol(proxyUrl.getScheme().equals("https") ? Protocol.HTTPS : Protocol.HTTP)
                   .withProxyHost(proxyUrl.getHostname())
                   .withProxyPort(proxyUrl.getPort())
                   .withProxyUsername(proxyUrl.getUsername())
                   .withProxyPassword(proxyUrl.getPassword())
                   .withNonProxyHosts("169.254.169.254");

            builder.setClientConfiguration(clientConfig);
        }
    }


    /**
     *  Optionally configures the builder with an assumed-role credentials provider.
     *  No-op if the provided role ARN is null or empty. The provider will use the
     *  specified proxy if it is valid.
     */
    public static void optSetAssumedRoleCredentialsProvider(AwsClientBuilder<?,?> builder, String roleNameOrArn, ProxyUrl proxy)
    {
        if ((roleNameOrArn != null) && !roleNameOrArn.isEmpty())
        {
            AWSCredentialsProvider credentialsProvider = new AssumedRoleCredentialsProviderProvider()
                                                         .provideProvider(roleNameOrArn, proxy);
            builder.setCredentials(credentialsProvider);
        }
    }

}
