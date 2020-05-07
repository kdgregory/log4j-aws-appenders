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
     *  Returns a declared or inherited method with the specified parameter types,
     *  <code>null</code> if one doesn't exist or the passed class is null.
     */
    public static Method findMethodIfExists(Class<?> klass, String methodName, Class<?>... paramTypes)
    {
        if ((klass == null) || (methodName == null) || methodName.isEmpty())
            return null;

        try
        {
            return klass.getDeclaredMethod(methodName, paramTypes);
        }
        catch (Exception ex)
        {
            // fall-through to find inherited method
        }

        try
        {
            return klass.getMethod(methodName, paramTypes);
        }
        catch (Exception ex)
        {
            return null;
        }
    }


    /**
     *  Attempts to find a method given its fully-qualified name (eg:
     *  <code>com.example.package.MyClass.myMethod</code>). Typically used
     *  for static factory method lookup, but can be used for instance
     *  methods as well.
     *
     *  @throws IllegalArgumentException if unable parse the method name.
     *  @throws ClassNotFoundException if unable to load the specified class.
     *  @throws NoSuchMethodException if unable to find a method with the given parameters.
     */
    public static Method findFullyQualifiedMethod(String name, Class<?>... params)
    throws ClassNotFoundException, NoSuchMethodException
    {
        if ((name == null) || (name.isEmpty()))
            return null;

        int methodIdx = name.lastIndexOf('.');
        if (methodIdx <= 0)
            throw new IllegalArgumentException("invalid factory method name: " + name);

        String className = name.substring(0, methodIdx);
        String methodName = name.substring(methodIdx + 1);

        Class<?> klass = loadClass(className);
        if (klass == null)
            throw new ClassNotFoundException(className);

        Method method = findMethodIfExists(klass, methodName, params);
        if (method == null)
            throw new NoSuchMethodException("invalid factory method: " + name);

        return method;
    }


    /**
     *  Invokes an instance method, returning <code>null</code> if the passed method
     *  is <code>null</code> or if any exception occurred.
     */
    public static Object invokeQuietly(Object obj, Method method, Object... params)
    {
        if ((obj == null) || (method == null))
            return null;

        try
        {
            return method.invoke(obj, params);
        }
        catch (Exception ex)
        {
            return null;
        }
    }


    /**
     *  Invokes a static method, returning <code>null</code> if the passed method
     *  is <code>null</code> or if any exception occurred.
     */
    public static Object invokeQuietly(Method method, Object... params)
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
     *  Invokes a named setter method; no-op if passed a null object. Throws
     *  <code>NoSuchMethodException</code> if unable to find a method that takes
     *  the specified parameters. Unwraps <code>InvocationTargetException</code>,
     *  all other reflection exceptions thrown unchanged.
     */
    public static void invokeSetter(Object obj, String setterName, Class<?> valueKlass, Object value)
    throws Throwable
    {
        if (obj == null)
            return;

        Method m = findMethodIfExists(obj.getClass(), setterName, valueKlass);
        if (m == null)
            throw new NoSuchMethodException(obj.getClass().getName() + "." + setterName);

        try
        {
            m.invoke(obj, value);
        }
        catch (InvocationTargetException ex)
        {
            throw ex.getCause();
        }
    }


    /**
     *  Invokes the named setter method suppressing any exceptions. Returns <code>true</code>
     *  if successful, false if not.
     */
    public static boolean invokeSetterQuietly(Object obj, String setterName, Class<?> valueKlass, Object value)
    throws Exception
    {
        if (obj == null)
            return false;

        try
        {
            invokeSetter(obj, setterName, valueKlass, value);
            return true;
        }
        catch (Throwable ex)
        {
            return false;
        }
    }
}
