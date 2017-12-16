// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.testhelpers;

import java.lang.reflect.Field;

/**
 *  Static utility methods to assist tests.
 */
public class TestUtils
{
    /**
     *  Looks for the specified field in the class or its superclasses, returning
     *  its value if it exists and throwing a <code>RuntimeException</code> if it
     *  doesn't or we had any exception during execution.
     */
    public static <T> T getFieldValue(Object obj, String fieldName, Class<T> expectedClass)
    {
        for (Class<?> objKlass = obj.getClass() ; objKlass != null ; objKlass = objKlass.getSuperclass())
        {
            try
            {
                Field field = objKlass.getDeclaredField(fieldName);
                field.setAccessible(true);
                return (T)field.get(obj);
            }
            catch (Exception ignored)
            {
                // we get here if the current class does not declare the field
                //  ... not so efficient, but we don't call it a lot and we need
                // to use getDeclaredField() to access private members
            }
        }

        throw new RuntimeException("unable to retrieve field " + fieldName + " from object of class " + obj.getClass().getName());
    }
}
