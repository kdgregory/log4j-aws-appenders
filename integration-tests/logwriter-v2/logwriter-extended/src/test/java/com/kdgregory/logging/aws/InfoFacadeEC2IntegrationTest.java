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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.test.StringAsserts;

import software.amazon.awssdk.services.ec2.Ec2Client;

import com.kdgregory.logging.aws.facade.FacadeFactory;
import com.kdgregory.logging.aws.facade.InfoFacade;
import com.kdgregory.logging.testhelpers.EC2TestHelper;


/**
 *  Tests retriever operations that require running on EC2. These are normally
 *  disabled.
 */
public class InfoFacadeEC2IntegrationTest
{
    private static Ec2Client ec2Client;

//----------------------------------------------------------------------------
//  JUnit Scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void beforeClass()
    {
        ec2Client = Ec2Client.builder().build();
    }


    @AfterClass
    public static void afterClass()
    {
        ec2Client.close();
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    @Ignore
    public void testInstanceId() throws Exception
    {
        InfoFacade facade = FacadeFactory.createFacade(InfoFacade.class);
        String value = facade.retrieveEC2InstanceId();

        StringAsserts.assertRegex("retrieved value (was: " + value + ")",
                                  "i-.*",
                                  value);
    }


    @Test
    @Ignore
    public void testRegion() throws Exception
    {
        InfoFacade facade = FacadeFactory.createFacade(InfoFacade.class);
        String value = facade.retrieveEC2Region();

        // rather than tie this to my default region, I'll see if it "looks right"
        StringAsserts.assertRegex("retrieved value (was: " + value + ")",
                                  "..-.*-\\d",
                                  value);
    }


    @Test
    @Ignore
    public void testTags() throws Exception
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
