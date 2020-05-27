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

package com.kdgregory.logging.aws.internal.retrievers;

import com.amazonaws.util.EC2MetadataUtils;


/**
 *  Retrieves the current region using the EC2 metadata service.
 *  <p>
 *  Beware: this will take a long time and then fail if you're not running
 *  on an EC2 instance.
 */
public class EC2RegionRetriever
{
    public String invoke()
    {
        return EC2MetadataUtils.getEC2InstanceRegion();
    }
}
