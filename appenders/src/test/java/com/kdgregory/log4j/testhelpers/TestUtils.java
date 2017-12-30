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
