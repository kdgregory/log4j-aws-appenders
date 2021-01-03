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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration;
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder;
import com.amazonaws.services.logs.AWSLogsClientBuilder;
import com.amazonaws.services.sns.AmazonSNSClientBuilder;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.internal.AbstractWriterConfig;
import com.kdgregory.logging.aws.internal.Utils;
import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;


/**
 *  Creates and configures an AWS client based on the provided writer configuration.
 *  <P>
 *  Implementation note: all internal methods are protected to enable testing.
 */
public class ClientFactory<T>
{
    private Class<T> clientType;
    private AbstractWriterConfig<?> config;

    public ClientFactory(Class<T> clientType, AbstractWriterConfig<?> config)
    {
        this.clientType = clientType;
        this.config = config;
    }

//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    public T create()
    {
        T client = tryInstantiateFromFactory();
        if (client != null)
            return client;

        AwsClientBuilder<?,?> builder = createClientBuilder();
        optSetRegionOrEndpoint(builder);

        String roleToAssume = config.getAssumedRole();
        if ((roleToAssume != null) && !roleToAssume.isEmpty())
        {
            setAssumedRoleCredentialsProvider(builder, roleToAssume);
        }

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


    /**
     *  If the configuration specifies region, attempts to set it.
     */
    protected void optSetRegionOrEndpoint(AwsClientBuilder<?,?> builder)
    {
        String region = config.getClientRegion();
        String endpoint = config.getClientEndpoint();

        if ((endpoint != null) && ! endpoint.isEmpty())
        {
            builder.setEndpointConfiguration(
                new EndpointConfiguration(endpoint, region));
        }
        else if ((region != null) && ! region.isEmpty())
        {
            builder.setRegion(region);
        }
    }


    /**
     *  Configures the builder with an assumed-role credentials provider. The
     *  test for this is in the caller, so that we can override this method for
     *  testing.
     */
    protected void setAssumedRoleCredentialsProvider(AwsClientBuilder<?,?> builder, String roleToAssume)
    {
        AWSCredentialsProvider credentialsProvider = new AssumedRoleCredentialsProviderProvider()
                                                     .provideProvider(roleToAssume);
        builder.setCredentials(credentialsProvider);
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
