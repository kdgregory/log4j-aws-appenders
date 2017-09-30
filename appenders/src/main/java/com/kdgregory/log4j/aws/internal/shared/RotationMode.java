// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.shared;

import org.apache.log4j.helpers.LogLog;

/**
 *  Defines the ways that we'll rotate logs (for appenders that support rotate).
 */
public enum RotationMode
{
    /** Rotation is disabled. */
    none,

    /** Rotation is based on number of records. */
    count,

    /** Rotation is controlled by the <code>rotationInterval</code> parameter. */
    interval,

    /** Rotation happens with the first message after every hour. */
    hourly,

    /** Rotation happens with the first message after midnight UTC */
    daily;


    /**
     *  Finds the rotation mode corresponding to a passed string (case-insensitive).
     *  Unable to find a matching mode, logs to the Log4J intenal logger and returns
     *  {@link #none}.
     */
    public static RotationMode lookup(String value)
    {
        try
        {
            return RotationMode.valueOf(value.toLowerCase());
        }
        catch (IllegalArgumentException ex)
        {
            LogLog.error("invalid rotationMode: " + value);
            return RotationMode.none;
        }
    }
}