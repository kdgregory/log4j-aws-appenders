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

package com.kdgregory.logging.aws.facade.v2;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.*;

import java.util.Map;

import com.kdgregory.logging.aws.facade.InfoFacade;
import com.kdgregory.logging.common.util.RetryManager;


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
            GetCallerIdentityRequest request = GetCallerIdentityRequest.builder().build();
            GetCallerIdentityResponse response = stsClient().getCallerIdentity(request);
            return response.account();
        }
        catch (Exception ignored)
        {
            return "unknown";
        }
    }


    @Override
    public String retrieveDefaultRegion()
    {
        return new DefaultAwsRegionProviderChain().getRegion().id();
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
    public Map<String,String> retrieveEC2Tags(String instanceId)
    {
        throw new UnsupportedOperationException("FIXME - implement");
    }


    @Override
    public String retrieveParameter(String parameterName)
    {
        try
        {
            GetParameterResponse result = retryManager.invoke(() -> {
                try
                {
                    GetParameterRequest request = GetParameterRequest.builder().name(parameterName).build();
                    return ssmClient().getParameter(request);
                }
                catch (SsmException ex)
                {
                    AwsErrorDetails errorDetails = ex.awsErrorDetails();
                    if ((errorDetails != null) && "ThrottlingException".equals(errorDetails.errorCode()))
                        return null;
                    else
                        throw ex;
                }
            });
            return (result.parameter().type() == ParameterType.SECURE_STRING)
                 ? null
                 : result.parameter().value();
        }
        catch (Exception ex)
        {
            return null;
        }
    }

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    private StsClient stsClient;
    private SsmClient ssmClient;

    protected RetryManager retryManager = new RetryManager(50, 1000, true);


    protected StsClient stsClient()
    {
        if (stsClient == null)
        {
            stsClient = StsClient.builder().build();
        }
        return stsClient;
    }


    protected SsmClient ssmClient()
    {
        if (ssmClient == null)
        {
            ssmClient = SsmClient.builder().build();
        }
        return ssmClient;
    }
}
