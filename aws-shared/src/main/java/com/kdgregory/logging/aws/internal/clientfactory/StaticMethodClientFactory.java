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

package com.kdgregory.logging.aws.internal.clientfactory;

import java.lang.reflect.Method;

import com.kdgregory.logging.aws.internal.Utils;
import com.kdgregory.logging.common.factories.ClientFactory;
import com.kdgregory.logging.common.factories.ClientFactoryException;
import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  Attempts to invoke a provided static factory method to create an AWS client.
 *  <p>
 *  As currently implemented, assumes only one variant of the factory method.
 */
public class StaticMethodClientFactory<ClientType>
implements ClientFactory<ClientType>
{
    // these are provided to constructor
    private Class<ClientType> clientType;
    private String fullyQualifiedMethodName;
    private String assumedRole;
    private String region;
    private String endpoint;
    private InternalLogger logger;


    public StaticMethodClientFactory(
            Class<ClientType> clientType, String fullyQualifiedMethodName,
            String assumedRole, String region, String endpoint,
            InternalLogger logger)
    {
        this.clientType = clientType;
        this.fullyQualifiedMethodName = fullyQualifiedMethodName;
        this.assumedRole = assumedRole;
        this.region = region;
        this.endpoint = endpoint;
        this.logger = logger;
    }


    @Override
    public ClientType createClient()
    {
        if ((fullyQualifiedMethodName == null) || fullyQualifiedMethodName.isEmpty())
            return null;

        logger.debug("creating client via factory method: " + fullyQualifiedMethodName);

        Method factoryMethod;
        try
        {
            factoryMethod = Utils.lookupFactoryMethod(fullyQualifiedMethodName, true);
        }
        catch (Exception ex)
        {
            throw new ClientFactoryException("invalid factory method: " + fullyQualifiedMethodName, ex);
        }

        try
        {
            return (factoryMethod.getParameterTypes().length == 0)
                 ? clientType.cast(factoryMethod.invoke(null))
                 : clientType.cast(factoryMethod.invoke(null, assumedRole, region, endpoint));
        }
        catch (Exception ex)
        {
            throw new ClientFactoryException("factory method error: " + fullyQualifiedMethodName, ex);
        }
    }
}
