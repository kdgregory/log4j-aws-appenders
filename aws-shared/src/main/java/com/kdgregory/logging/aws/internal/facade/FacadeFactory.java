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

import java.lang.reflect.Constructor;

import com.kdgregory.logging.aws.internal.AbstractWriterConfig;


/**
 *  Creates new instances of AWS facade objects. Uses reflection to determine
 *  which implementation library is linked into the application.
 */
public class FacadeFactory
{
    /**
     *  Instantiates the facade implementation corresponding to the provided
     *  interface class. This variant is for service facades, which must be
     *  configured.
     *
     *  @throws IllegalArgumentException if unable to instantiate. Exception
     *          message will provide more information.
     */
    public static <T> T createFacade(Class<T> facadeType, AbstractWriterConfig<?> config)
    {
        Class<?> implClass = findImplementationClass(facadeType);
        return (T)instantiate(implClass, config);
    }


    /**
     *  Instantiates the facade implementation corresponding to the provided
     *  interface class. This variant is for <code>InfoFacade</code>, which
     *  uses default clients (so doesn't need configuration).
     *
     *  @throws IllegalArgumentException if unable to instantiate. Exception
     *          message will provide more information.
     */
    public static <T> T createFacade(Class<T> facadeType)
    {
        Class<?> implClass = findImplementationClass(facadeType);
        return (T)instantiate(implClass);
    }

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    private final static String[] FACADE_PACKAGES = new String[]
    {
        "com.kdgregory.logging.aws.facade.v1",
        "com.kdgregory.logging.aws.facade.v2"
    };


    /**
     *  Determines whether a facade implementation class exists in the classpath,
     *  and returns it (or null).
     *  <p>
     *  Implementation note: the facade implementation class names are based on
     *  the interface names, so it's easier to construct the names than to use
     *  a lookup table.
     */
    private static Class<?> findImplementationClass(Class<?> facadeType)
    {
        for (String packageName : FACADE_PACKAGES)
        {
            String className = packageName + "." + facadeType.getSimpleName() + "Impl";
            try
            {
                return Class.forName(className);
            }
            catch (ClassNotFoundException ignored)
            {
                // yes, it's using an exception for flow control; sue me
            }
        }

        throw new IllegalArgumentException("no implementation class for " + facadeType.getName());
    }


    /**
     *  Instantiates the implementation class, with optional arguments.
     */
    private static Object instantiate(Class<?> implClass, Object... ctorArgs)
    {
        try
        {
            Constructor<?>[] ctors = implClass.getConstructors();
            if (ctors.length != 1)
            {
                throw new IllegalArgumentException("implementation class does not expose a single constructor: " + implClass.getName());
            }

            return ctors[0].newInstance(ctorArgs);
        }
        catch (Exception ex)
        {
            throw new IllegalArgumentException("unable to instantiate: " + implClass.getName(), ex);
        }
    }
}
