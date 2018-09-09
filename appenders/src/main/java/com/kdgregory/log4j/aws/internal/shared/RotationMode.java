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
