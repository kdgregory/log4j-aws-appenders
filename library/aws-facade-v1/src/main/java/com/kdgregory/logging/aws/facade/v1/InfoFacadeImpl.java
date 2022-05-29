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
import com.kdgregory.logging.aws.facade.v1.internal.ClientBuilderUtils;
import com.kdgregory.logging.common.util.ProxyUrl;
import com.kdgregory.logging.common.util.RetryManager;


/**
 *  Provides a facade over various "information" services using the v1 SDK.
 */
public class InfoFacadeImpl
implements InfoFacade
{

    protected RetryManager retryManager = new RetryManager(50, 1000, true);

    private EC2ClientWrapper ec2ClientWrapper;
    private STSClientWrapper stsClientWrapper;
    private SSMClientWrapper ssmClientWrapper;

//----------------------------------------------------------------------------
//  InfoFacade implementation
//----------------------------------------------------------------------------

    @Override
    public String retrieveAccountId()
    {
        try
        {
            if (stsClientWrapper == null)
            {
                stsClientWrapper = new STSClientWrapper();
            }
            GetCallerIdentityRequest request = new GetCallerIdentityRequest();
            GetCallerIdentityResult response = stsClientWrapper.client().getCallerIdentity(request);
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
        try
        {
            if (ec2ClientWrapper == null)
            {
                ec2ClientWrapper = new EC2ClientWrapper();
            }
        }
        catch (Exception ignored)
        {
            return new HashMap<>();
        }

        return retryManager.invoke(() -> {
            try
            {
                List<Filter> filters = new ArrayList<>();
                filters.add(new Filter().withName("resource-type").withValues("instance"));
                filters.add(new Filter().withName("resource-id").withValues(instanceId));

                DescribeTagsRequest request = new DescribeTagsRequest().withFilters(filters);
                DescribeTagsResult response = ec2ClientWrapper.client().describeTags(request);

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
            if (ssmClientWrapper == null)
            {
                ssmClientWrapper = new SSMClientWrapper();
            }

            GetParameterResult result = retryManager.invoke(() -> {
                try
                {
                    GetParameterRequest request = new GetParameterRequest().withName(parameterName);
                    return ssmClientWrapper.client().getParameter(request);
                }
                catch (AmazonServiceException ex)
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
//  Wrappers for AWS clients. These exist to avoid creating hard dependencies
//  to libraries that a user might not care about.
//----------------------------------------------------------------------------

    private static class EC2ClientWrapper
    {
        private AmazonEC2 client;

        public EC2ClientWrapper()
        {
            AmazonEC2ClientBuilder clientBuilder = AmazonEC2ClientBuilder.standard();
            ClientBuilderUtils.optSetProxy(clientBuilder, new ProxyUrl());
            client = clientBuilder.build();
        }

        public AmazonEC2 client()
        {
            return client;
        }
    }


    private static class STSClientWrapper
    {
        private AWSSecurityTokenService client;

        public STSClientWrapper()
        {
            AWSSecurityTokenServiceClientBuilder clientBuilder = AWSSecurityTokenServiceClientBuilder.standard();
            ClientBuilderUtils.optSetProxy(clientBuilder, new ProxyUrl());
            client = clientBuilder.build();
        }

        public AWSSecurityTokenService client()
        {
            return client;
        }
    }


    private static class SSMClientWrapper
    {
        private AWSSimpleSystemsManagement client;

        public SSMClientWrapper()
        {
            AWSSimpleSystemsManagementClientBuilder clientBuilder = AWSSimpleSystemsManagementClientBuilder.standard();
            ClientBuilderUtils.optSetProxy(clientBuilder, new ProxyUrl());
            client = clientBuilder.build();
        }

        public AWSSimpleSystemsManagement client()
        {
            return client;
        }
    }
}
