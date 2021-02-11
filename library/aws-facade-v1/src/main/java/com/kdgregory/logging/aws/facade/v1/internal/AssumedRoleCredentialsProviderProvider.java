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

import java.util.regex.Pattern;

import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.ListRolesRequest;
import com.amazonaws.services.identitymanagement.model.ListRolesResult;
import com.amazonaws.services.identitymanagement.model.Role;


/**
 *  Used by {@link ClientFactory} to create an <code>STSAssumeRoleSessionCredentialsProvider</code>.
 *  This is a separate class to (1) ensure that there isn't a hard reference to the STS SDK, and
 *  (2) to simplify testing.
 */
public class AssumedRoleCredentialsProviderProvider
{
    private AmazonIdentityManagement iamClient;

//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    public STSAssumeRoleSessionCredentialsProvider provideProvider(String roleNameOrArn)
    {
        String roleArn = retrieveArn(roleNameOrArn);
        if (roleArn == null)
            throw new RuntimeException("no such role: " + roleNameOrArn);

        return new STSAssumeRoleSessionCredentialsProvider.Builder(roleArn, "com.kdgregory.logging.aws")
               .build();
    }

//----------------------------------------------------------------------------
//  Internals -- protected so they can be overridden for testing
//----------------------------------------------------------------------------

    protected AmazonIdentityManagement iamClient()
    {
        if (iamClient == null)
        {
            iamClient = AmazonIdentityManagementClientBuilder.defaultClient();
        }
        return iamClient;
    }


    public String retrieveArn(String nameOrArn)
    {
        if (Pattern.matches("arn:.*:iam::\\d{12}:role/.*", nameOrArn))
            return nameOrArn;

        ListRolesRequest request = new ListRolesRequest();
        boolean isTruncated = false;
        do
        {
            ListRolesResult response = iamClient().listRoles(request);
            for (Role role : response.getRoles())
            {
                if (role.getRoleName().equals(nameOrArn) || role.getArn().equals(nameOrArn))
                    return role.getArn();
            }
            request.setMarker(response.getMarker());
            isTruncated = (response.isTruncated() != null) && response.isTruncated().booleanValue();
        } while (isTruncated);

        return null;
    }
}
