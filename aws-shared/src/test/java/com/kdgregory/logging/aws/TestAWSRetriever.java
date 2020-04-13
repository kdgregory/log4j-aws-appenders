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

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.ListRolesResult;
import com.amazonaws.services.identitymanagement.model.Role;

import com.kdgregory.logging.aws.internal.AWSRetriever;

import static net.sf.kdgcommons.test.StringAsserts.*;

import java.util.List;
import java.util.UUID;


/**
 *  "Integration" tests for the functions in <code>AWSRetriever</code>. These are
 *  disabled by default, should only be run in an environment that has access
 *  to AWS.
 */
public class TestAWSRetriever
{
    @Test
    public void testRetrieveAccountId() throws Exception
    {
        assertRegex("\\d{12}", AWSRetriever.retrieveAccountId());
    }


    @Test
    @Ignore
    public void testRetrieveAccountIdWithNoCredentials() throws Exception
    {
        // as the name says, this needs to be run without valid credentials
        assertRegex(null, AWSRetriever.retrieveAccountId());
    }


    @Test
    public void testRetrieveRoleArnHappyPath() throws Exception
    {
        // need to find an existing role to run this; will assume that this is run
        // in an account that has multiple roles, and will pick one from list
        AmazonIdentityManagement iamClient = new AmazonIdentityManagementClient();
        ListRolesResult result = iamClient.listRoles();
        List<Role> roles = result.getRoles();
        Role role = roles.get(roles.size() / 2);

        assertEquals(role.getArn(), AWSRetriever.retrieveRoleArn(role.getRoleName()));
    }


    @Test
    public void testRetrieveRoleArnSadPath() throws Exception
    {
        // I'm pretty sure that this will never be found

        assertEquals(null, AWSRetriever.retrieveRoleArn(UUID.randomUUID().toString()));
    }


    @Test
    public void testRetrieveRoleArnShortcut() throws Exception
    {
        String roleArn = "arn:aws:iam::123456789012:role/ThisRoleDoesntExist";

        // no way to verify that we didn't actually talk to AWS
        assertEquals(roleArn, AWSRetriever.retrieveRoleArn(roleArn));
    }

}
