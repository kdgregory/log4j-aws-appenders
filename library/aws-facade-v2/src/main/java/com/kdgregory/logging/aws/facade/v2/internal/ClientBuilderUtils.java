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
import java.net.URISyntaxException;

import com.kdgregory.logging.common.util.ProxyUrl;

import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.client.builder.SdkSyncClientBuilder;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;


/**
 *  Static utility methods for building clients. These are extracted from
 *  {@link ClientFactory} for separate testing.
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
            builder.endpointOverride(URI.create(endpoint));
        }
        if ((region != null) && ! region.isEmpty())
        {
            builder.region(Region.of(region));
        }
    }


    /**
     *  Optionally configures the builder to use a proxy. No-op if passed proxy
     *  config is not valid (ie, underlying environment variable wasn't set).
     */
    public static void optSetProxy(AwsClientBuilder<?,?> builder, ProxyUrl proxyUrl)
    {
        if (! proxyUrl.isValid())
            return;

        try
        {
            String reconstructedUrl = proxyUrl.getScheme().toLowerCase() + "://" + proxyUrl.getHostname() + ":" + proxyUrl.getPort();
            ProxyConfiguration proxyConfig = ProxyConfiguration.builder()
                                             .endpoint(new URI(reconstructedUrl))
                                             .username(proxyUrl.getUsername())
                                             .password(proxyUrl.getPassword())
                                             .addNonProxyHost("169.254.169.254")
                                             .useSystemPropertyValues(Boolean.FALSE)
                                             .build();

            ApacheHttpClient.Builder clientBuilder = ApacheHttpClient.builder().proxyConfiguration(proxyConfig);
            ((SdkSyncClientBuilder<?,?>)builder).httpClientBuilder(clientBuilder);
        }
        catch (URISyntaxException ex)
        {
            throw new RuntimeException("invalid proxy URL: " + proxyUrl);
        }
        catch (Exception ex)
        {
            throw new RuntimeException("failed to configure proxy", ex);
        }
    }


    /**
     *  Optionally configures the builder with an assumed-role credentials provider.
     *  No-op if the provided role ARN is null or empty. The provider will use the
     *  specified proxy if it is valid.
     */
    public static void optSetAssumedRoleCredentialsProvider(AwsClientBuilder<?,?> builder, String roleNameOrArn, ProxyUrl proxyUrl)
    {
        if ((roleNameOrArn != null) && !roleNameOrArn.isEmpty())
        {
            StsAssumeRoleCredentialsProvider credentialsProvider = new AssumedRoleCredentialsProviderProvider()
                                                                   .provideProvider(roleNameOrArn, proxyUrl);
            builder.credentialsProvider(credentialsProvider);
        }
    }
}
