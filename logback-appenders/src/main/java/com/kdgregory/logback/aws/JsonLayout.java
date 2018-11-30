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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.kdgregory.logging.aws.common.Substitutions;
import com.kdgregory.logging.common.util.JsonConverter;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.LayoutBase;


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
 *  <li> <code>mdc</code>:          the mapped diagnostic context. This is a child map.
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
 *  <li> <code>hostname</code>:     the name of the machine where the logger is running, if
 *                                  available (this is currently retrieved from
 *                                  <code>RuntimeMxBean</code> and may not be available on
 *                                  all platforms).
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
extends LayoutBase<ILoggingEvent>
{
    // if enabled and supported, these will be not-null
    private String processId;
    private String hostname;
    private String instanceId;
    private Map<String,String> tags;

//----------------------------------------------------------------------------
//  Configuration
//----------------------------------------------------------------------------

    private boolean appendNewlines;
    private boolean enableLocation;
    private boolean enableHostname;
    private boolean enableInstanceId;
    private String rawTags;


    public void setAppendNewlines(boolean value)
    {
        appendNewlines = value;
    }

    public boolean getAppendNewlines()
    {
        return appendNewlines;
    }


    public void setEnableLocation(boolean value)
    {
        enableLocation = value;
    }


    public boolean getEnableLocation()
    {
        return enableLocation;
    }


    public void setEnableInstanceId(boolean value)
    {
        enableInstanceId = value;
    }


    public boolean getEnableInstanceId()
    {
        return enableInstanceId;
    }


    public void setEnableHostname(boolean value)
    {
        enableHostname = value;
    }


    public boolean getEnableHostname()
    {
        return enableHostname;
    }


    public void setTags(String value)
    {
        rawTags = value;
    }


    public String getTags()
    {
        return rawTags;
    }

//----------------------------------------------------------------------------
//  Layout Overrides
//----------------------------------------------------------------------------

    @Override
    public void start()
    {
        Substitutions subs = new Substitutions(new Date(), 0);

        processId = subs.perform("{pid}");
        if ("unknown".equals(processId))
            processId = null;

        if (enableHostname)
        {
            hostname = subs.perform("{hostname}");
            if ("unknown".equals(hostname))
                hostname = null;
        }

        if (enableInstanceId)
        {
            instanceId = subs.perform("{instanceId}");
            if ("{instanceId}".equals(instanceId))
                instanceId = null;
        }

        if ((rawTags != null) && !rawTags.isEmpty())
        {
            tags = new TreeMap<String,String>();
            for (String tagdef : rawTags.split(","))
            {
                String[] splitdef = tagdef.split("=");
                if (splitdef.length == 2)
                {
                    tags.put(splitdef[0], subs.perform(splitdef[1]));
                }
                else
                {
                    throw new IllegalArgumentException("invalid tag definition: " + tagdef);
                }
            }
        }
        super.start();
    }


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

        if (processId != null)  map.put("processId", processId);
        if (hostname != null)   map.put("hostname", hostname);
        if (instanceId != null) map.put("instanceId", instanceId);

        if (tags != null)       map.put("tags",         tags);

        String json = (new JsonConverter()).convert(map);
        if (getAppendNewlines())
        {
            json += "\n";
        }

        return json;
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
}
