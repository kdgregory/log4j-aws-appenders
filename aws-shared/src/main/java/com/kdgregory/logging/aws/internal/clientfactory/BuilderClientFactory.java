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

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import com.amazonaws.auth.AWSCredentialsProvider;

import com.kdgregory.logging.aws.internal.ReflectionBasedInvoker;
import com.kdgregory.logging.aws.internal.Utils;
import com.kdgregory.logging.aws.internal.retrievers.RoleArnRetriever;
import com.kdgregory.logging.common.factories.ClientFactory;
import com.kdgregory.logging.common.factories.ClientFactoryException;
import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  Attempts to use a ClientBuilder to create an AWS client. Since the builders
 *  are not implemented for early versions of the 1.11.x SDK, this class uses
 *  reflection to do its work.
 */
public class BuilderClientFactory<ClientType>
implements ClientFactory<ClientType>
{
    // these are provided to constructor
    private Class<ClientType> clientType;
    private String builderClassName;
    private String assumedRole;
    private String region;
    private InternalLogger logger;

    // assigned by constructor
    private Class<?> builderClass;


    public BuilderClientFactory(Class<ClientType> clientType, String builderClassName,
                                String assumedRole, String region,
                                InternalLogger logger)
    {
        this.clientType = clientType;
        this.builderClassName = builderClassName;
        this.assumedRole = assumedRole;
        this.region = region;
        this.logger = logger;

        // we load this now because it means a simple "if" in createClient()
        builderClass = Utils.loadClass(builderClassName);
    }


    @Override
    public ClientType createClient()
    {
        if (builderClass == null)
            return null;

        logger.debug("creating client via SDK builder");

        // can't refer to builder by its actual class, because that class doesn't exist in 1.11.0
        Object builder = createBuilder();

        setRegion(builder);
        setCredentialsProvider(builder);

        try
        {
            Method clientFactoryMethod = builder.getClass().getMethod("build");
            return clientType.cast(clientFactoryMethod.invoke(builder));
        }
        catch (Exception ex)
        {
            throw new ClientFactoryException("failed to invoke builder: " + builderClassName, ex);
        }
    }


    // although I could use the methods in Utils to do this, I want to preserve any exception
    protected Object createBuilder()
    {
        try
        {
            Method method = builderClass.getDeclaredMethod("standard");
            return method.invoke(null);
        }
        catch (Exception ex)
        {
            throw new ClientFactoryException("failed to create builder", ex);
        }
    }


    protected void setRegion(Object builder)
    {
        try
        {
            if ((region != null) && !region.isEmpty())
            {
                logger.debug("setting region: " + region);
                Utils.maybeSetValue(builder, "setRegion", String.class, region, true);
            }
        }
        catch (Exception ex)
        {
            throw new ClientFactoryException("failed to set region: " + region, ex);
        }
    }


    protected void setCredentialsProvider(Object builder)
    {
        try
        {
            AWSCredentialsProvider credentialsProvider;
            if (assumedRole == null)
            {
                logger.debug("using default credentials provider");
                credentialsProvider = createDefaultCredentialsProvider();
            }
            else
            {
                logger.debug("assuming role " + assumedRole);
                credentialsProvider = createAssumedRoleCredentialsProvider();
            }

            Utils.maybeSetValue(builder, "setCredentials", AWSCredentialsProvider.class, credentialsProvider, true);
        }
        catch (Exception ex)
        {
            throw new ClientFactoryException("failed to set credentials provider", ex);
        }
    }


    protected AWSCredentialsProvider createDefaultCredentialsProvider()
    {
        ReflectionBasedInvoker invoker = new ReflectionBasedInvoker("com.amazonaws.auth.DefaultAWSCredentialsProviderChain");
        AWSCredentialsProvider provider = (AWSCredentialsProvider)invoker.invokeStatic(invoker.clientKlass, "getInstance", null, null);
        if (invoker.exception != null)
        {
            logger.error("failed to create default credentials provider", invoker.exception);
        }
        return provider;
    }


    protected AWSCredentialsProvider createAssumedRoleCredentialsProvider()
    {
        String roleArn = new RoleArnRetriever().invoke(assumedRole);

        // implementation note: there's no requirement for uniqueness on the role session
        // name, and adding too much additional text (such as a hostname) risks exceeding
        // the 64-character limit, so we'll let all loggers use the same name
        String sessionName = getClass().getName();

        ReflectionBasedInvoker invoker = new ReflectionBasedInvoker("com.amazonaws.auth.STSAssumeRoleSessionCredentialsProvider$Builder",
                                                                    "com.amazonaws.services.securitytoken.AWSSecurityTokenService",
                                                                    null);

        try
        {
            // unfortunately, the invoker only supports 0-arity constructors so we have to do reflection
            Constructor<?> ctor = invoker.clientKlass.getConstructor(String.class, String.class);
            Object stsClientBuilder = ctor.newInstance(roleArn, sessionName);
            invoker.invokeMethod(invoker.clientKlass, stsClientBuilder, "withStsClient", invoker.requestKlass, createSTSClient());
            AWSCredentialsProvider provider = (AWSCredentialsProvider)invoker.invokeMethod(invoker.clientKlass, stsClientBuilder, "build", null, null);
            if (invoker.exception != null)
                throw invoker.exception; // gets caught below
            return provider;
        }
        catch (Throwable ex)
        {
            throw new ClientFactoryException("failed to create assumed-role credentials provider", ex);
        }
    }


    protected Object createSTSClient()
    throws Throwable
    {
        ReflectionBasedInvoker invoker = new ReflectionBasedInvoker("com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder");
        Object stsClient = invoker.invokeStatic(invoker.clientKlass, "defaultClient", null, null);
        if (invoker.exception != null)
            throw invoker.exception;
        return stsClient;
    }
}
