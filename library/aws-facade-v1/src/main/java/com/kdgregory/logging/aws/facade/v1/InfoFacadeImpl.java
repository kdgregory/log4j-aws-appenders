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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.*;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.*;
import com.amazonaws.util.EC2MetadataUtils;

import com.kdgregory.logging.aws.facade.InfoFacade;
import com.kdgregory.logging.common.util.RetryManager;


/**
 *  Provides a facade over various "information" services using the v1 SDK.
 */
public class InfoFacadeImpl
implements InfoFacade
{

    protected RetryManager retryManager = new RetryManager(50, 1000, true);

    private AmazonEC2 ec2Client;
    private AWSSecurityTokenService stsClient;
    private AWSSimpleSystemsManagement ssmClient;

//----------------------------------------------------------------------------
//  InfoFacade implementation
//----------------------------------------------------------------------------

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
    public Map<String,String> retrieveEC2Tags(String instanceId)
    {
        return retryManager.invoke(() -> {
            try
            {
                List<Filter> filters = new ArrayList<>();
                filters.add(new Filter().withName("resource-type").withValues("instance"));
                filters.add(new Filter().withName("resource-id").withValues(instanceId));

                DescribeTagsRequest request = new DescribeTagsRequest().withFilters(filters);
                DescribeTagsResult response = ec2Client().describeTags(request);

                Map<String,String> result = new HashMap<>();
                for (TagDescription desc : response.getTags())
                {
                    result.put(desc.getKey(), desc.getValue());
                }
                return result;
            }
            catch (AmazonServiceException ex)
            {
                // this code determined via experimentation
                if ("RequestLimitExceeded".equals(ex.getErrorCode()))
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

    protected AmazonEC2 ec2Client()
    {
        if (ec2Client == null)
        {
            ec2Client = AmazonEC2ClientBuilder.defaultClient();
        }
        return ec2Client;
    }

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
