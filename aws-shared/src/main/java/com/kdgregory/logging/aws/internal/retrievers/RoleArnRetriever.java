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

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import com.kdgregory.logging.aws.internal.ReflectionBasedInvoker;


/**
 *  Finds a role's ARN given its name.
 *  <p>
 *  Short-circuits if provided something that looks like an ARN (returns it), or
 *  null (returns null). Returns <code>null</code> if unable to retrieve the role
 *  for any other reason (generally a missing library or invalid credentials).
 */
public class RoleArnRetriever
extends ReflectionBasedInvoker
{
    private Class<?> roleKlass;

    public RoleArnRetriever()
    {
        super("com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient",
              "com.amazonaws.services.identitymanagement.model.ListRolesRequest",
              "com.amazonaws.services.identitymanagement.model.ListRolesResult");
        roleKlass = loadClass("com.amazonaws.services.identitymanagement.model.Role");
    }

    public String invoke(String roleName)
    {
        // bozo check
        if (roleName == null)
            return null;

        // if it looks like an ARN already, don't do anything
        if (Pattern.matches("arn:.+:iam::\\d{12}:role/.+", roleName))
            return roleName;

        Object client = instantiate(clientKlass);
        try
        {
            Object request = instantiate(requestKlass);
            Object response;
            do
            {
                response = invokeRequest(client, "listRoles", request);
                if (response == null)
                    return null;
                String marker = getResponseValue(response, "getMarker", String.class);
                setRequestValue(request, "setMarker", String.class, marker);
                List<Object> roles = getResponseValue(response, "getRoles", List.class);
                if (roles == null) roles = Collections.emptyList(); // should not be needed
                for (Object role : roles)
                {
                    String roleArn = (String)invokeMethod(roleKlass, role, "getArn", null, null);
                    if (roleName.equals(invokeMethod(roleKlass, role, "getRoleName", null, null)))
                        return roleArn;
                }
            } while (getResponseValue(response, "isTruncated", Boolean.class));
            return null;
        }
        finally
        {
            shutdown(client);
        }
    }
}