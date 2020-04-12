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

import java.util.Arrays;

import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.test.SelfMock;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.ListRolesRequest;
import com.amazonaws.services.identitymanagement.model.ListRolesResult;
import com.amazonaws.services.identitymanagement.model.Role;

import com.kdgregory.logging.aws.internal.Utils;


public class TestUtils
{
//----------------------------------------------------------------------------
//  Tests for retrieveRoleArn()
//----------------------------------------------------------------------------

    // produces a standard set of roles, with pagination
    SelfMock<AmazonIdentityManagement> listRolesMock = new SelfMock<AmazonIdentityManagement>(AmazonIdentityManagement.class)
    {
        @SuppressWarnings("unused")
        public ListRolesResult listRoles(ListRolesRequest request)
        {
            if ("second page".equals(request.getMarker()))
            {
                return new ListRolesResult()
                       .withRoles(Arrays.asList(
                           new Role().withRoleName("Argle").withArn("arn:aws:iam::123456789012:role/Argle"),
                           new Role().withRoleName("ExampleRole").withArn("arn:aws:iam::123456789012:role/ExampleRole"),
                           new Role().withRoleName("Bargle").withArn("arn:aws:iam::123456789012:role/Bargle")
                       ));
            }
            else
            {
                return new ListRolesResult()
                       .withIsTruncated(Boolean.TRUE)
                       .withMarker("second page")
                       .withRoles(Arrays.asList(
                           new Role().withRoleName("Foo").withArn("arn:aws:iam::123456789012:role/Foo"),
                           new Role().withRoleName("Bar").withArn("arn:aws:iam::123456789012:role/Bar"),
                           new Role().withRoleName("Baz").withArn("arn:aws:iam::123456789012:role/Baz")
                       ));
            }
        }
    };


    @Test
    public void testRetrieveRoleArnWhenGivenArn() throws Exception
    {
        String roleArn = "arn:aws:iam::123456789012:role/ExampleRole";

        assertEquals(roleArn, Utils.retrieveRoleArn(roleArn, null));
    }


    @Test
    public void testRetrieveRoleArn() throws Exception
    {
        String roleName = "ExampleRole";
        String roleArn = "arn:aws:iam::123456789012:role/ExampleRole";

        AmazonIdentityManagement mockClient = listRolesMock.getInstance();

        assertEquals(roleArn, Utils.retrieveRoleArn(roleName, mockClient));
    }

}
