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

package com.kdgregory.logging.testhelpers;

import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest;
import software.amazon.awssdk.services.ec2.model.Tag;


public class EC2TestHelper
{
    private Ec2Client client;


    public EC2TestHelper(Ec2Client ec2Client)
    {
        this.client = ec2Client;
    }


    /**
     *  Returns the current instance's ID (will fail if not running on EC2).
     */
    public String retrieveCurrentInstanceID()
    {
        return EC2MetadataUtils.getInstanceId();
    }


    /**
     *  Tags the specified instance.
     */
    public void tagInstance(String instanceId, String tagName, String tagValue)
    {
        CreateTagsRequest createRequest = CreateTagsRequest.builder()
                                          .resources(instanceId)
                                          .tags(Tag.builder().key(tagName).value(tagValue).build())
                                          .build();
        client.createTags(createRequest);
    }
}
