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

package com.kdgregory.logging.testhelpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.*;



/**
 *  A helper for the assumed-role tests. Instantated per-test.
 */
public class RoleTestHelper
{
    private IamClient iamClient;
    private StsClient stsClient;

    private Logger localLogger = LoggerFactory.getLogger(getClass());


    public RoleTestHelper(IamClient iamClient, StsClient stsClient)
    {
        this.iamClient = iamClient;
        this.stsClient = stsClient;
    }


    public RoleTestHelper()
    {
        this(IamClient.builder().region(Region.AWS_GLOBAL).build(), StsClient.create());
    }


    /**
     *  Shuts down the clients. Call this at the end of a test using this helper.
     */
    public void shutdown()
    {
        try
        {
            if (iamClient != null)
                iamClient.close();
        }
        catch (Exception ex)
        {
            localLogger.warn("failed to shutdown IAM client", ex);
        }

        try
        {
            if (stsClient != null)
                stsClient.close();
        }
        catch (Exception ex)
        {
            localLogger.warn("failed to shutdown STS client", ex);
        }
    }


    /**
     *  Returns the invoking user's account.
     */
    public String getAwsAccountId()
    {
        GetCallerIdentityRequest request = GetCallerIdentityRequest.builder().build();
        GetCallerIdentityResponse response = stsClient.getCallerIdentity(request);
        return response.account();
    }


    /**
     *  Creates a role with the specified name, attaching zero or more managed policies.
     */
    public Role createRole(String roleName, String... managedPolicyArns)
    {
        String trustPolicy
            = "{"
            + "\"Version\":\"2012-10-17\","
            + "\"Statement\": ["
            +     "{"
            +     "\"Effect\":\"Allow\","
            +     "\"Principal\": { \"AWS\": \"" + getAwsAccountId() + "\"},"
            +     "\"Action\":[\"sts:AssumeRole\"]"
            + "}]}";

        localLogger.debug("creating role: {}", roleName);
        CreateRoleRequest createRequest = CreateRoleRequest.builder()
                                          .roleName(roleName)
                                          .assumeRolePolicyDocument(trustPolicy)
                                          .build();
        CreateRoleResponse response = iamClient.createRole(createRequest);

        Role role = response.role();
        localLogger.debug("created role: {}", role.arn());

        for (String managedPolicyArn : managedPolicyArns)
        {
            AttachRolePolicyRequest attachPolicyRequest = AttachRolePolicyRequest.builder()
                                                          .roleName(roleName)
                                                          .policyArn(managedPolicyArn)
                                                          .build();
            iamClient.attachRolePolicy(attachPolicyRequest);
            localLogger.debug("attached managed policy {} to role {}", managedPolicyArn, roleName);
        }

        return role;
    }


    /**
     *  Waits for a role to become assumable, making attempts every 5 seconds. In practice, this
     *  usually happens within 10 seconds.
     */
    public void waitUntilRoleAssumable(String roleArn, long timeout)
    throws Exception
    {
        localLogger.debug("waiting up to {} seconds for {} to become assumable", timeout, roleArn);

        long tryUntil = System.currentTimeMillis() + timeout * 1000;
        AssumeRoleRequest request = AssumeRoleRequest.builder()
                                    .roleArn(roleArn)
                                    .roleSessionName(getClass().getName())
                                    .build();

        while (System.currentTimeMillis() < tryUntil)
        {
            try
            {
                stsClient.assumeRole(request);
                return;
            }
            catch (Exception ex)
            {
                Thread.sleep(5000);
            }
        }

        throw new IllegalStateException("failed to assume " + roleArn + " within " + timeout + " seconds");
    }


    /**
     *  Deletes the role with the given name. Intended to be called in a finally block
     *  at the end of the test (fails silently as a result).
     */
    public void deleteRole(String roleName)
    {
        localLogger.debug("deleting role {}", roleName);
        try
        {
            ListAttachedRolePoliciesRequest listPoliciesRequest = ListAttachedRolePoliciesRequest.builder().roleName(roleName).build();
            ListAttachedRolePoliciesResponse listPoliciesReponse = iamClient.listAttachedRolePolicies(listPoliciesRequest);

            for (AttachedPolicy policy : listPoliciesReponse.attachedPolicies())
            {
                DetachRolePolicyRequest detachRequest = DetachRolePolicyRequest.builder()
                                                        .roleName(roleName)
                                                        .policyArn(policy.policyArn())
                                                        .build();
                iamClient.detachRolePolicy(detachRequest);
            }

            DeleteRoleRequest deleteRequest = DeleteRoleRequest.builder().roleName(roleName).build();
            iamClient.deleteRole(deleteRequest);
        }
        catch (Exception ex)
        {
            localLogger.warn("failed to delete role {}", roleName, ex);
        }
    }
}
