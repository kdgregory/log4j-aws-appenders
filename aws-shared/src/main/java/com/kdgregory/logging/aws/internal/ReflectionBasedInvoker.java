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

package com.kdgregory.logging.aws.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


/**
 *  A helper class for performing retrievals using reflection. This exists to
 *  avoid making hard references to optional libraries.
 *  <p>
 *  Each instance is intended to perform a single retrieval operation on an AWS
 *  client. A typical usage has the following steps:
 *  <ol>
 *  <li> Create an instance with the classnames of the client, request, and response objects.
 *  <li> Call {@link #instantiate} for the client and request objects.
 *  <li> Call {@link #setRequestValue} to prepare the request.
 *  <li> Call {@link #invokeRequest} to peform the request, saving the result.
 *  <li> Call {@link #getResponseValue} to retrieve any values from the result.
 *  <li> Call {@link #shutdown} to shut down the client. Typically in a finally
 *       block.
 *  </ol>
 *  All variables involved in this process must be defined as <code>Object</code>.
 *  <p>
 *  There are also two utility methods: {@link #loadClass} to load arbitrary classes,
 *  and {@link #invoke} for invocation of an arbitrary 0-arity or 1-arity method.
 *  <p>
 *  Any exceptions thrown during method invocations are retained, and short-circuit
 *  any subsequent operations. It is therefore safe to perform a chain of operations,
 *  as long as you can accept a null value at the end of the chain.
 */
public class ReflectionBasedInvoker
{
    // these are public because otherwise I'd just need to add accessors; I promise not to misuse
    public Throwable exception;
    public Class<?> clientKlass;
    public Class<?> requestKlass;
    public Class<?> responseKlass;


    /**
     *  Constructs an instance for a "standard" operation.
     */
    public ReflectionBasedInvoker(String clientClassName, String requestClassName, String responseClassName)
    {
        clientKlass = loadClass(clientClassName);
        requestKlass = loadClass(requestClassName);
        responseKlass = loadClass(responseClassName);
    }


    /**
     *  Exception-safe class load. Returns null if unable to load the class, and tracks
     *  the exception.
     */
    public Class<?> loadClass(String className)
    {
        if (className == null)
            return null;

        try
        {
            return Class.forName(className);
        }
        catch (Throwable ex)
        {
            exception = ex;
            return null;
        }
    }


    /**
     *  Instantiates the provided class. Normally this will be one of the classes
     *  loaded during construction, or via {@link #loadClass}.
     */
    public Object instantiate(Class<?> klass)
    {
        if ((exception != null) || (klass == null))
            return null;

        try
        {
            return klass.newInstance();
        }
        catch (Throwable ex)
        {
            exception = ex;
            return null;
        }
    }


    /**
     *  Base method invoker. This can be used for functions with arity 0 or 1:
     *  for former, pass null <code>paramKlass</code>.
     *  <p>
     *  Returns null if there's an outstanding exception or the provided object is
     *  null (this is a short-circuit), as well as when an exception occurs during
     *  invocation. For the latter, unwraps <code>InvocationTargetException</code>.
     */
    public Object invoke(Class<?> objKlass, Object obj, String methodName, Class<?> paramKlass, Object value)
    {
        if ((exception != null) || (objKlass == null) || (obj == null))
            return null;

        try
        {
            if (paramKlass != null)
            {
                Method method = objKlass.getMethod(methodName, paramKlass);
                return method.invoke(obj, value);
            }
            else
            {
                Method method = objKlass.getMethod(methodName);
                return method.invoke(obj);
            }
        }
        catch (InvocationTargetException ex)
        {
            exception = ex.getCause();
            return null;
        }
        catch (Throwable ex)
        {
            exception = ex;
            return null;
        }
    }


    /**
     *  Convenience method that invokes a setter on the provided object, which is
     *  assumed to be an instance of the constructed request class.
     */
    public void setRequestValue(Object request, String methodName, Class<?> valueKlass, Object value)
    {
        invoke(requestKlass, request, methodName, valueKlass, value);
    }


    /**
     *  Convenience method that invokes a single-argument client method. This uses
     *  the client, request, and response classes passed to the constructor.
     */
    public Object invokeRequest(Object client, String methodName, Object value)
    {
        return invoke(clientKlass, client, methodName, requestKlass, value);
    }


    /**
     *  Convenience method to invoke an accessor method on an object that's assumed
     *  to be the constructed response class.
     */
    public <T> T getResponseValue(Object response, String methodName, Class<T> resultKlass)
    {
        return resultKlass.cast(invoke(responseKlass, response, methodName, null, null));
    }


    /**
     *  Invokes the <code>shutdown()</code> method on the provided client, which is assumed
     *  to be an instance of the constructed client class. This short-circuits if the provided
     *  client object is null, and ignores any exceptions. It should be called in a finally
     *  block for any operation that creates an AWS client.
     */
    public void shutdown(Object client)
    {
        // note: if we have a client we want to shut it down, even if an exception has happened
        if (client == null)
            return;

        try
        {
            Method method = clientKlass.getMethod("shutdown");
            method.invoke(client);
        }
        catch (Throwable ex)
        {
            // ignored: at this point we don't care about exceptions because we've got a value
        }
    }
}