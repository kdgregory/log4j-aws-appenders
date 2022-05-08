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
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;

import com.kdgregory.logging.aws.testhelpers.IAMClientMock;
import com.kdgregory.logging.aws.testhelpers.STSClientMock;
import com.kdgregory.logging.common.util.ProxyUrl;


public class TestAssumedRoleCredentialsProviderProvider
{
    private final static String TEST_ROLE_NAME = "test";
    private final static String TEST_ROLE_ARN = IAMClientMock.ROLE_ARN_BASE + "test";

    // the IAM mock will be created by the the client, to validate interaction
    private IAMClientMock iamMock;

    // we count the number of times the provider requested a client and cache what it got
    public int iamClientRequestedInvocationCount;
    public AmazonIdentityManagement iamClient;

    // there's no test-specific configuration here, so we'll preset
    private STSClientMock stsMock = new STSClientMock();

    // again, count the number of times the client asked for it, and cache the actual client
    public int stsClientRequestedInvocationCount;
    public AWSSecurityTokenService stsClient;


    /**
     *  This class overrides the client creation functions, and records their operation.
     */
    private class TestableAssumedRoleCredentialsProviderProvider
    extends AssumedRoleCredentialsProviderProvider
    {
        @Override
        protected AmazonIdentityManagement iamClient()
        {
            iamClientRequestedInvocationCount++;
            if (iamClient == null)
            {
                iamClient = iamMock.createClient();
            }
            return iamClient;
        }

        @Override
        protected AWSSecurityTokenService stsClient()
        {
            stsClientRequestedInvocationCount++;
            if (stsClient == null)
            {
                stsClient = stsMock.createClient();
            }
            return stsClient;
        }

        @Override
        // protected by default, make it public so we can exercise
        public String retrieveArn(String nameOrArn)
        {
            return super.retrieveArn(nameOrArn);
        }
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testRoleLookupByName() throws Exception
    {
        iamMock = new IAMClientMock(TEST_ROLE_NAME);
        TestableAssumedRoleCredentialsProviderProvider cpp = new TestableAssumedRoleCredentialsProviderProvider();

        String roleArn = cpp.retrieveArn(TEST_ROLE_NAME);

        assertEquals("retrieved ARN",                       TEST_ROLE_ARN,  roleArn);
        assertEquals("number of IAM clients requested",     1,              iamClientRequestedInvocationCount);
        assertEquals("number of IAM clients requested",     0,              stsClientRequestedInvocationCount);
        assertEquals("listRoles invocation count",          1,              iamMock.listRolesInvocationCount);
    }


    @Test
    public void testRoleLookupByArn() throws Exception
    {
        iamMock = new IAMClientMock(TEST_ROLE_NAME);
        TestableAssumedRoleCredentialsProviderProvider cpp = new TestableAssumedRoleCredentialsProviderProvider();

        String roleArn = cpp.retrieveArn(TEST_ROLE_NAME);

        assertEquals("retrieved ARN",                       TEST_ROLE_ARN,  roleArn);
        assertEquals("number of IAM clients requested",     1,              iamClientRequestedInvocationCount);
        assertEquals("number of IAM clients requested",     0,              stsClientRequestedInvocationCount);
        assertEquals("listRoles invocation count",          1,              iamMock.listRolesInvocationCount);
    }


    @Test
    public void testRoleLookupNoSuchRole() throws Exception
    {
        iamMock = new IAMClientMock("foo", "bar", "baz");
        TestableAssumedRoleCredentialsProviderProvider cpp = new TestableAssumedRoleCredentialsProviderProvider();

        String roleArn = cpp.retrieveArn("biff");

        assertNull("returned null",                                 roleArn);
        assertEquals("listRoles invocation count",  1,              iamMock.listRolesInvocationCount);
    }


    @Test
    public void testRoleLookupPaginated() throws Exception
    {
        iamMock = new IAMClientMock(1, "foo", "bar", TEST_ROLE_NAME, "baz");
        TestableAssumedRoleCredentialsProviderProvider cpp = new TestableAssumedRoleCredentialsProviderProvider();

        String roleArn = cpp.retrieveArn(TEST_ROLE_NAME);

        assertEquals("retrieved ARN",               TEST_ROLE_ARN,  roleArn);
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

        STSAssumeRoleSessionCredentialsProvider provider = cpp.provideProvider(TEST_ROLE_NAME, new ProxyUrl());

        // these fields are internal and subject to change (but probably won't)
        assertEquals("provider configured with role",       TEST_ROLE_ARN,                  ClassUtil.getFieldValue(provider, "roleArn", String.class));
        assertEquals("provider configured with session",    "com.kdgregory.logging.aws",    ClassUtil.getFieldValue(provider, "roleSessionName", String.class));
        assertSame("provider configured with STS client",   stsClient,                      ClassUtil.getFieldValue(provider, "securityTokenService", AWSSecurityTokenService.class));
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
            cpp.provideProvider(TEST_ROLE_NAME, new ProxyUrl());
            fail("should have thrown");
        }
        catch (Exception ex)
        {
            assertEquals("exception message", "no such role: " +  TEST_ROLE_NAME, ex.getMessage());
        }
    }
}
