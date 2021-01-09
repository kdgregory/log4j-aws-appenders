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

package com.kdgregory.logging.aws.internal.facade;


/**
 *  A facade for various read-only operations. Unlike the service-level
 *  facades, these operations are "best effort": if unable to perform
 *  the operation, the implementation is permitted to return a default
 *  value (although they are expected to retry if throttled).
 *  <p>
 *  Note also that these operations use the default service client.
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
