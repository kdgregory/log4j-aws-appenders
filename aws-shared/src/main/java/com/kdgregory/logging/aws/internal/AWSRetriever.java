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
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;


/**
 *  Static utility functions for interacting with AWS. These functions are not
 *  intended to be called frequently: they create a new AWS client for every
 *  call. The information that they retrieve is generally unchanging and can
 *  be cached.
 *  <p>
 *  These functions are implemented using reflection to avoid creating a hard
 *  dependency on libraries that aren't otherwise used. This is implemented
 *  using "retriever" objects that are instantiated once per call (and can be
 *  independently unit-tested).
 *  <p>
 *  To support all SDK release, this code uses client constructors rather than
 *  builders. As long as they're used only to retrieve non-regional data, this
 *  will not be a problem.
 */
public class AWSRetriever
{

    /**
     *  Retrieves the current AWS account ID. Returns null if unable to determine the
     *  account ID for any reason.
     */
    public static String retrieveAccountId()
    {
        return new AccountIdRetriever().invoke();
    }


    /**
     *  Looks up a role ARN given a name.
     */
    public static String retrieveRoleArn(String roleName)
    {
        // bozo check
        if (roleName == null)
            return null;

        // if it looks like an ARN already, don't do anything
        if (Pattern.matches("arn:.+:iam::\\d{12}:role/.+", roleName))
            return roleName;

        return new RoleArnRetriever().invoke(roleName);
    }

//----------------------------------------------------------------------------
//  Handlers
//----------------------------------------------------------------------------

    protected static class AccountIdRetriever
    extends BaseRetriever
    {
        public AccountIdRetriever()
        {
            super("com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient",
                  "com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest",
                  "com.amazonaws.services.securitytoken.model.GetCallerIdentityResult");
        }

        public String invoke()
        {
            Object client = instantiate(clientKlass);
            try
            {
                Object request = instantiate(requestKlass);
                Object response = invokeRequest(client, "getCallerIdentity", request);
                return getResponseValue(response, "getAccount", String.class);
            }
            finally
            {
                shutdown(client);
            }
        }
    }


    protected static class RoleArnRetriever
    extends BaseRetriever
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
            Object client = instantiate(clientKlass);
            try
            {
                Object request = instantiate(requestKlass);
                Object response;
                do
                {
                    response = invokeRequest(client, "listRoles", request);
                    String marker = getResponseValue(response, "getMarker", String.class);
                    setRequestValue(request, "setMarker", String.class, marker);
                    List<Object> roles = getResponseValue(response, "getRoles", List.class);
                    if (roles == null) roles = Collections.emptyList(); // should not be needed
                    for (Object role : roles)
                    {
                        String roleArn = getValue(role, roleKlass, "getArn", String.class);
                        if (roleName.equals(getValue(role, roleKlass, "getRoleName", String.class)))
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


    /**
     *  Base retriever: performs all of the reflection operations, with try-catch
     *  blocks. Ayn exception or null result anywhere in the process will cause
     *  subsequent operations to abort.
     */
    protected static class BaseRetriever
    {
        protected Throwable exception;
        protected Class<?> clientKlass;
        protected Class<?> requestKlass;
        protected Class<?> responseKlass;

        protected BaseRetriever(String clientClassName, String requestClassName, String responseClassName)
        {
            clientKlass = loadClass(clientClassName);
            requestKlass = loadClass(requestClassName);
            responseKlass = loadClass(responseClassName);
        }


        protected Class<?> loadClass(String className)
        {
            try
            {
                return Class.forName(className);
            }
            catch (Throwable ex)
            {
                exception = ex;
                return null;
            }
        }


        protected Object instantiate(Class<?> klass)
        {
            if ((exception != null) || (klass == null))
                return null;

            try
            {
                return klass.newInstance();
            }
            catch (Throwable ex)
            {
                exception = ex;
                return null;
            }
        }


        protected Object invokeRequest(Object client, String methodName, Object value)
        {
            if ((exception != null) || (client == null))
                return null;

            try
            {
                Method method = clientKlass.getMethod(methodName, requestKlass);
                return method.invoke(client, value);
            }
            catch (Throwable ex)
            {
                exception = ex;
                return null;
            }
        }


        protected void setRequestValue(Object request, String methodName, Class<?> valueKlass, Object value)
        {
            if ((exception != null) || (request == null))
                return;

            try
            {
                Method method = requestKlass.getMethod(methodName, valueKlass);
                method.invoke(request, value);
            }
            catch (Throwable ex)
            {
                exception = ex;
            }
        }


        protected <T> T getResponseValue(Object response, String methodName, Class<T> resultKlass)
        {
            return getValue(response, responseKlass, methodName, resultKlass);
        }


        protected <T> T getValue(Object obj, Class<?> objKlass, String methodName, Class<T> resultKlass)
        {
            if ((exception != null) || (obj == null))
                return null;

            try
            {
                Method method = objKlass.getMethod(methodName);
                return resultKlass.cast(method.invoke(obj));
            }
            catch (Throwable ex)
            {
                exception = ex;
                return null;
            }
        }


        protected void shutdown(Object client)
        {
            // note: if we have a client we want to shut it down, even if an exception has happened
            if (client == null)
                return;

            try
            {
                Method method = clientKlass.getMethod("shutdown");
                method.invoke(client);
            }
            catch (Throwable ex)
            {
                // ignored: at this point we don't care about exceptions because we've got a value
            }
        }
    }
}
