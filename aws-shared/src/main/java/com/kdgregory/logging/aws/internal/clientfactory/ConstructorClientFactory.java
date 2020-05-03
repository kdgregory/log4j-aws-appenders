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

import com.amazonaws.regions.Regions;

import com.kdgregory.logging.aws.internal.Utils;
import com.kdgregory.logging.common.factories.ClientFactory;
import com.kdgregory.logging.common.factories.ClientFactoryException;
import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  Invokes an explicit client constructor to produce an AWS client.
 */
public class ConstructorClientFactory<ClientType>
implements ClientFactory<ClientType>
{
    private Class<ClientType> clientType;
    private Class<?> clientClass;
    private String region;
    private String endpoint;
    private InternalLogger logger;


    public ConstructorClientFactory(
            Class<ClientType> clientType, String clientClassName,
            String region, String endpoint,
            InternalLogger logger)
    {
        this.clientType = clientType;
        this.region = region;
        this.endpoint = endpoint;
        this.logger = logger;

        clientClass = Utils.loadClass(clientClassName);
    }


    @Override
    public ClientType createClient()
    {
        logger.debug("creating client via constructor");

        ClientType client = invokeConstructor();

        if (maybeSetEndpoint(client))
            return client;

        maybeSetRegion(client);
        return client;
    }


    protected ClientType invokeConstructor()
    {
        try
        {
            return clientType.cast(clientClass.newInstance());
        }
        catch (Exception ex)
        {
            throw new ClientFactoryException("failed to instantiate service client: " + clientClass, ex);
        }
    }


    protected boolean maybeSetEndpoint(ClientType client)
    {
        if ((endpoint == null) || (endpoint.isEmpty()))
            return false;

        try
        {
            logger.debug("setting endpoint: " + endpoint);
            Utils.invokeSetter(client, "setEndpoint", String.class, endpoint);
            return true;
        }
        catch (Throwable ex)
        {
            throw new ClientFactoryException("failed to set endpoint: " + endpoint, ex);
        }
    }


    protected boolean maybeSetRegion(ClientType client)
    {
        String regionName = (region != null)
                          ? region
                          :  System.getenv("AWS_REGION");
        if ((regionName == null) || (regionName.isEmpty()))
            return false;

        logger.debug("setting region: " + regionName);
        try
        {
            Regions resolvedRegion = Regions.fromName(regionName);
            Utils.invokeSetter(client, "configureRegion", Regions.class, resolvedRegion);
            return true;
        }
        catch (Throwable ex)
        {
            throw new ClientFactoryException("failed to set region: " + regionName);
        }
    }
}
