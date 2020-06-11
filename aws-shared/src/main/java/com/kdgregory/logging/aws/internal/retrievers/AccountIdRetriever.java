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

/**
 *  Retrieves the callers AWS account ID using STS. Returns null if unable to retrieve
 *  the ID for any reason (generally a missing library or invalid credentials).
 */
public class AccountIdRetriever
extends AbstractReflectionBasedRetriever
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