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

package com.kdgregory.logging.aws.common;

import java.util.HashMap;
import java.util.Map;

import com.kdgregory.logging.aws.internal.clientfactory.BuilderClientFactory;
import com.kdgregory.logging.aws.internal.clientfactory.ConstructorClientFactory;
import com.kdgregory.logging.aws.internal.clientfactory.StaticMethodClientFactory;
import com.kdgregory.logging.common.factories.ClientFactory;
import com.kdgregory.logging.common.factories.ClientFactoryException;
import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  Responsible for constructing an AWS client based on writer configuration.
 *  <p>
 *  The following three approaches are tried, in order:
 *  <ol>
 *  <li> Invoking a configured factory method via reflection.
 *  <li> Invoking the SDK client builder via reflection, if it exists in the
 *       the SDK in use. This option also allows assuming a role.
 *  <li> Invoking the SDK client constructor and configuring it using either a
 *       configured endpoint or the <code>AWS_REGION </code> environment variable.
 *  </ol>
 *
 *  Implementation note: this class defers to individual client factories, which
 *  it creates when constructed. This factories are stored in protected variables,
 *  so that they can be replaced for testing.
 */
public class DefaultClientFactory<AWSClientType>
implements ClientFactory<AWSClientType>
{
    // lookup tables for client constructors and factories
    // these are protected intance variables (rather than static) so that they
    // can be modified for testing

    protected Map<String,String> factoryClasses = new HashMap<String,String>();
    {
        factoryClasses.put("com.amazonaws.services.logs.AWSLogs",           "com.amazonaws.services.logs.AWSLogsClientBuilder");
        factoryClasses.put("com.amazonaws.services.kinesis.AmazonKinesis",  "com.amazonaws.services.kinesis.AmazonKinesisClientBuilder");
        factoryClasses.put("com.amazonaws.services.sns.AmazonSNS",          "com.amazonaws.services.sns.AmazonSNSClientBuilder");
    }

    protected Map<String,String> clientClasses = new HashMap<String,String>();
    {
        clientClasses.put("com.amazonaws.services.logs.AWSLogs",            "com.amazonaws.services.logs.AWSLogsClient");
        clientClasses.put("com.amazonaws.services.kinesis.AmazonKinesis",   "com.amazonaws.services.kinesis.AmazonKinesisClient");
        clientClasses.put("com.amazonaws.services.sns.AmazonSNS",           "com.amazonaws.services.sns.AmazonSNSClient");
    }

    // these are protected so that they can be overridden during testing
    protected ClientFactory<AWSClientType> staticMethodClientFactory;
    protected ClientFactory<AWSClientType> builderClientFactory;
    protected ClientFactory<AWSClientType> constructorClientFactory;


    /**
     *  @param clientType       The AWS client interface type, used for hardcoded selection chains.
     *  @param factoryMethod    Optional: if not-null, specifies a caller-defined static method to
     *                          create the client.
     *  @param assumedRole      Optional: if non-blank, will attempt to assume a role when using the
     *                          client-builder.
     *  @param region           Optional: if non-blank, specifies the desired AWS region for a client
     *                          created either via constructor or SDK builder.
     *  @param endpoint         Optional: if not null, specifies a caller-defined endpoint to apply
     *                          to a client created via the default constructor.
     *  @param logger           Used to log creation events/errors.
     */
    public DefaultClientFactory(Class<AWSClientType> clientType, String factoryMethod, String assumedRole, String region, String endpoint, InternalLogger logger)
    {
        staticMethodClientFactory = new StaticMethodClientFactory<>(clientType, factoryMethod, assumedRole, region, endpoint, logger);
        builderClientFactory = new BuilderClientFactory<>(clientType, factoryClasses.get(clientType.getName()), assumedRole, region, logger);
        constructorClientFactory = new ConstructorClientFactory<>(clientType, clientClasses.get(clientType.getName()), region, endpoint, logger);
    }


    @Override
    public AWSClientType createClient()
    {
        AWSClientType client = staticMethodClientFactory.createClient();
        if (client != null)
            return client;

        client = builderClientFactory.createClient();
        if (client != null)
            return client;

        client = constructorClientFactory.createClient();
        if (client != null)
            return client;

        throw new ClientFactoryException("I don't know how to create client (perhaps you're missing an SDK dependency?)");
    }
}
