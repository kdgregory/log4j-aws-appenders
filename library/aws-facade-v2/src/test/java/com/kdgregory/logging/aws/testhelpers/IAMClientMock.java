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

import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.*;
import software.amazon.awssdk.services.iam.paginators.ListRolesIterable;


/**
 *  Supports mock-object testing of facade code that uses IAM.
 *  <p>
 *  This is a proxy-based mock: you create an instance of the mock, and from it
 *  create an instance of a proxy that implements the client interface. Each of
 *  the supported client methods is implemented in the mock, and called from the
 *  invocation handler. To test specific behaviors, subclasses should override
 *  the method implementation.
 *  <p>
 *  Each method has an associated invocation counter, along with variables that
 *  hold the last set of arguments passed to this method. These variables are
 *  public, to minimize boilerplate code; if testcases modify the variables, they
 *  only hurt themselves.
 *  <p>
 *  The mock is assumed to be invoked from a single thread, so no effort has been
 *  taken to make it threadsafe.
 *  <p>
 *  At this time, the only supported IAM operation is <code>ListRoles</code>.
 */
public class IAMClientMock
implements InvocationHandler
{
    /** All role ARNs will start with this */
    public final static String ROLE_ARN_BASE = "arn:aws:iam::123456789012:role/";

    private List<Role> knownRoles = new ArrayList<>();
    private int pageSize;
    
    // cached client for paginator
    private IamClient cachedClient;

    // invocation counts for known methods
    public int listRolesInvocationCount;


    /**
     *  Default constructor: knows about a list of roles.
     */
    public IAMClientMock(int pageSize, String... knownRoleNames)
    {
        this.pageSize = pageSize;
        for (String roleName : knownRoleNames)
        {
            Role role = Role.builder()
                        .roleName(roleName)
                        .arn(ROLE_ARN_BASE + roleName)
                        .build();
            knownRoles.add(role);
        }
    }

    /**
     *  Constructor for paginated operations.
     */
    public IAMClientMock(String... knownRoleNames)
    {
        this(knownRoleNames.length + 1, knownRoleNames);
    }

//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    public IamClient createClient()
    {
        if (cachedClient == null)
        {
            cachedClient = (IamClient)Proxy.newProxyInstance(
                                      getClass().getClassLoader(),
                                      new Class<?>[] { IamClient.class },
                                      IAMClientMock.this);
        }
        return cachedClient;
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
        if (methodName.equals("listRolesPaginator"))
        {
            // this is not actually an SDK request
            return new ListRolesIterable(cachedClient, ListRolesRequest.builder().build());
        }
        else if (methodName.equals("listRoles"))
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

    public ListRolesResponse listRoles(ListRolesRequest request)
    {
        int startIdx = (request.marker() != null)
                     ? Integer.valueOf(request.marker())
                     : 0;
        int endIdx = Math.min(knownRoles.size(), startIdx + pageSize);
        boolean truncated = endIdx < knownRoles.size();
        String marker = truncated ? String.valueOf(endIdx) : null;

        return ListRolesResponse.builder()
               .roles(knownRoles.subList(startIdx, endIdx))
               .isTruncated(Boolean.valueOf(truncated))
               .marker(marker)
               .build();
    }
}
