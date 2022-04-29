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

package com.kdgregory.logging.aws.facade.v1.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.internal.AbstractWriterConfig;
import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;
import com.kdgregory.logging.common.internal.Utils;
import com.kdgregory.logging.common.util.ProxyUrl;


/**
 *  Creates and configures an AWS client based on the provided writer configuration.
 *  <P>
 *  Implementation note: the methods that configure a client builder are public and
 *  static, so that they can be used to create clients elsewhere in the facade.
 */
public class ClientFactory<T>
{
    private Class<T> clientType;
    private AbstractWriterConfig<?> config;

    // this field exists (and is exposed) to allow testing
    protected ProxyUrl proxy = new ProxyUrl();

    public ClientFactory(Class<T> clientType, AbstractWriterConfig<?> config)
    {
        this.clientType = clientType;
        this.config = config;
    }

//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    /**
     *  Creates a client based on provided configuration.
     */
    public T create()
    {
        T client = tryInstantiateFromFactory();
        if (client != null)
            return client;

        AwsClientBuilder<?,?> builder = createClientBuilder();
        ClientBuilderUtils.optSetRegionOrEndpoint(builder, config.getClientRegion(), config.getClientEndpoint());
        ClientBuilderUtils.optSetProxy(builder, proxy);
        ClientBuilderUtils.optSetAssumedRoleCredentialsProvider(builder, config.getAssumedRole(), proxy);
        return clientType.cast(builder.build());
    }

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    /**
     *  Determines whether the configuration specifies a factory method, and
     *  if so tries to invoke it.
     */
    protected T tryInstantiateFromFactory()
    {
        String fullyQualifiedMethodName = config.getClientFactoryMethod();
        if ((fullyQualifiedMethodName == null) || fullyQualifiedMethodName.isEmpty())
            return null;

        // there are two variants of the factory method; we'll look for the simple one
        // first, then the one that takes arguments; we separate lookup from invocation
        // because they throw different exceptions
        Method factoryMethod;
        try
        {
            factoryMethod = Utils.findFullyQualifiedMethod(fullyQualifiedMethodName);
        }
        catch (Exception ignored)
        {
            try
            {
                factoryMethod = Utils.findFullyQualifiedMethod(fullyQualifiedMethodName, String.class, String.class, String.class);
            }
            catch (Exception ex)
            {
                throw new RuntimeException("invalid factory method: " + fullyQualifiedMethodName, ex);
            }
        }

        try
        {
            return (factoryMethod.getParameterTypes().length == 0)
                 ? clientType.cast(factoryMethod.invoke(null))
                 : clientType.cast(factoryMethod.invoke(null, config.getAssumedRole(), config.getClientRegion(), config.getClientEndpoint()));
        }
        catch (Throwable ex)
        {
            if (ex instanceof InvocationTargetException)
                ex = ex.getCause();

            throw new RuntimeException("exception invoking factory method: " + fullyQualifiedMethodName, ex);
        }
    }


    /**
     *  Picks an appropriate client builder, based on the configuration type.
     */
    protected AwsClientBuilder<?,?> createClientBuilder()
    {
        if (config instanceof CloudWatchWriterConfig)
            return new AWSLogsClientBuilderBuilder().buildBuilder();

        if (config instanceof KinesisWriterConfig)
            return new AmazonKinesisClientBuilderBuilder().buildBuilder();

        if (config instanceof SNSWriterConfig)
            return new AmazonSNSClientBuilderBuilder().buildBuilder();

        throw new RuntimeException("unsupported configuration type: " + config.getClass());
    }

//----------------------------------------------------------------------------
//
//  The classes below exist to break hard dependencies on the various SDK
//  libraries (ie, if you're just using the CloudWatch appender, you shouldn't
//  need to add the Kinesis SDK to your classpath).
//
//  These are called from createClientBuilder(), which has an explit return
//  type. As best I can tell (after lots of experiments, which will probably
//  turn into a blog post), the bytecode verifier tries to ensure that the
//  returned values are compatible. But to do this, it has to load the class.
//
//  By wrapping these static method calls in their own class, I defer that
//  verification until I load the relevant class. I suppose I could use a
//  lambda to do this, but then I'd need to put this long comment into what
//  is otherwise a short method.
//
//  The names of these classes may perpetuate a stereotype, but they are
//  accurate. The method name should be taken as a joke.
//
//----------------------------------------------------------------------------

    private static class AWSLogsClientBuilderBuilder
    {
        public AwsClientBuilder<?,?> buildBuilder()
        {
            return AWSLogsClientBuilder.standard();
        }
    }


    private static class AmazonKinesisClientBuilderBuilder
    {
        public AwsClientBuilder<?,?> buildBuilder()
        {
            return AmazonKinesisClientBuilder.standard();
        }
    }


    private static class AmazonSNSClientBuilderBuilder
    {
        public AwsClientBuilder<?,?> buildBuilder()
        {
            return AmazonSNSClientBuilder.standard();
        }
    }
}
