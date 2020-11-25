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
 *  Creates new instances of AWS facade objects. Uses refelection to identify
 *  which facade library is linked into the application.
 */
public class FacadeFactory
{
    /**
     *  Instantiates the facade implementation corresponding to the provided
     *  interface class.
     *
     *  @throws IllegalArgumentException if unable to instantiate. Exception
     *          message will provide more information.
     */
    public static <T> T createFacade(Class<T> facadeType, AbstractWriterConfig<?> config)
    {
        Class<?> implClass = findImplementationClass(facadeType);
        if (implClass == null)
        {
            throw new IllegalArgumentException("no implementation class for " + facadeType.getName());
        }

        try
        {
            // implementation classes must provide a one-argument constructor, taking a compatible
            // configuration object
            Constructor<?> ctor = implClass.getConstructors()[0];
            return (T)ctor.newInstance(config);
        }
        catch (Exception ex)
        {
            throw new IllegalArgumentException("unable to instantiate: " + implClass.getName(), ex);
        }
    }


//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    private final static String[] FACADE_PACKAGES = new String[]
    {
        "com.kdgregory.logging.aws.facade.v1"
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
        // TODO - implement a cache (only needed when we'd creating clients more
        //        than once per application run)

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

        return null;
    }
}
