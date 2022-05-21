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

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.util.EC2MetadataUtils;


public class EC2TestHelper
{
    private AmazonEC2 ec2Client;


    public EC2TestHelper(AmazonEC2 ec2Client)
    {
        this.ec2Client = ec2Client;
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
        CreateTagsRequest createRequest = new CreateTagsRequest()
                                          .withResources(instanceId)
                                          .withTags(new Tag(tagName, tagValue));
        ec2Client.createTags(createRequest);
    }

}
