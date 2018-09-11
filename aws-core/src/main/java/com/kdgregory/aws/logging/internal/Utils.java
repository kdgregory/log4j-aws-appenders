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

package com.kdgregory.aws.logging.internal;

import java.lang.reflect.Method;


/**
 *  Various static utility functions. Most are copied from KDGCommons, to avoid
 *  potential dependency conflicts.
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
}
