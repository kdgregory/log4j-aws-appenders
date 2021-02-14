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

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.*;


/**
 *  Used by {@link ClientFactory} to create an <code>STSAssumeRoleSessionCredentialsProvider</code>.
 *  This is a separate class to (1) ensure that there isn't a hard reference to the STS SDK, and
 *  (2) to simplify testing.
 */
public class AssumedRoleCredentialsProviderProvider
{
    private IamClient iamClient;

//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    public StsAssumeRoleCredentialsProvider provideProvider(String roleNameOrArn)
    {
        String roleArn = retrieveArn(roleNameOrArn);
        if (roleArn == null)
            throw new RuntimeException("no such role: " + roleNameOrArn);

        // setting the client on the provider builder is not documented for v2 but is required
        StsClient stsClient = StsClient.builder().build();

        AssumeRoleRequest request = AssumeRoleRequest.builder()
                                    .roleArn(roleArn)
                                    .roleSessionName("com.kdgregory.logging.aws")
                                    .build();

        return StsAssumeRoleCredentialsProvider.builder()
               .stsClient(stsClient)
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
            iamClient = IamClient.builder().region(Region.AWS_GLOBAL).build();
        }
        return iamClient;
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
