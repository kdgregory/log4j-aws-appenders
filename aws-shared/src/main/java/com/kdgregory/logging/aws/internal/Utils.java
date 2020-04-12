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

package com.kdgregory.logging.aws.internal;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.ListRolesRequest;
import com.amazonaws.services.identitymanagement.model.ListRolesResult;
import com.amazonaws.services.identitymanagement.model.Role;


/**
 *  Various static utility functions. These are used by multiple classes and/or
 *  should be tested outside of the class where they're used.
 */
public class Utils
{
    /**
     *  Sleeps until the specified time elapses or the thread is interrupted.
     */
    public static void sleepQuietly(long time)
    {
        try
        {
            Thread.sleep(time);
        }
        catch (InterruptedException ignored)
        {
            // this will simply break to the caller
        }
    }


    /**
     *  Retrieves the current AWS account ID, using reflection so that we don't
     *  have a hard reference to the STS SDK JAR (ie, if you don't want account
     *  IDs you don't need the JAR).
     *  <p>
     *  Returns null if unable to determine the account ID for any reason.
     */
    public static String retrieveAWSAccountId()
    {
        try
        {
            Class<?> stsClientKlass = Class.forName("com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient");
            Class<?> requestKlass = Class.forName("com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest");
            Class<?> responseKlass = Class.forName("com.amazonaws.services.securitytoken.model.GetCallerIdentityResult");
            Object stsClient = stsClientKlass.newInstance();
            Object request = requestKlass.newInstance();
            Method requestMethod = stsClientKlass.getMethod("getCallerIdentity", requestKlass);
            Object response = requestMethod.invoke(stsClient, request);
            Method getAccountMethod = responseKlass.getMethod("getAccount");
            return (String)getAccountMethod.invoke(response);
        }
        catch (Throwable ex)
        {
            // TODO - report exception
            return null;
        }
    }


    /**
     *  Looks up a role ARN given a name. The IAM client is provided for mock testing.
     */
    public static String retrieveRoleArn(String roleName, AmazonIdentityManagement iamClient)
    {
        // if it looks like an ARN already, don't do anything
        if (Pattern.matches("arn:.+:iam::\\d{12}:role/.+", roleName))
            return roleName;

        ListRolesRequest request = new ListRolesRequest();
        ListRolesResult response;
        do
        {
            response = iamClient.listRoles(request);
            request.setMarker(response.getMarker());
            for (Role role : response.getRoles())
            {
                if (role.getRoleName().equals(roleName))
                    return role.getArn();
            }
        } while (response.isTruncated());

        return null;
    }
}
