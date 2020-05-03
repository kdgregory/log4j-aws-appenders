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

import org.junit.Test;
import static org.junit.Assert.*;

import static net.sf.kdgcommons.test.StringAsserts.*;


public class TestUtils
{
//----------------------------------------------------------------------------
//  Support Code
//----------------------------------------------------------------------------

    public static class ObjectWithSetter
    {
        public String value;

        public void setValue(String value)
        {
            this.value = value;
        }
    }


    public int declaredMethod()
    {
        return 1;
    }

    public int declaredMethod(String foo)
    {
        return 2;
    }

    public static int factoryMethod()
    {
        return 100;
    }

    public static int factoryMethod(String foo)
    {
        return 200;
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testLoadClass() throws Exception
    {
        assertSame("known good class", getClass(),  Utils.loadClass("com.kdgregory.logging.aws.internal.TestUtils"));
        assertNull("known bad class",               Utils.loadClass("com.kdgregory.IDoNotExist"));
    }


    @Test
    public void testGetDeclaredMethodIfExists() throws Exception
    {
        Method m0a = Utils.findMethodIfExists(null, "declaredMethod");
        assertNull("returned null for null class", m0a);

        Method m0b = Utils.findMethodIfExists(getClass(), null);
        assertNull("returned null for null method name", m0b);

        Method m0c = Utils.findMethodIfExists(getClass(), "");
        assertNull("returned null for empty method name", m0c);

        Method m1 = Utils.findMethodIfExists(getClass(), "declaredMethod");
        assertEquals("found no-param method", Integer.valueOf(1), m1.invoke(this));

        Method m2 = Utils.findMethodIfExists(getClass(), "declaredMethod", String.class);
        assertEquals("found one-param method", Integer.valueOf(2), m2.invoke(this, "foo"));

        Method m3 = Utils.findMethodIfExists(getClass(), "declaredMethod", String.class, Integer.class);
        assertNull("returned null for incorrect parameters", m3);

        Method m4 = Utils.findMethodIfExists(getClass(), "bogus");
        assertNull("returned null for bogus method name", m4);
    }


    @Test
    public void testFindFactoryMethod() throws Exception
    {
        String unparseable = "bogus";
        try
        {
            Utils.findFullyQualifiedMethod(unparseable);
            fail("did not throw for unparseable method name");
        }
        catch (IllegalArgumentException ex)
        {
            assertRegex("unparseable method name exception message (was: " + ex.getMessage() + ")",
                        "invalid.*" + unparseable,
                        ex.getMessage());
        }

        String invalidClass = "com.example.Bogus";
        try
        {
            Utils.findFullyQualifiedMethod(invalidClass + ".doesntMatter");
            fail("did not throw for nonexistent class");
        }
        catch (ClassNotFoundException ex)
        {
            assertRegex("nonexistent class exception message (was: " + ex.getMessage() + ")",
                        ".*" + invalidClass,
                        ex.getMessage());
        }

        String validClassInvalidMethod = getClass().getName() + ".bogus";
        try
        {
            Utils.findFullyQualifiedMethod(validClassInvalidMethod);
            fail("did not throw for nonexistent method");
        }
        catch (NoSuchMethodException ex)
        {
            assertRegex("nonexistent method exception message (was: " + ex.getMessage() + ")",
                        ".*" + validClassInvalidMethod,
                        ex.getMessage());
        }


        String validClassValidMethod = getClass().getName() + ".factoryMethod";

        Method m1 = Utils.findFullyQualifiedMethod(validClassValidMethod);
        assertEquals("found no-param method", Integer.valueOf(100), m1.invoke(null));

        Method m2 = Utils.findFullyQualifiedMethod(validClassValidMethod, String.class);
        assertEquals("found one-param method", Integer.valueOf(200), m2.invoke(null, "foo"));

        try
        {
            Utils.findFullyQualifiedMethod(validClassValidMethod, String.class, Integer.class);
            fail("did not throw for method with invalid parameters");
        }
        catch (NoSuchMethodException ex)
        {
            assertRegex("nonexistent method exception message (was: " + ex.getMessage() + ")",
                        ".*" + validClassValidMethod,
                        ex.getMessage());
        }
    }


    @Test
    public void testInvokeQuietly() throws Exception
    {
        Method m1 = Utils.findFullyQualifiedMethod(getClass().getName() + ".declaredMethod", String.class);

        assertEquals("valid instance invocation",               Integer.valueOf(2),     Utils.invokeQuietly(this, m1, "foo"));
        assertEquals("instance invocation with wrong #/params", null,                   Utils.invokeQuietly(this, m1, "foo", "bar"));
        assertEquals("instance invocation with null method",    null,                   Utils.invokeQuietly(this, null, "foo", "bar"));
        assertEquals("instance invocation with null object",    null,                   Utils.invokeQuietly((Object)null, m1, "foo", "bar"));

        Method m2 = Utils.findFullyQualifiedMethod(getClass().getName() + ".factoryMethod", String.class);

        assertEquals("valid static invocation",                 Integer.valueOf(200),   Utils.invokeQuietly(m2, "foo"));
        assertEquals("static invocation with wrong #/params",   null,                   Utils.invokeQuietly(m2, "foo", "bar"));
        assertEquals("static invocation with null method",      null,                   Utils.invokeQuietly(null, "foo", "bar"));
    }


    @Test
    public void testInvokeSetter() throws Throwable
    {
        ObjectWithSetter obj = new ObjectWithSetter();

        Utils.invokeSetter(obj, "setValue", String.class, "foo");
        assertEquals("happy path", "foo", obj.value);

        assertTrue(Utils.invokeSetterQuietly(obj, "setValue", String.class, null));
        assertNull("can set null value", obj.value);

        // null object is a no-op, this shouldn't throw
        Utils.invokeSetter(null, "setValue", String.class, "foo");

        try
        {
            Utils.invokeSetter(obj, "setSomething", String.class, "foo");
            fail("succeeded with nonexistent method");
        }
        catch (NoSuchMethodException ex)
        {
            assertRegex("nonexistent method exception message (was: " + ex.getMessage() + ")",
                        ".*setSomething",
                        ex.getMessage());
        }

        try
        {
            Utils.invokeSetter(obj, "setValue", String.class, Integer.valueOf(123));
            fail("succeeded with incorrect parameter type");
        }
        catch (IllegalArgumentException ex)
        {
            // this is success; nothing valuable to assert on: message is JDK-dependent
        }
    }


    @Test
    public void testInvokeSetterQuietly() throws Throwable
    {
        ObjectWithSetter obj = new ObjectWithSetter();

        assertTrue(Utils.invokeSetterQuietly(obj, "setValue", String.class, "foo"));
        assertEquals("happy path", "foo", obj.value);

        assertTrue(Utils.invokeSetterQuietly(obj, "setValue", String.class, null));
        assertNull("can set null value", obj.value);

        // if any of these throw, we have a problem
        assertFalse(Utils.invokeSetterQuietly(null, "setValue", String.class, "foo"));
        assertFalse(Utils.invokeSetterQuietly(obj, "setSomething", String.class, "foo"));
        assertFalse(Utils.invokeSetterQuietly(obj, "setValue", String.class, Integer.valueOf(123)));
    }
}
