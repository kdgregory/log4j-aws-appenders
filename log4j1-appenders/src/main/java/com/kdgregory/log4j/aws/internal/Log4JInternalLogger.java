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

package com.kdgregory.log4j.aws.internal;

import org.apache.log4j.helpers.LogLog;

import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  An implementation of <code>InternalLogger</code> that passes all messages
 *  to the Log4J <code>LogLog</code> object.
 *  <p>
 *  All log messages are prefixed with a combination of appender type (eg,
 *  "cloudwatch") and appender name. The former is provided during construction
 *  (and may not be an actual appender, see JMXManager); the latter whenever the
 *  appender's name is changed (which should only happen during initialization).
 */
public class Log4JInternalLogger
implements InternalLogger
{
    private String appenderType;
    private String messagePrefix;


    public Log4JInternalLogger(String appenderType)
    {
        this.appenderType = appenderType;
        this.messagePrefix = appenderType + ": ";
    }


    /**
     *  Called by <code>Appender.setName()</code>, to add the appender's name into the
     *  message prefix.
     */
    public void setAppenderName(String name)
    {
        if ((name != null) && (! name.isEmpty()))
        {
            messagePrefix = appenderType + "(" + name + "): ";
        }
    }


    @Override
    public void debug(String message)
    {
        LogLog.debug(messagePrefix + message);
    }


    @Override
    public void warn(String message)
    {
        LogLog.warn(messagePrefix + message);
    }


    @Override
    public void error(String message, Throwable ex)
    {
        if (ex != null)
            LogLog.error(messagePrefix + message, ex);
        else
            LogLog.error(messagePrefix + message);
    }

}
