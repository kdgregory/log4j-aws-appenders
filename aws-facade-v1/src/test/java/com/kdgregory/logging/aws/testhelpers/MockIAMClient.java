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

package com.kdgregory.logging.aws.testhelpers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.*;


/**
 *  Mocks the IAM operations that we care about (at this point, only listRoles).
 */
public class MockIAMClient
implements InvocationHandler
{
    /** All role ARNs will start with this */
    public final static String ROLE_ARN_BASE = "arn:aws:iam::123456789012:role/";

    private List<Role> knownRoles = new ArrayList<>();
    private int pageSize;

    // invocation counts for known methods
    public int listRolesInvocationCount;


    /**
     *  Default constructor: knows about a list of roles.
     */
    public MockIAMClient(int pageSize, String... knownRoleNames)
    {
        this.pageSize = pageSize;
        for (String roleName : knownRoleNames)
        {
            Role role = new Role()
                        .withRoleName(roleName)
                        .withArn(ROLE_ARN_BASE + roleName);
            knownRoles.add(role);
        }
    }

    /**
     *  Constructor for paginated operations.
     */
    public MockIAMClient(String... knownRoleNames)
    {
        this(knownRoleNames.length + 1, knownRoleNames);
    }

//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    public AmazonIdentityManagement createClient()
    {
        return (AmazonIdentityManagement)Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] { AmazonIdentityManagement.class },
                                    MockIAMClient.this);
    }
//----------------------------------------------------------------------------
//  Invocation Handler
//----------------------------------------------------------------------------

    /**
     *  The invocation handler; test code should not care about this.
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        String methodName = method.getName();
        if (methodName.equals("listRoles"))
        {
            listRolesInvocationCount++;
            ListRolesRequest request = (ListRolesRequest)args[0];
            return listRoles(request);
        }

        // if nothing matches, fall through to here
        System.err.println("invocation handler called unexpectedly: " + methodName);
        throw new IllegalArgumentException("unexpected client call: " + methodName);
    }

//----------------------------------------------------------------------------
//  Default mock implementations
//----------------------------------------------------------------------------

    public ListRolesResult listRoles(ListRolesRequest request)
    {
        int startIdx = (request.getMarker() != null)
                     ? Integer.valueOf(request.getMarker())
                     : 0;
        int endIdx = Math.min(knownRoles.size(), startIdx + pageSize);
        boolean truncated = endIdx < knownRoles.size();
        String marker = truncated ? String.valueOf(endIdx) : null;

        return new ListRolesResult()
               .withRoles(knownRoles.subList(startIdx, endIdx))
               .withIsTruncated(Boolean.valueOf(truncated))
               .withMarker(marker);
    }
}
