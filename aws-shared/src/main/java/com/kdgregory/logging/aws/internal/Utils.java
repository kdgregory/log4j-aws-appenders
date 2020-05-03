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

import java.lang.reflect.Method;

/**
 *  Various static utility functions. These are used by multiple classes and/or
 *  should be tested outside of the class where they're used.
 */
public class Utils
{
    /**
     *  Sleeps until the specified time elapses or the thread is interrupted.
     */
    public static void sleepQuietly(long time)
    {
        try
        {
            Thread.sleep(time);
        }
        catch (InterruptedException ignored)
        {
            // this will simply break to the caller
        }
    }


    /**
     *  Attempts to load a class, returning <code>null</code> if it doesn't exist.
     */
    public static Class<?> loadClass(String fullyQualifiedName)
    {
        try
        {
            return Class.forName(fullyQualifiedName);
        }
        catch (ClassNotFoundException ex)
        {
            return null;
        }
    }


    /**
     *  Returns a declared method with the specified parameter types, <code>null</code>
     *  if one doesn't exist or the passed class is null.
     */
    public static Method findDeclaredMethod(Class<?> klass, String methodName, Class<?>... paramTypes)
    {
        if (klass == null)
            return null;

        try
        {
            return klass.getDeclaredMethod(methodName, paramTypes);
        }
        catch (Exception ex)
        {
            return null;
        }
    }


    /**
     *  Attempts to find a static factory method, given a fully-qualified method name
     *  (eg: <code>com.example.package.MyClass.myMethod</code>). Returns null if passed
     *  null or an empty string, throws if given an invalid method name, and optionally
     *  throws if the method can't be found.
     *  <p>
     *  If there are multiple methods with the same name, returns the first one found.
     */
    public static Method lookupFactoryMethod(String fullyQualifiedMethodName, boolean throwIfMissing)
    throws ClassNotFoundException, NoSuchMethodException
    {
        if ((fullyQualifiedMethodName == null) || (fullyQualifiedMethodName.isEmpty()))
            return null;

        int methodIdx = fullyQualifiedMethodName.lastIndexOf('.');
        if (methodIdx <= 0)
            throw new IllegalArgumentException("invalid factory method name: " + fullyQualifiedMethodName);

        String className = fullyQualifiedMethodName.substring(0, methodIdx);
        String methodName = fullyQualifiedMethodName.substring(methodIdx + 1);

        Class<?> klass = loadClass(className);
        if (klass == null)
            throw new ClassNotFoundException(className);

        for (Method method : klass.getDeclaredMethods())
        {
            if (method.getName().equals(methodName))
            {
                return method;
            }
        }

        if (throwIfMissing)
            throw new NoSuchMethodException("invalid factory method: " + fullyQualifiedMethodName);
        else
            return null;
    }


    /**
     *  Invokes a static method, returning <code>null</code> if the passed method
     *  is <code>null</code>, or if any exception occurred.
     */
    public static Object invokeStatic(Method method, Object... params)
    {
        if (method == null)
            return null;

        try
        {
            return method.invoke(null, params);
        }
        catch (Exception ex)
        {
            return null;
        }
    }


    /**
     *  Invokes the named single-argument method, iff the passed value is not null. Optionally
     *  throws or swallows any exceptions.
     */
    public static boolean maybeSetValue(Object obj, String setterName, Class<?> valueKlass, Object value, boolean rethrow)
    throws Exception
    {
        if ((obj == null) || (value == null))
            return false;

        try
        {
            Method setter = obj.getClass().getMethod(setterName, valueKlass);
            setter.invoke(obj, value);
            return true;
        }
        catch (Exception ex)
        {
            if (rethrow)
                throw ex;
        }

        return false;
    }
}
