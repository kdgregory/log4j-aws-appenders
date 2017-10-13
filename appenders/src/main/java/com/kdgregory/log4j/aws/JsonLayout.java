// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import org.apache.log4j.Layout;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;

import com.kdgregory.log4j.aws.internal.shared.JsonConverter;


/**
 *  Formats a LogMessage as a JSON string. This is useful when directing the
 *  output of the logger into a search engine such as ElasticSearch.
 *  <p>
 *  The JSON object will always contain the following properties:
 *  <ul>
 *  <li> <code>timestamp</code>:    the date/time that the message was logged.
 *  <li> <code>thread</code>:       the name of the thread where the message was logged.
 *  <li> <code>logger</code>:       the name of the logger.
 *  <li> <code>level</code>:        the level of this log message.
 *  <li> <code>message</code>:      the message itself.
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
 *  appear if specifically requested via configuration:
 *  <ul>
 *  <li> <code>locationInfo</code>: the location where the logger was called. This is a
 *                                  child object with the following components:
 *                                  <ul>
 *                                  <li> <code>className</code>
 *                                  <li> <code>methodName</code>
 *                                  <li> <code>fileName</code>
 *                                  <li> <code>lineNumber</code>
 *                                  </ul>
 *  </ul>
 */
public class JsonLayout
extends Layout
{
//----------------------------------------------------------------------------
//  Configuration
//----------------------------------------------------------------------------

    private boolean enableLocation;


    public void setEnableLocation(boolean value)
    {
        enableLocation = value;
    }


    public boolean getEnableLocation()
    {
        return enableLocation;
    }


//----------------------------------------------------------------------------
//  Layout Overrides
//----------------------------------------------------------------------------

    @Override
    public void activateOptions()
    {
        // no options that we need to activate
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

        if ((event.getProperties() != null) && ! event.getProperties().isEmpty())
        {
            map.put("mdc", event.getProperties());
        }

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

        return JsonConverter.convert(map);
    }


    @Override
    public boolean ignoresThrowable()
    {
        return false;
    }

}
