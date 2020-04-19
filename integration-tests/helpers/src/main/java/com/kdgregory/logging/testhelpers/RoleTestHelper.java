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

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.AttachRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.AttachedPolicy;
import com.amazonaws.services.identitymanagement.model.CreateRoleRequest;
import com.amazonaws.services.identitymanagement.model.CreateRoleResult;
import com.amazonaws.services.identitymanagement.model.DeleteRoleRequest;
import com.amazonaws.services.identitymanagement.model.DetachRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.ListAttachedRolePoliciesRequest;
import com.amazonaws.services.identitymanagement.model.ListAttachedRolePoliciesResult;
import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityResult;


/**
 *  A helper for the assumed-role tests. Instantated per-test.
 */
public class RoleTestHelper
{
    private AmazonIdentityManagement iamClient;
    private AWSSecurityTokenService stsClient;

    private Logger localLogger = LoggerFactory.getLogger(getClass());


    public RoleTestHelper()
    {
        iamClient = new AmazonIdentityManagementClient();
        stsClient = new AWSSecurityTokenServiceClient();
    }


    /**
     *  Shuts down the clients. Call this at the end of a test using this helper.
     */
    public void shutdown()
    {
        try
        {
            if (iamClient != null)
                iamClient.shutdown();
        }
        catch (Exception ex)
        {
            localLogger.warn("failed to shutdown IAM client", ex);
        }

        try
        {
            if (stsClient != null)
                stsClient.shutdown();
        }
        catch (Exception ex)
        {
            localLogger.warn("failed to shutdown STS client", ex);
        }
    }


    /**
     *  Creates a role with the specified name, attaching zero or more managed policies.
     */
    public Role createRole(String roleName, String... managedPolicyArns)
    {
        GetCallerIdentityResult identityResponse = stsClient.getCallerIdentity(new GetCallerIdentityRequest());
        String trustPolicy
            = "{"
            + "\"Version\":\"2012-10-17\","
            + "\"Statement\": ["
            +     "{"
            +     "\"Effect\":\"Allow\","
            +     "\"Principal\": { \"AWS\": \"" + identityResponse.getAccount() + "\"},"
            +     "\"Action\":[\"sts:AssumeRole\"]"
            + "}]}";

        localLogger.debug("creating role: {}", roleName);
        CreateRoleRequest createRequest = new CreateRoleRequest()
                                          .withRoleName(roleName)
                                          .withAssumeRolePolicyDocument(trustPolicy);
        CreateRoleResult response = iamClient.createRole(createRequest);
        localLogger.debug("created role: {}", response.getRole().getArn());

        for (String managedPolicyArn : managedPolicyArns)
        {
            AttachRolePolicyRequest attachPolicyRequest = new AttachRolePolicyRequest()
                                                          .withRoleName(roleName)
                                                          .withPolicyArn(managedPolicyArn);
            iamClient.attachRolePolicy(attachPolicyRequest);
            localLogger.debug("attached managed policy {} to role {}", managedPolicyArn, roleName);
        }

        return response.getRole();
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
        AssumeRoleRequest request = new AssumeRoleRequest()
                                    .withRoleArn(roleArn)
                                    .withRoleSessionName(String.valueOf(tryUntil)); // gotta use something

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
            ListAttachedRolePoliciesRequest listPoliciesRequest = new ListAttachedRolePoliciesRequest().withRoleName(roleName);
            ListAttachedRolePoliciesResult listPoliciesReponse = iamClient.listAttachedRolePolicies(listPoliciesRequest);

            for (AttachedPolicy policy : listPoliciesReponse.getAttachedPolicies())
            {
                DetachRolePolicyRequest detachRequest = new DetachRolePolicyRequest()
                                                        .withRoleName(roleName)
                                                        .withPolicyArn(policy.getPolicyArn());
                iamClient.detachRolePolicy(detachRequest);
            }

            DeleteRoleRequest deleteRequest = new DeleteRoleRequest().withRoleName(roleName);
            iamClient.deleteRole(deleteRequest);
        }
        catch (Exception ex)
        {
            localLogger.warn("failed to delete role {}", roleName, ex);
        }
    }
}
