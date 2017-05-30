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
    private RuntimeMXBean runtimeMx;
    private String date;
    private String timestamp;
    private String startupTimestamp;


    /**
     *  Constructs an instance with specified substitution values.
     */
    public Substitutions(Date curremtDate)
    {
        runtimeMx = ManagementFactory.getRuntimeMXBean();

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
               substituteStartupTimestamp(
               substituteProcessId(
               substituteHostname(input)))));
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


    private String substituteProcessId(String input)
    {
        int index = input.indexOf("{pid}");
        if (index >= 0)
        {
            String vmName = runtimeMx.getName();
            String pid = (vmName.indexOf('@') > 0)
                       ? vmName.substring(0, vmName.indexOf('@'))
                       : "unknown";
            return perform(input.substring(0, index) + pid + input.substring(index + 5, input.length()));
        }
        return input;
    }


    private String substituteHostname(String input)
    {
        int index = input.indexOf("{hostname}");
        if (index >= 0)
        {
            String vmName = runtimeMx.getName();
            String hostname = (vmName.indexOf('@') > 0)
                       ? vmName.substring(vmName.indexOf('@') + 1, vmName.length())
                       : "unknown";
            return perform(input.substring(0, index) + hostname + input.substring(index + 10, input.length()));
        }
        return input;
    }
}
