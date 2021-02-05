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

package com.kdgregory.logging.aws.facade;

import java.util.Map;

/**
 *  A facade for various operations that retrieve information about the
 *  AWS environment. 
 *  <p>
 *  Unlike the service-level facades, these operations are "best effort": if 
 *  unable to perform the operation, the implementation is permitted to return 
 *  a default value (typically null).
 *  <p>
 *  Also unlike service-level facades, implemtations are expected to retry if
 *  throttled.
 *  <p>
 *  Finally, these operations use the default client for whatever service they
 *  invoke. They do not attempt to assume a role or connect to an alternate
 *  region.
 */
public interface InfoFacade
{
    /**
     *  Retrieves the current AWS account ID, "unknown" if unable to determine
     *  the account ID for any reason.
     *  <p>
     *  Requires the STS SDK.
     */
    String retrieveAccountId();


    /**
     *  Retrieves the current configured region, using the default region provider.
     */
    String retrieveDefaultRegion();


    /**
     *  Returns the current instance ID, using the EC2 metadata service. This will only
     *  work valid when running on an EC2 instance, and may take a long time to return
     *  if running elsewhere.
     */
    String retrieveEC2InstanceId();


    /**
     *  Returns the instance where the logger is running, using the EC2 metadata service.
     *  This will only work valid when running on an EC2 instance, and may take a long time
     *  to return if running elsewhere.
     *  <p>
     *  See {@link #retrieveRegion}, which uses the default region provider.
     */
    String retrieveEC2Region();
    
    
    /**
     *  Returns all tags for the specified EC2 instance. Returns an empty map if unable to
     *  retrieve these tags for any reason (eg, invalid instance ID, permission denied).
     */
    Map<String,String> retrieveEC2Tags(String instanceId);


    /**
     *  Retrieves a named Systems Manager parameter, null if it doesnt' exist or is a
     *  secure string.
     *  <p>
     *  If throttled, this method will retry up to 4 times before returning null. Other
     *  exceptions are silently caught, and return null.
     *  <p>
     *  Requires the Systems Manager SDK.
     */
    String retrieveParameter(String parameterName);
}
