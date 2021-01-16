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

import java.util.HashMap;
import java.util.Map;

import com.kdgregory.logging.aws.internal.facade.InfoFacade;


/**
 *  Simple mock-object, used for testing Substitutions. Rather than go through any
 *  sort of proxy operation, or even much configuration, all of the returned values
 *  are configured as public member variables.
 */
public class MockInfoFacade
implements InfoFacade
{
    public String accountId;
    public String defaultRegion;
    public String ec2InstanceId;
    public String ec2Region;
    public Map<String,String> parameterValues = new HashMap<>();


    @Override
    public String retrieveAccountId()
    {
        return accountId;
    }

    @Override
    public String retrieveDefaultRegion()
    {
        return defaultRegion;
    }

    @Override
    public String retrieveEC2InstanceId()
    {
        return ec2InstanceId;
    }

    @Override
    public String retrieveEC2Region()
    {
        return ec2Region;
    }

    @Override
    public String retrieveParameter(String parameterName)
    {
        return parameterValues.get(parameterName);
    }

}
