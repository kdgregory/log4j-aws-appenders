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

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

import static net.sf.kdgcommons.test.StringAsserts.*;

import java.util.Map;

import com.kdgregory.logging.aws.facade.InfoFacade;
import com.kdgregory.logging.aws.testhelpers.EC2ClientMock;
import com.kdgregory.logging.aws.testhelpers.SSMClientMock;
import com.kdgregory.logging.aws.testhelpers.STSClientMock;
import com.kdgregory.logging.common.util.RetryManager;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.*;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.*;


public class TestInfoFacadeImpl
{
    // used for tests that involve retries
    private final static long RETRY_TIMEOUT_MS = 250;

    // update any of these mocks inside the test, before invoking facade methods
    private EC2ClientMock ec2Mock;
    private STSClientMock stsMock;
    private SSMClientMock ssmMock;

    // use this facade implementation for testing unless you want to call AWS
    private InfoFacade facade = new InfoFacadeImpl()
    {
        {
            // don't waste time on retry timeouts!
            retryManager = new RetryManager(50, RETRY_TIMEOUT_MS, false);
        }

        @Override
        protected Ec2Client ec2Client()
        {
            return ec2Mock.createClient();
        }

        @Override
        protected StsClient stsClient()
        {
            return stsMock.createClient();
        }

        @Override
        protected SsmClient ssmClient()
        {
            return ssmMock.createClient();
        }
    };

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testRetrieveAccountIdHappyPath() throws Exception
    {
        stsMock = new STSClientMock();

        assertEquals("returned expected value", STSClientMock.DEFAULT_ACCOUNT_ID, facade.retrieveAccountId());
    }


    @Test
    public void testRetrieveAccountIdException() throws Exception
    {
        stsMock = new STSClientMock()
        {
            @Override
            public GetCallerIdentityResponse getCallerIdentity(GetCallerIdentityRequest request)
            {
                throw new RuntimeException("any exception will do");
            }
        };

        assertEquals("returned expected value", "unknown", facade.retrieveAccountId());
    }


    @Test
    @Ignore("will fail if you're not configured like I am")
    public void testRetrieveDefaultRegion() throws Exception
    {
        assertEquals("returned default", "us-east-1", facade.retrieveDefaultRegion());
    }


    @Test
    @Ignore("will fail if you're not running on EC2")
    public void testRetrieveEC2InstanceId() throws Exception
    {
        String instanceId = facade.retrieveEC2InstanceId();

        // note: I think that these IDs are limited to hex digits, but
        // https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/resource-ids.html
        // does not offer any such restriction

        assertRegex("returned a reasonable ID (was: " + instanceId + ")",
                    "i-[0-9A-Za-z]+",
                    instanceId);
    }


    @Test
    @Ignore("will fail if you're not running on EC2")
    public void testRetrieveEC2InstanceRegion() throws Exception
    {
        assertEquals("returned expected region", "us-east-1", facade.retrieveEC2Region());
    }


    @Test
    public void testRetrieveEC2Tags() throws Exception
    {
        ec2Mock = new EC2ClientMock();
        ec2Mock.describeTagsValues.put("foo", "bar");
        ec2Mock.describeTagsValues.put("ARGLE", "BARGLE");

        String testInstanceId = "i-12345678";

        Map<String,String> result = facade.retrieveEC2Tags(testInstanceId);

        assertEquals("returned tags",                   ec2Mock.describeTagsValues, result);
        assertEquals("requested correct resource type", "instance",                 ec2Mock.describeTagsResourceType);
        assertEquals("passed instance ID to client",    testInstanceId,             ec2Mock.describeTagsResourceId);
    }


    @Test
    public void testRetrieveEC2TagsThrottled() throws Exception
    {
        ec2Mock = new EC2ClientMock()
        {
            @Override
            public DescribeTagsResponse describeTags(DescribeTagsRequest request)
            {
                if (describeTagsInvocationCount == 1)
                {
                    // these settings determined via experimentation
                    Ec2Exception ex = (Ec2Exception)Ec2Exception.builder()
                                      .message("Request limit exceeded")
                                      .awsErrorDetails(AwsErrorDetails.builder().errorCode("RequestLimitExceeded").build())
                                      .statusCode(503)
                                      .build();
                    throw ex;
                }
                else
                {
                    return super.describeTags(request);
                }
            }
        };
        ec2Mock.describeTagsValues.put("foo", "bar");
        ec2Mock.describeTagsValues.put("ARGLE", "BARGLE");

        String testInstanceId = "i-12345678";

        Map<String,String> result = facade.retrieveEC2Tags(testInstanceId);

        assertEquals("returned tags",                   ec2Mock.describeTagsValues, result);
        assertEquals("requested correct resource type", "instance",                 ec2Mock.describeTagsResourceType);
        assertEquals("passed instance ID to client",    testInstanceId,             ec2Mock.describeTagsResourceId);
    }


    @Test
    public void testRetrieveEC2TagsException() throws Exception
    {
        ec2Mock = new EC2ClientMock()
        {
            @Override
            public DescribeTagsResponse describeTags(DescribeTagsRequest request)
            {
                throw new RuntimeException("irrelevant");
            }
        };

        Map<String,String> result = facade.retrieveEC2Tags("i-12345678");

        assertTrue("returned empty map",                result.isEmpty());
    }


    @Test
    public void testRetrieveParameterHappyPath() throws Exception
    {
        ssmMock = new SSMClientMock("test", "value");

        assertEquals("returned expected value",     "value",    facade.retrieveParameter("test"));
        assertEquals("invocation count",            1,          ssmMock.getParameterInvocationCount);
        assertEquals("provided name",               "test",     ssmMock.getParameterName);
    }


    @Test
    public void testRetrieveParameterThatDoesntExist() throws Exception
    {
        ssmMock = new SSMClientMock("test", "value");

        assertEquals("returned null",               null,       facade.retrieveParameter("bogus"));
        assertEquals("invocation count",            1,          ssmMock.getParameterInvocationCount);
        assertEquals("provided name",               "bogus",    ssmMock.getParameterName);
    }


    @Test
    public void testRetrieveParameterSecureString() throws Exception
    {
        ssmMock = new SSMClientMock("test", "value")
        {
            @Override
            public GetParameterResponse getParameter(GetParameterRequest request)
            {
                Parameter oldParam = super.getParameter(request).parameter();
                Parameter newParam = Parameter.builder()
                                  .name(oldParam.name())
                                  .type(ParameterType.SECURE_STRING)
                                  .value(oldParam.value())
                                  .build();
                return GetParameterResponse.builder().parameter(newParam).build();
            }
        };

        assertEquals("returned null",               null,       facade.retrieveParameter("test"));
        assertEquals("invocation count",            1,          ssmMock.getParameterInvocationCount);
        assertEquals("provided name",               "test",     ssmMock.getParameterName);
    }


    @Test
    public void testRetrieveParameterThrottling() throws Exception
    {
        ssmMock = new SSMClientMock("test", "value")
        {
            @Override
            public GetParameterResponse getParameter(GetParameterRequest request)
            {
                if (getParameterInvocationCount < 2)
                {
                    // exception type and error code determined through experimentation
                    AwsErrorDetails details = AwsErrorDetails.builder()
                                              .errorCode("ThrottlingException")
                                              .build();
                    throw SsmException.builder().awsErrorDetails(details).build();
                }
                else
                {
                    return super.getParameter(request);
                }
            }
        };

        assertEquals("returned value",              "value",    facade.retrieveParameter("test"));
        assertEquals("invocation count",            2,          ssmMock.getParameterInvocationCount);
    }


    @Test
    public void testRetrieveParameterThrottlingTimeout() throws Exception
    {
        ssmMock = new SSMClientMock("test", "value")
        {
            @Override
            public GetParameterResponse getParameter(GetParameterRequest request)
            {
                // exception type and error code determined through experimentation
                AwsErrorDetails details = AwsErrorDetails.builder()
                                          .errorCode("ThrottlingException")
                                          .build();
                throw SsmException.builder().awsErrorDetails(details).build();
            }
        };

        // I'm measuring execution time, because at the scale of this test Java8 lambda
        // lambda time is signficant; I'm also measuring minimum execution time because
        // I've found that my laptop doesn't execute quickly enough for a range test

        long start = System.currentTimeMillis();
        Object value = facade.retrieveParameter("test");
        long elapsed = System.currentTimeMillis() - start;

        assertNull("returned null",                         value);
        assertTrue("SDK was invoked multiple times",        ssmMock.getParameterInvocationCount > 1);
        assertTrue("elapsed time (was: " + elapsed + ")",   elapsed >= RETRY_TIMEOUT_MS);
    }


    @Test
    public void testRetrieveParameterException() throws Exception
    {
        ssmMock = new SSMClientMock("test", "value")
        {
            @Override
            public GetParameterResponse getParameter(GetParameterRequest request)
            {
                throw new RuntimeException("irrelevant");
            }
        };

        assertEquals("returned null",               null,       facade.retrieveParameter("test"));
        assertEquals("invocation count",            1,          ssmMock.getParameterInvocationCount);
    }
}
