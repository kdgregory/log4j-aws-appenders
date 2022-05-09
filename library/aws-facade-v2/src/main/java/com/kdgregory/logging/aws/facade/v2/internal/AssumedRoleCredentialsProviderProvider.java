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

import java.util.regex.Pattern;

import com.kdgregory.logging.common.util.ProxyUrl;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.IamClientBuilder;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.*;


/**
 *  Used by {@link ClientFactory} to create an <code>STSAssumeRoleSessionCredentialsProvider</code>.
 *  This is a separate class to (1) ensure that there isn't a hard reference to the STS SDK, and
 *  (2) to simplify testing.
 */
public class AssumedRoleCredentialsProviderProvider
{
    private ProxyUrl proxy;
    private IamClient iamClient;
    private StsClient stsClient;

//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    public StsAssumeRoleCredentialsProvider provideProvider(String roleNameOrArn, ProxyUrl proxyUrl)
    {
        this.proxy = proxyUrl;
        String roleArn = retrieveArn(roleNameOrArn);
        if (roleArn == null)
            throw new RuntimeException("no such role: " + roleNameOrArn);

        AssumeRoleRequest request = AssumeRoleRequest.builder()
                                    .roleArn(roleArn)
                                    .roleSessionName("com.kdgregory.logging.aws")
                                    .build();

        return StsAssumeRoleCredentialsProvider.builder()
               .stsClient(stsClient())
               .refreshRequest(request)
               .build();
    }

//----------------------------------------------------------------------------
//  Internals -- protected so they can be overridden for testing
//----------------------------------------------------------------------------

    protected IamClient iamClient()
    {
        if (iamClient == null)
        {
            IamClientBuilder clientBuilder = IamClient.builder().region(Region.AWS_GLOBAL);
            ClientBuilderUtils.optSetProxy(clientBuilder, proxy);
            iamClient = clientBuilder.build();
        }
        return iamClient;
    }
    

    protected StsClient stsClient()
    {
        if (stsClient == null)
        {
            StsClientBuilder clientBuilder = StsClient.builder();
            ClientBuilderUtils.optSetProxy(clientBuilder, proxy);
            stsClient = clientBuilder.build();
        }
        return stsClient;
    }


    public String retrieveArn(String nameOrArn)
    {
        if (Pattern.matches("arn:.*:iam::\\d{12}:role/.*", nameOrArn))
            return nameOrArn;

        for (Role role : iamClient().listRolesPaginator().roles())
        {
            if (role.roleName().equals(nameOrArn) || role.arn().equals(nameOrArn))
                return role.arn();
        }

        return null;
    }
}
