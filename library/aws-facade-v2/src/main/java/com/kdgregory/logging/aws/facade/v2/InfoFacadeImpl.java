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
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils;
import software.amazon.awssdk.regions.providers.DefaultAwsRegionProviderChain;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.kdgregory.logging.aws.facade.InfoFacade;
import com.kdgregory.logging.common.util.RetryManager2;


/**
 *  Provides a facade over various "information" services using the v1 SDK.
 */
public class InfoFacadeImpl
implements InfoFacade
{
    private Ec2Client ec2Client;
    private StsClient stsClient;
    private SsmClient ssmClient;

    // these control retries for retrieving EC2 instance tags
    protected Duration retrieveTagsTimeout = Duration.ofMillis(1000);
    protected RetryManager2 retrieveTagsRetry = new RetryManager2("describeTags", Duration.ofMillis(50));

    // these control retries for retrieving from Parameter Store
    protected Duration getParameterTimeout = Duration.ofMillis(1000);
    protected RetryManager2 getParameterRetry = new RetryManager2("getParameter", Duration.ofMillis(50));

//----------------------------------------------------------------------------
//  InfoFacade implementation
//----------------------------------------------------------------------------

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
        return retrieveTagsRetry.invoke(retrieveTagsTimeout, () -> {
            try
            {
                List<Filter> filters = new ArrayList<>();
                filters.add(Filter.builder().name("resource-type").values("instance").build());
                filters.add(Filter.builder().name("resource-id").values(instanceId).build());

                DescribeTagsRequest request = DescribeTagsRequest.builder().filters(filters).build();
                DescribeTagsResponse response = ec2Client().describeTags(request);

                Map<String,String> result = new HashMap<>();
                for (TagDescription desc : response.tags())
                {
                    result.put(desc.key(), desc.value());
                }
                return result;
            }
            catch (AwsServiceException ex)
            {
                // this code determined via experimentation
                if ("RequestLimitExceeded".equals(ex.awsErrorDetails().errorCode()))
                    return null;
                return new HashMap<>();
            }
            catch (Exception ignored)
            {
                return new HashMap<>();
            }
        });
    }


    @Override
    public String retrieveParameter(String parameterName)
    {
        try
        {
            GetParameterResponse result = getParameterRetry.invoke(getParameterTimeout, () -> {
                try
                {
                    GetParameterRequest request = GetParameterRequest.builder().name(parameterName).build();
                    return ssmClient().getParameter(request);
                }
                catch (AwsServiceException ex)
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

    protected Ec2Client ec2Client()
    {
        if (ec2Client == null)
        {
            ec2Client = Ec2Client.builder().build();
        }
        return ec2Client;
    }

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
