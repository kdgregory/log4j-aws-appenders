// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.shared;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;


/**
 *  Handles the standard substitution variables.
 *  <p>
 *  This is an instantiable class to support testing: the default constructor
 *  uses "real" values (eg, the current date), while the base constructor allows
 *  the caller to specify test values.
 *  <p>
 *  Instances are thread-safe.
 */
public class Substitutions
{
    private String date;
    private String timestamp;
    private String startupTimestamp;


    /**
     *  Constructs an instance with specified substitution values.
     */
    public Substitutions(Date curremtDate)
    {
        RuntimeMXBean runtimeMx = ManagementFactory.getRuntimeMXBean();
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        date = dateFormat.format(curremtDate);

        SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        timestampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        timestamp = timestampFormat.format(curremtDate);
        startupTimestamp = timestampFormat.format(new Date(runtimeMx.getStartTime()));
    }


    /**
     *  Constructs an instance with real substitution values.
     */
    public Substitutions()
    {
        this(new Date());
    }


    /**
     *  Applies all substitutions.
     */
    public String perform(String input)
    {
        return substituteDate(
               substituteTimestamp(
               substituteStartupTimestamp(input)));
    }


    private String substituteDate(String input)
    {
        int index = input.indexOf("{date}");
        return (index >= 0)
             ? perform(input.substring(0, index) + date + input.substring(index + 6, input.length()))
             : input;
    }


    private String substituteTimestamp(String input)
    {
        int index = input.indexOf("{timestamp}");
        return (index >= 0)
             ? perform(input.substring(0, index) + timestamp + input.substring(index + 11, input.length()))
             : input;
    }


    private String substituteStartupTimestamp(String input)
    {
        int index = input.indexOf("{startupTimestamp}");
        return (index >= 0)
             ? perform(input.substring(0, index) + startupTimestamp + input.substring(index + 18, input.length()))
             : input;
    }
}
