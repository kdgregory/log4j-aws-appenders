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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.regions.Regions;

import com.kdgregory.logging.common.factories.ClientFactory;
import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  Responsible for constructing an AWS client based on writer configuration.
 *  <p>
 *  The following three approaches are tried, in order:
 *  <ol>
 *  <li> Invoking a configured factory method via reflection.
 *  <li> Invoking the SDK default client builder via reflection, if it exists in
 *       the version of the SDK in use.
 *  <li> Invoking the SDK client constructor and configuring it using either a
 *       configured endpoint or the <code>AWS_REGION </code> environment variable.
 *  </ol>
 */
public class DefaultClientFactory<AWSClientType>
implements ClientFactory<AWSClientType>
{
    private static Map<String,String> sdkFactoryNames = new HashMap<String,String>();
    private static Map<String,String> clientClasses = new HashMap<String,String>();
    static
    {
        sdkFactoryNames.put("com.amazonaws.services.logs.AWSLogs",          "com.amazonaws.services.logs.AWSLogsClientBuilder.defaultClient");
        sdkFactoryNames.put("com.amazonaws.services.kinesis.AmazonKinesis", "com.amazonaws.services.kinesis.AmazonKinesisClientBuilder.defaultClient");
        sdkFactoryNames.put("com.amazonaws.services.sns.AmazonSNS",         "com.amazonaws.services.sns.AmazonSNSClientBuilder.defaultClient");

        clientClasses.put("com.amazonaws.services.logs.AWSLogs",          "com.amazonaws.services.logs.AWSLogsClient");
        clientClasses.put("com.amazonaws.services.kinesis.AmazonKinesis", "com.amazonaws.services.kinesis.AmazonKinesisClient");
        clientClasses.put("com.amazonaws.services.sns.AmazonSNS",         "com.amazonaws.services.sns.AmazonSNSClient");
    }

    private Class<AWSClientType> clientType;
    private String factoryMethodName;
    private String endpoint;
    private InternalLogger logger;


    /**
     *  @param clientType       The AWS client interface type, used for hardcoded selection chains.
     *  @param factoryMethod    Optional: if not-null, specifies a caller-defined static method to
     *                          create the client.
     *  @param endpoint         Optional: if not null, specifies a caller-defined endpoint to apply
     *                          to a client created via the default constructor.
     *  @param logger           Used to log creation events/errors.
     */
    public DefaultClientFactory(Class<AWSClientType> clientType, String factoryMethod, String endpoint, InternalLogger logger)
    {
        this.clientType = clientType;
        this.factoryMethodName = factoryMethod;
        this.endpoint = endpoint;
        this.logger = logger;
    }


    @Override
    public AWSClientType createClient()
    {
        AWSClientType client = tryClientFactory(factoryMethodName, true);
        if (client != null)
            return client;

        client = tryClientFactory(sdkFactoryNames.get(clientType.getName()), false);
        if (client != null)
            return client;

        client = clientType.cast(
                    tryConfigureEndpoint(
                        createViaConstructor()));
        return client;
    }


    /**
     *  Attempts to invoke the named factory method. No-op if passed empty or null.
     *  This is called for both the configured factory method and th SDK factory.
     *
     *  @param  factoryName     The fully-qualified name of the factory method (eg:
     *                          <code>com.example.MyClass.myMethod</code>).
     *  @param  rethrow         If true, any reflection exceptions will be rethrown,
     *                          wrapped in a <code>RuntimeException</code>; if false,
     *                          they are ignored and the function returns null. The
     *                          former is appropriate for configured factory methods,
     *                          the latter for the SDK method (which might not exist).
     */
    private AWSClientType tryClientFactory(String factoryName, boolean rethrow)
    {
        if ((factoryName == null) || factoryName.isEmpty())
            return null;

        int methodIdx = factoryName.lastIndexOf('.');
        if (methodIdx < 0)
            throw new IllegalArgumentException("invalid client factory: " + factoryName);

        try
        {
            Class<?> factoryKlass = Class.forName(factoryName.substring(0, methodIdx));
            Method factoryMethod = factoryKlass.getDeclaredMethod(factoryName.substring(methodIdx + 1));
            AWSClientType client = clientType.cast(factoryMethod.invoke(null));
            logger.debug("created client from factory: " + factoryName);
            return client;
        }
        catch (Exception ex)
        {
            if (rethrow)
                throw new RuntimeException("unable to invoke client factory: " + factoryName, ex);
            else
                return null;
        }
    }


    /**
     *  Invokes the default constructor appropriate to the client type.
     */
    private AmazonWebServiceClient createViaConstructor()
    {
        String clientClass = clientClasses.get(clientType.getName());
        if (clientClass == null)
        {
            throw new IllegalArgumentException("unsupported client type: " + clientType);  // should never happen
        }

        try
        {
            Class<?> klass = Class.forName(clientClass);
            return (AmazonWebServiceClient)klass.newInstance();
        }
        catch (Exception ex)
        {
            throw new RuntimeException("failed to instantiate service client: " + clientClass, ex);
        }
    }


    /**
     *  Configures the endpoint for a constructed client, using either a configured
     *  endpoint or the AWS_REGION environment variable.
     */
    private AmazonWebServiceClient tryConfigureEndpoint(AmazonWebServiceClient client)
    {
        if (endpoint != null)
        {
            logger.debug("configuring client endpoint: " + endpoint);
            client.setEndpoint(endpoint);
            return client;
        }

        String region = System.getenv("AWS_REGION");
        if (region != null)
        {
            logger.debug("configuring client region: " + region);
            client.configureRegion(Regions.fromName(region));
            return client;
        }

        return client;
    }
}
