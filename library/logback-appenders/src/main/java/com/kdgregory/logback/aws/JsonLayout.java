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

package com.kdgregory.logback.aws;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.kdgregory.logback.aws.internal.AbstractJsonLayout;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;


/**
 *  Formats an <code>ILoggingEvent</code> as a JSON string. This supports Elasticsearch
 *  without the need for Logstash (as well as any other JSON-consuming endpoint).
 *  <p>
 *  The JSON object will always contain the following properties:
 *  <ul>
 *  <li> <code>timestamp</code>:    the date/time that the message was logged.
 *  <li> <code>thread</code>:       the name of the thread where the message was logged.
 *  <li> <code>logger</code>:       the name of the logger.
 *  <li> <code>level</code>:        the level of this log message.
 *  <li> <code>message</code>:      the message itself.
 *  <li> <code>hostname</code>:     the name of the machine where the logger is running, if
 *                                  available (this is currently retrieved from
 *                                  <code>RuntimeMxBean</code> and may not be available on
 *                                  all platforms).
 *  <li> <code>processId</code>:    the PID of the invoking process, if available (this is
 *                                  retrieved from <code>RuntimeMxBean</code> and may not be
 *                                  available on all platforms).
 *  </ul>
 *  <p>
 *  The following properties will only appear if they are present in the event:
 *  <ul>
 *  <li> <code>exception</code>:    an exception with stack trace. This is an array, with the
 *                                  first element identifying the exception and message, and
 *                                  subsequent elements identifying the stack trace.
 *  <li> <code>mdc</code>:          the mapped diagnostic context. This is a child object.
 *  </ul>
 *  <p>
 *  The following properties are potentially expensive to compute, so will only
 *  appear if specifically enabled via configuration:
 *  <ul>
 *  <li> <code>locationInfo</code>: the location where the logger was invoked. This is a
 *                                  child object with the following components:
 *                                  <ul>
 *                                  <li> <code>className</code>
 *                                  <li> <code>methodName</code>
 *                                  <li> <code>fileName</code>
 *                                  <li> <code>lineNumber</code>
 *                                  </ul>
 *  <li> <code>instanceId</code>:   the EC2 instance ID of the machine where the logger is
 *                                  running. WARNING: do not enable this elsewhere, as the
 *                                  operation to retrieve this value may take a long time.
 *  <li> <code>accountId</code>:    the AWS account ID associated with the current user/role.
 *  </ul>
 *  <p>
 *  Lastly, you can define a set of user tags, which are written as a child object with
 *  the key <code>tags</code>. These are intended to provide program-level information,
 *  in the case where multiple programs send their logs to the same stream. They are set
 *  as a single comma-separate string, which may contain substitution values (example:
 *  <code>appName=Fribble,startedAt={startupTimestamp}</code>).
 *  <p>
 *  WARNING: you should not rely on the order in which elements are output. Any apparent
 *  ordering is an implementation artifact and subject to change without notice.
 */
public class JsonLayout
extends AbstractJsonLayout<ILoggingEvent>
{
    private KeyValueHandler keyValueHandler = null;

//----------------------------------------------------------------------------
//  Configuration
//----------------------------------------------------------------------------

    private boolean enableLocation;
    private boolean enableKeyValuePairs;


    public void setEnableLocation(boolean value)
    {
        enableLocation = value;
    }


    public boolean getEnableLocation()
    {
        return enableLocation;
    }


    public void setEnableKeyValuePairs(boolean value)
    {
        enableKeyValuePairs = value;
        keyValueHandler = value ? new KeyValueHandler() : null;
    }


    public boolean getEnableKeyValuePairs()
    {
        return enableKeyValuePairs;
    }

//----------------------------------------------------------------------------
//  Layout Overrides
//----------------------------------------------------------------------------

    @Override
    public String doLayout(ILoggingEvent event)
    {
        Map<String,Object> map = new TreeMap<String,Object>();
        map.put("timestamp",    new Date(event.getTimeStamp()));
        map.put("thread",       event.getThreadName());
        map.put("logger",       event.getLoggerName());
        map.put("level",        event.getLevel().toString());
        map.put("message",      event.getFormattedMessage());

        List<String> exceptionInfo = extractExceptionInfo(event);
        if (exceptionInfo != null)
        {
            map.put("exception", exceptionInfo);
        }

        if ((event.getMDCPropertyMap() != null) && ! event.getMDCPropertyMap().isEmpty())
        {
            map.put("mdc", event.getMDCPropertyMap());
        }

        if (enableLocation)
        {
            StackTraceElement[] callerData = event.getCallerData();
            if ((callerData != null) && (event.getCallerData().length > 0))
            {
                StackTraceElement info = callerData[0];
                Map<String,Object> location = new TreeMap<String,Object>();
                location.put("className",  info.getClassName());
                location.put("methodName", info.getMethodName());
                location.put("fileName",   info.getFileName());
                location.put("lineNumber", info.getLineNumber());
                map.put("locationInfo", location);
            }
        }

        if (enableKeyValuePairs)
        {
            keyValueHandler.appendKeyValuePairs(event, map);
        }

        return addCommonAttributesAndConvert(map);
    }


    /**
     *  Extracts the exception information from an event, returning either null
     *  or a list of strings.
     */
    private static List<String> extractExceptionInfo(ILoggingEvent event)
    {
        return (event.getThrowableProxy() == null)
             ? null
             : appendThrowable(new ArrayList<String>(), event.getThrowableProxy());
    }


    /**
     *  Appends exception info from the current throwable to the passed list,
     *  including its cause.
     */
    private static List<String> appendThrowable(List<String> entries, IThrowableProxy throwable)
    {
        String optCausedBy = entries.isEmpty() ? "" : "Caused by: ";
        String initialLine = optCausedBy + throwable.getClassName() + ": " + throwable.getMessage();
        entries.add(initialLine);

        for (StackTraceElementProxy ste : throwable.getStackTraceElementProxyArray())
        {
            entries.add(ste.getSTEAsString());
        }

        return (throwable.getCause() == null)
             ? entries
             : appendThrowable(entries, throwable.getCause());
    }


//----------------------------------------------------------------------------
//  Helper classes
//----------------------------------------------------------------------------

    /**
     *  Handles the key-value pairs added to <code>ILoggingEvent</code> with
     *  Logback version 1.3. This class uses reflection to access these fields,
     *  so is compatible with earlier versions; if the fields don't exist, it
     *  acts as a no-op.
     *
     *  Implementation note: I compared the performance of direct access, method
     *  handles, and traditional reflection. Reflection and method handles were
     *  about the same, and about 20% slower than direct access. Since the latter
     *  would have required changing this module's dependency version, and would
     *  have required other code changes due to backwards-incompatible changes, I
     *  decided to stick with reflection.
     */
    private static class KeyValueHandler
    {
        private boolean isConfigured;
        private Method mGetKeyValuePairs;
        private Field kvpKey;
        private Field kvpValue;

        public KeyValueHandler()
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
}
