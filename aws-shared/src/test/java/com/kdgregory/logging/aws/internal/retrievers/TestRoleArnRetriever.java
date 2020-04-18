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

package com.kdgregory.logging.aws.internal.retrievers;

import java.util.Arrays;

import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.test.SelfMock;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.ListRolesRequest;
import com.amazonaws.services.identitymanagement.model.ListRolesResult;
import com.amazonaws.services.identitymanagement.model.Role;


/**
 *  Mock-object tests.
 */
public class TestRoleArnRetriever
{
    @Test
    public void testRoleArnRetrieverShortcuts() throws Exception
    {
        RoleArnRetriever retriever = new RoleArnRetriever();
        assertNull("no construction exception", retriever.exception);

        // setting this to a bogus value will cause an exception if it's actually invoked
        retriever.clientKlass = String.class;

        String roleArn = "arn:aws:iam::123456789012:role/ThisRoleDoesntExist";
        assertEquals("returned ARN", roleArn, retriever.invoke(roleArn));
        assertNull("no invocation exception", retriever.exception);

        assertNull("returned null when passed null", retriever.invoke(null));
        assertNull("still no invocation exception", retriever.exception);
    }


    @Test
    public void testRoleArnRetrieverHappyPath() throws Exception
    {
        RoleArnRetriever retriever = new RoleArnRetriever();
        assertNull("no construction exception", retriever.exception);

        retriever.clientKlass = TestRoleArnRetrieverHappyPath.class;

        assertEquals("returned expected result",
                     TestRoleArnRetrieverHappyPath.ROLE_ARN,
                     retriever.invoke(TestRoleArnRetrieverHappyPath.ROLE_NAME));
        assertNull("no execution exception", retriever.exception);
    }


    public static class TestRoleArnRetrieverHappyPath
    extends SelfMock<AmazonIdentityManagement>
    {
        private final static String MARKER = "second batch";

        public final static String ROLE_NAME = "TestRole";
        public final static String ROLE_ARN  = "arn:aws:iam::123456789012:role/" + ROLE_NAME;


        public TestRoleArnRetrieverHappyPath()
        {
            super(AmazonIdentityManagement.class);
        }

        @SuppressWarnings("unused")
        public ListRolesResult listRoles(ListRolesRequest request)
        {
            // we do a paginated response, with the desired role in the second page
            if (MARKER.equals(request.getMarker()))
            {
                return new ListRolesResult()
                       .withIsTruncated(Boolean.FALSE)
                       .withRoles(Arrays.asList(
                           new Role().withRoleName("Argle").withArn("arn:aws:iam::123456789012:role/Argle"),
                           new Role().withRoleName("Bargle").withArn("arn:aws:iam::123456789012:role/Bargle"),
                           new Role().withRoleName(ROLE_NAME).withArn(ROLE_ARN)
                       ));
            }
            else
            {
                return new ListRolesResult()
                       .withIsTruncated(Boolean.TRUE)
                       .withMarker(MARKER)
                       .withRoles(Arrays.asList(
                           new Role().withRoleName("Foo").withArn("arn:aws:iam::123456789012:role/Foo"),
                           new Role().withRoleName("Bar").withArn("arn:aws:iam::123456789012:role/Bar"),
                           new Role().withRoleName("Baz").withArn("arn:aws:iam::123456789012:role/Baz")
                       ));
            }
        }
    }


    @Test
    public void testRoleArnRetrieverNoResults() throws Exception
    {
        RoleArnRetriever retriever = new RoleArnRetriever();
        assertNull("no construction exception", retriever.exception);

        retriever.clientKlass = TestRoleArnRetrieverNoResults.class;

        assertNull("no result", retriever.invoke("NoSuchRole"));
        assertNull("no execution exception", retriever.exception);
    }


    public static class TestRoleArnRetrieverNoResults
    extends SelfMock<AmazonIdentityManagement>
    {
        public TestRoleArnRetrieverNoResults()
        {
            super(AmazonIdentityManagement.class);
        }

        @SuppressWarnings("unused")
        public ListRolesResult listRoles(ListRolesRequest request)
        {
            return new ListRolesResult()
                   .withIsTruncated(Boolean.FALSE)
                   .withRoles(Arrays.asList(
                       new Role().withRoleName("Foo").withArn("arn:aws:iam::123456789012:role/Foo"),
                       new Role().withRoleName("Bar").withArn("arn:aws:iam::123456789012:role/Bar"),
                       new Role().withRoleName("Baz").withArn("arn:aws:iam::123456789012:role/Baz")
                   ));
        }
    }


    @Test
    public void testRoleArnRetrieverException() throws Exception
    {
        RoleArnRetriever retriever = new RoleArnRetriever();
        assertNull("no construction exception", retriever.exception);

        retriever.clientKlass = TestRoleArnRetrieverException.class;

        assertNull("no result", retriever.invoke("NoSuchRole"));
        assertNotNull("execution exception recorded", retriever.exception);
    }


    public static class TestRoleArnRetrieverException
    extends SelfMock<AmazonIdentityManagement>
    {
        public TestRoleArnRetrieverException()
        {
            super(AmazonIdentityManagement.class);
        }

        @SuppressWarnings("unused")
        public ListRolesResult listRoles(ListRolesRequest request)
        {
            throw new RuntimeException("specific exception doesn't matter");
        }
    }
}
