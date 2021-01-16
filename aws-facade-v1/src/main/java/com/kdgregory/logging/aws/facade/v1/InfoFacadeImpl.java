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

package com.kdgregory.logging.aws.facade.v1;

import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.*;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.*;
import com.amazonaws.util.EC2MetadataUtils;

import com.kdgregory.logging.aws.internal.RetryManager;
import com.kdgregory.logging.aws.internal.facade.InfoFacade;


/**
 *  Provides a facade over various "information" services using the v1 SDK.
 */
public class InfoFacadeImpl
implements InfoFacade
{
    @Override
    public String retrieveAccountId()
    {
        try
        {
            GetCallerIdentityRequest request = new GetCallerIdentityRequest();
            GetCallerIdentityResult response = stsClient().getCallerIdentity(request);
            return response.getAccount();
        }
        catch (Exception ignored)
        {
            return "unknown";
        }
    }


    @Override
    public String retrieveDefaultRegion()
    {
        return new DefaultAwsRegionProviderChain().getRegion();
    }


    @Override
    public String retrieveEC2InstanceId()
    {
        return EC2MetadataUtils.getInstanceId();
    }


    @Override
    public String retrieveEC2Region()
    {
        return EC2MetadataUtils.getEC2InstanceRegion();
    }


    @Override
    public String retrieveParameter(String parameterName)
    {
        try
        {
            GetParameterResult result = retryManager.invoke(() -> {
                try
                {
                    GetParameterRequest request = new GetParameterRequest().withName(parameterName);
                    return ssmClient().getParameter(request);
                }
                catch (AWSSimpleSystemsManagementException ex)
                {
                    if ("ThrottlingException".equals(ex.getErrorCode()))
                        return null;
                    else
                        throw ex;
                }
            });
            return result.getParameter().getType().equals(ParameterType.SecureString.name())
                 ? null
                 : result.getParameter().getValue();
        }
        catch (Exception ex)
        {
            return null;
        }
    }

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    private AWSSecurityTokenService stsClient;
    private AWSSimpleSystemsManagement ssmClient;

    protected RetryManager retryManager = new RetryManager(50, 1000, true);


    protected AWSSecurityTokenService stsClient()
    {
        if (stsClient == null)
        {
            stsClient = AWSSecurityTokenServiceClientBuilder.defaultClient();
        }
        return stsClient;
    }


    protected AWSSimpleSystemsManagement ssmClient()
    {
        if (ssmClient == null)
        {
            ssmClient = AWSSimpleSystemsManagementClientBuilder.defaultClient();
        }
        return ssmClient;
    }
}
