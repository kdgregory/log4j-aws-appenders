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

package com.kdgregory.log4j.aws;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Layout;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;

import com.kdgregory.log4j.aws.common.Substitutions;
import com.kdgregory.log4j.common.JsonConverter;


/**
 *  Formats a LogMessage as a JSON string. This is useful when directing the
 *  output of the logger into a search engine such as ElasticSearch.
 *  <p>
 *  The JSON object will always contain the following properties, most of which
 *  are extracted from the Log4J <code>LoggingEvent</code>
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
 *  <li> <code>exception</code>:    the stack trace of an associated exception. This
 *                                  is exposed as an array of strings, with the first
 *                                  element being the location where the exception
 *                                  was caught.
 *  <li> <code>mdc</code>:          the mapped diagnostic context. This is a child map.
 *  <li> <code>ndc</code>:          the nested diagnostic context. This is a single
 *                                  string that contains each of the pushed entries
 *                                  separated by spaces (yes, that's how Log4J does it).
 *  </ul>
 *  <p>
 *  The following properties are potentially expensive to compute, so will only
 *  appear if specifically enabled via configuration:
 *  <ul>
 *  <li> <code>locationInfo</code>: the location where the logger was called. This is a
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
 *  ordering is an implementation choice and subject to change without notice.
 */
public class JsonLayout
extends Layout
{
    // if enabled and supported, these will be not-null
    private String processId;
    private String hostname;
    private String instanceId;
    private Map<String,String> tags;

    private ThreadLocal<JsonConverter> converterTL = new ThreadLocal<JsonConverter>()
    {
        @Override
        protected JsonConverter initialValue()
        {
            return new JsonConverter();
        }
    };

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
    public void activateOptions()
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
    }


    @Override
    public String format(LoggingEvent event)
    {
        Map<String,Object> map = new TreeMap<String,Object>();
        map.put("timestamp",    new Date(event.getTimeStamp()));
        map.put("thread",       event.getThreadName());
        map.put("logger",       event.getLogger().getName());
        map.put("level",        event.getLevel().toString());
        map.put("message",      event.getRenderedMessage());

        if (event.getThrowableStrRep() != null) map.put("exception",    event.getThrowableStrRep());
        if (event.getNDC() != null)             map.put("ndc",          event.getNDC());
        if (tags != null)                       map.put("tags",         tags);

        if ((event.getProperties() != null) && ! event.getProperties().isEmpty())
        {
            map.put("mdc", event.getProperties());
        }


        if (processId != null)  map.put("processId", processId);
        if (hostname != null)   map.put("hostname", hostname);
        if (instanceId != null) map.put("instanceId", instanceId);

        if (enableLocation)
        {
            LocationInfo info = event.getLocationInformation();
            Map<String,Object> location = new TreeMap<String,Object>();
            location.put("className",  info.getClassName());
            location.put("methodName", info.getMethodName());
            location.put("fileName",   info.getFileName());
            location.put("lineNumber", info.getLineNumber());
            map.put("locationInfo", location);
        }

        String json = converterTL.get().convert(map);
        if (getAppendNewlines())
        {
            json += "\n";
        }

        return json;
    }


    @Override
    public boolean ignoresThrowable()
    {
        return false;
    }
}
