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

package com.kdgregory.log4j2.aws;

import java.net.URI;
import java.util.UUID;

import static net.sf.kdgcommons.test.StringAsserts.*;

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.ParameterType;

import com.kdgregory.logging.testhelpers.ParameterStoreTestHelper;


public class LookupsIntegrationTest
{
    private Logger localLogger = LoggerFactory.getLogger(getClass());
    private StrSubstitutor strsub;

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    /**
     *  Loads the test-specific Logback configuration and resets the environment.
     */
    public void init(String testName) throws Exception
    {
        MDC.put("testName", testName);
        localLogger.info("starting");

        String propsName = "LookupsIntegrationTest/" + testName + ".xml";
        URI config = ClassLoader.getSystemResource(propsName).toURI();
        assertNotNull("was able to retrieve config", config);

        LoggerContext context = LoggerContext.getContext();
        context.setConfigLocation(config);

        strsub = context.getConfiguration().getStrSubstitutor();

        // must reload after configuration
        localLogger = LoggerFactory.getLogger(getClass());
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testGeneralAWSLookups() throws Exception
    {
        init("testGeneralAWSLookups");

        assertRegex("aws:accountId",                    "\\d{12}",      strsub.replace("${awslogs:aws:accountId}"));
        assertRegex("awsAccountId (deprecated name)",   "\\d{12}",      strsub.replace("${awslogs:awsAccountId}"));
    }


    @Test
    public void testSSMLookups() throws Exception
    {
        init("testGeneralAWSLookups");

        AWSSimpleSystemsManagement ssmClient = AWSSimpleSystemsManagementClientBuilder.defaultClient();
        String paramName = "/LookupsIntegrationTest/testSSMLookups/" + UUID.randomUUID();
        String paramValue = UUID.randomUUID().toString();
        try
        {
            ParameterStoreTestHelper.createParameter(ssmClient, paramName, ParameterType.String, paramValue);
            assertEquals("basic parameter retrieval",       paramValue,                             strsub.replace("${awslogs:ssm:" + paramName + "}"));
            assertEquals("unknown parameter",               "${awslogs:ssm:" + paramName + "x}",    strsub.replace("${awslogs:ssm:" + paramName + "x}"));
            assertEquals("unknown parameter, default value", "default",                             strsub.replace("${awslogs:ssm:" + paramName + "x:default}"));
        }
        finally
        {
            ParameterStoreTestHelper.deleteParameter(ssmClient, paramName);
            ssmClient.shutdown();
        }
    }


    @Test
    @Ignore("only run on EC2")
    public void testEC2AWSLookups() throws Exception
    {
        init("testEC2AWSLookups");

        assertRegex("ec2:instanceId",                   "i-[0-9a-f]+",  strsub.replace("${awslogs:ec2:instanceId}"));
        assertRegex("ec2InstanceId (deprecated name)",  "i-[0-9a-f]+",  strsub.replace("${awslogs:ec2InstanceId}"));

        assertRegex("ec2:region",                       "..-.*-\\d",    strsub.replace("${awslogs:ec2:region}"));
        assertRegex("ec2Region (deprecated name)",      "..-.*-\\d",    strsub.replace("${awslogs:ec2Region}"));
    }
}
