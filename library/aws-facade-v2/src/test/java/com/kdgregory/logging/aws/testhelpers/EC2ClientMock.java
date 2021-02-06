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

package com.kdgregory.logging.aws.testhelpers;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.kdgcommons.collections.CollectionUtil;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;


/**
 *  Supports mock-object testing of facade code that uses EC2.
 *  <p>
 *  This is a proxy-based mock: you create an instance of the mock, and from it
 *  create an instance of a proxy that implements the client interface. Each of
 *  the supported client methods is implemented in the mock, and called from the
 *  invocation handler. To test specific behaviors, subclasses should override
 *  the method implementation.
 *  <p>
 *  Each method has an associated invocation counter, along with variables that
 *  hold the last set of arguments passed to this method. These variables are
 *  public, to minimize boilerplate code; if testcases modify the variables, they
 *  only hurt themselves.
 *  <p>
 *  The mock is assumed to be invoked from a single thread, so no effort has been
 *  taken to make it threadsafe.
 */
public class EC2ClientMock
implements InvocationHandler
{
    // this map holds the tags that will be returned from a DescribeTags call
    // it must be populated by the testcase
    public Map<String,String> describeTagsValues = new HashMap<>();

    // invocation counts for known methods
    public int describeTagsInvocationCount;

    // information passed to DescribeTags
    public Set<String> describeTagsFilterTypes;
    public String describeTagsResourceType;
    public String describeTagsResourceId;

//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    public Ec2Client createClient()
    {
        return (Ec2Client)Proxy.newProxyInstance(
                                    getClass().getClassLoader(),
                                    new Class<?>[] { Ec2Client.class },
                                    EC2ClientMock.this);
    }

//----------------------------------------------------------------------------
//  Invocation Handler
//----------------------------------------------------------------------------

    /**
     *  The invocation handler; test code should not care about this.
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        String methodName = method.getName();
        if (methodName.equals("describeTags"))
        {
            describeTagsInvocationCount++;
            DescribeTagsRequest request = (DescribeTagsRequest)args[0];
            describeTagsFilterTypes = new HashSet<>();
            describeTagsResourceType = null;
            describeTagsResourceId = null;
            for (Filter filter : request.filters())
            {
                describeTagsFilterTypes.add(filter.name());
                switch (filter.name())
                {
                    case "resource-id":
                        describeTagsResourceId = CollectionUtil.first(filter.values());
                        break;
                    case "resource-type":
                        describeTagsResourceType = CollectionUtil.first(filter.values());
                        break;
                    default:
                        // do nothing
                }
            }
            return describeTags(request);
        }

        // if nothing matches, fall through to here
        System.err.println("invocation handler called unexpectedly: " + methodName);
        throw new IllegalArgumentException("unexpected client call: " + methodName);
    }

//----------------------------------------------------------------------------
//  Default mock implementations
//----------------------------------------------------------------------------

    public DescribeTagsResponse describeTags(DescribeTagsRequest request)
    {
        List<TagDescription> tags = new ArrayList<>();
        for (Map.Entry<String,String> entry : describeTagsValues.entrySet())
        {
            TagDescription desc = TagDescription.builder()
                                  .resourceType(describeTagsResourceType)
                                  .resourceId(describeTagsResourceId)
                                  .key(entry.getKey())
                                  .value(entry.getValue())
                                  .build();
            tags.add(desc);
        }
        return DescribeTagsResponse.builder().tags(tags).build();
    }
}
