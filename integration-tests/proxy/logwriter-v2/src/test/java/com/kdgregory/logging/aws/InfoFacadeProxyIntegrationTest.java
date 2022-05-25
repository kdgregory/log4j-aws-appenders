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

package com.kdgregory.logging.aws;

import java.util.Map;
import java.util.UUID;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

import org.slf4j.MDC;

import com.kdgregory.logging.aws.facade.FacadeFactory;
import com.kdgregory.logging.aws.facade.InfoFacade;
import com.kdgregory.logging.testhelpers.EC2TestHelper;
import com.kdgregory.logging.testhelpers.ParameterStoreTestHelper;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.ParameterType;


/**
 *  Exercises the InfoFacade directly (versus via substitutions).
 *  <p>
 *  This is intended to be run on an EC2 instance in a private subnet, with a
 *  proxy  server running in a public subnet. It accesses EC2 metadata, so will
 *  fail if run elsewhere.
 *  <p>
 *  You must set the COM_KDGREGORY_LOGGING_PROXY_URL  environment variable, and
 *  edit the static variables in {@link AbstractProxyIntegrationTest} to match.
 */
public class InfoFacadeProxyIntegrationTest
extends AbstractProxyIntegrationTest
{
    // "helper" clients; created before any tests, shut down after they're done
    private static Ec2Client ec2Client;
    private static SsmClient ssmClient;

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    {
        ec2Client = configureProxy(Ec2Client.builder()).build();
        ssmClient = configureProxy(SsmClient.builder()).build();
    }


    @Before
    public void setUp()
    {
        // no setup; this is here for completeness
    }


    @After
    public void tearDown()
    {
        localLogger.info("finished");
        MDC.clear();
    }


    @AfterClass
    public static void afterClass()
    {
        ec2Client.close();
        ssmClient.close();
    }

//----------------------------------------------------------------------------
//  Tests
//----------------------------------------------------------------------------

    @Test
    public void testInfoFacadeParameterStore() throws Exception
    {
        final String parameterName = "V1ProxyIntegrationTest-" + System.currentTimeMillis();
        final String parameterValue = "this is a test";

        try {
            ParameterStoreTestHelper.createParameter(ssmClient, parameterName, ParameterType.STRING, parameterValue);

            InfoFacade facade = FacadeFactory.createFacade(InfoFacade.class);
            String value = facade.retrieveParameter(parameterName);
            assertEquals(parameterValue, value);

        }
        finally {
            ParameterStoreTestHelper.deleteParameter(ssmClient, parameterName);
        }
    }


    @Test
    public void testEC2Tags() throws Exception
    {
        final String tagName = UUID.randomUUID().toString();
        final String tagValue = UUID.randomUUID().toString();

        EC2TestHelper testHelper = new EC2TestHelper(ec2Client);
        String instanceId = testHelper.retrieveCurrentInstanceID();
        testHelper.tagInstance(instanceId, tagName, tagValue);

        InfoFacade facade = FacadeFactory.createFacade(InfoFacade.class);

        Map<String,String> retrievedTags = facade.retrieveEC2Tags(instanceId);
        assertEquals("tag returned", tagValue, retrievedTags.get(tagName));
    }
}
