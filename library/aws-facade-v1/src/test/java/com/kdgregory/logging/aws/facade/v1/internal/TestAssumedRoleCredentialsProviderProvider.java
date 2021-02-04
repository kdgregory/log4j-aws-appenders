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

import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.ClassUtil;

import com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;

import com.kdgregory.logging.aws.testhelpers.IAMClientMock;


public class TestAssumedRoleCredentialsProviderProvider
{
    private final static String TEST_ROLE_NAME = "test";
    private final static String TEST_ROLE_ARN = IAMClientMock.ROLE_ARN_BASE + "test";

    // each test must creat this prior to creating the testable provider
    private IAMClientMock iamMock;


    private class TestableAssumedRoleCredentialsProviderProvider
    extends AssumedRoleCredentialsProviderProvider
    {
        @Override
        protected AmazonIdentityManagement iamClient()
        {
            // all created clients refer to the same invocation handler, so no need to cache
            return iamMock.createClient();
        }

        @Override
        public String retrieveArn(String nameOrArn)
        {
            return super.retrieveArn(nameOrArn);
        }
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testLookupByName() throws Exception
    {
        iamMock = new IAMClientMock(TEST_ROLE_NAME);
        TestableAssumedRoleCredentialsProviderProvider cpp = new TestableAssumedRoleCredentialsProviderProvider();

        assertEquals("retrieved ARN",               TEST_ROLE_ARN,  cpp.retrieveArn(TEST_ROLE_NAME));

        assertEquals("listRoles invocation count",  1,              iamMock.listRolesInvocationCount);
    }


    @Test
    public void testLookupByArn() throws Exception
    {
        iamMock = new IAMClientMock(TEST_ROLE_NAME);
        TestableAssumedRoleCredentialsProviderProvider cpp = new TestableAssumedRoleCredentialsProviderProvider();

        assertEquals("retrieved ARN",               TEST_ROLE_ARN,  cpp.retrieveArn(TEST_ROLE_ARN));

        assertEquals("listRoles invocation count",  0,              iamMock.listRolesInvocationCount);
    }


    @Test
    public void testLookupNoSuchRole() throws Exception
    {
        iamMock = new IAMClientMock("foo", "bar", "baz");
        TestableAssumedRoleCredentialsProviderProvider cpp = new TestableAssumedRoleCredentialsProviderProvider();

        assertNull("returned null",                                 cpp.retrieveArn("biff"));

        assertEquals("listRoles invocation count",  1,              iamMock.listRolesInvocationCount);
    }


    @Test
    public void testLookupPaginated() throws Exception
    {
        iamMock = new IAMClientMock(1, "foo", "bar", TEST_ROLE_NAME, "baz");
        TestableAssumedRoleCredentialsProviderProvider cpp = new TestableAssumedRoleCredentialsProviderProvider();

        assertEquals("retrieved ARN",               TEST_ROLE_ARN,  cpp.retrieveArn(TEST_ROLE_NAME));

        assertEquals("listRoles invocation count",  3,              iamMock.listRolesInvocationCount);
    }


    @Test
    public void testProviderWithKnownRoleByName() throws Exception
    {
        TestableAssumedRoleCredentialsProviderProvider cpp = new TestableAssumedRoleCredentialsProviderProvider()
        {
            @Override
            public String retrieveArn(String nameOrArn)
            {
                assertEquals("verify internal call", TEST_ROLE_NAME, nameOrArn);
                return TEST_ROLE_ARN;
            }
        };

        STSAssumeRoleSessionCredentialsProvider provider = cpp.provideProvider(TEST_ROLE_NAME);

        // these fields are internal and subject to change (but probably won't)
        assertEquals("provider configured with role",       TEST_ROLE_ARN,                  ClassUtil.getFieldValue(provider, "roleArn", String.class));
        assertEquals("provider configured with session",    "com.kdgregory.logging.aws",    ClassUtil.getFieldValue(provider, "roleSessionName", String.class));
    }


    @Test
    public void testProviderWithNonexistentRole() throws Exception
    {
        TestableAssumedRoleCredentialsProviderProvider cpp = new TestableAssumedRoleCredentialsProviderProvider()
        {
            @Override
            public String retrieveArn(String nameOrArn)
            {
                return null;
            }
        };

        try
        {
            cpp.provideProvider(TEST_ROLE_NAME);
            fail("should have thrown");
        }
        catch (Exception ex)
        {
            assertEquals("exception message", "no such role: " +  TEST_ROLE_NAME, ex.getMessage());
        }
    }
}
