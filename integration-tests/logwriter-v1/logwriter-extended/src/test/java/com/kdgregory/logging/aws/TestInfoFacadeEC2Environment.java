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

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.test.StringAsserts;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.Tag;

import com.kdgregory.logging.aws.facade.FacadeFactory;
import com.kdgregory.logging.aws.facade.InfoFacade;


/**
 *  Tests retriever operations that require running on EC2. These are normally
 *  disabled.
 */
public class TestInfoFacadeEC2Environment
{

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
        InfoFacade facade = FacadeFactory.createFacade(InfoFacade.class);

        String instanceId = facade.retrieveEC2InstanceId();

        String tagName = UUID.randomUUID().toString();
        String tagValue = UUID.randomUUID().toString();

        AmazonEC2 client = AmazonEC2ClientBuilder.defaultClient();

        CreateTagsRequest createRequest = new CreateTagsRequest()
                                          .withResources(instanceId)
                                          .withTags(new Tag(tagName, tagValue));
        client.createTags(createRequest);

        Map<String,String> retrievedTags = facade.retrieveEC2Tags(instanceId);
        assertEquals("tag returned", tagValue, retrievedTags.get(tagName));
    }
}
