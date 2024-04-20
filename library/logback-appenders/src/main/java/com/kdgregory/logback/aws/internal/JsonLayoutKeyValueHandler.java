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

package com.kdgregory.logback.aws.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import ch.qos.logback.classic.spi.ILoggingEvent;


/**
 *  Handles the key-value pairs added to <code>ILoggingEvent</code> with
 *  Logback version 1.3. Instances of this object are only created when
 *  key-value pairs are explicitly enabled, and so permit use of older
 *  versions of Logback.
 */
public class JsonLayoutKeyValueHandler
{
    private boolean isConfigured = false;
    private Method mGetKeyValuePairs;
    private Field kvpKey;
    private Field kvpValue;

    public JsonLayoutKeyValueHandler()
    {
        try
        {
            mGetKeyValuePairs = ILoggingEvent.class.getMethod("getKeyValuePairs");
            Class<?> kvpKlass = Class.forName("org.slf4j.event.KeyValuePair");
            kvpKey = kvpKlass.getField("key");
            kvpValue = kvpKlass.getField("value");
            isConfigured = true;
        }
        catch (Exception ex)
        {
            // we fail silently; there's no way for a layout to report errors
        }
    }


    public void appendKeyValuePairs(ILoggingEvent event, Map<String,Object> eventMap)
    {
        // always create the holder; this will give us something to test using 1.2
        Map<String,Object> pairMap = new TreeMap<>();
        eventMap.put("extra", pairMap);

        if (!isConfigured)
            return;

        try
        {
            List<? extends Object> pairs = (List<?>)mGetKeyValuePairs.invoke(event);
            if (pairs == null)
                return;

            for (Object pair : pairs)
            {
                pairMap.put((String)kvpKey.get(pair), kvpValue.get(pair));
            }
        }
        catch (Exception ex)
        {
            // we fail silently; there's no way for a layout to report errors
        }
    }
}
