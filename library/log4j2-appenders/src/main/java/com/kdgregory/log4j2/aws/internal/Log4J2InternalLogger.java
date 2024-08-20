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

package com.kdgregory.log4j2.aws.internal;

import org.apache.logging.log4j.status.StatusLogger;

import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  Logs messages from within the appender.
 *
 *  This class uses a mixture of built-in logging mechanisms: <code>StatusLogger</code>
 *  for non-error conditions, and <code>ErrorHandler</code> for errors. The latter will
 *  limit the number of repetitions of an error message, which is important if there's
 *  a network or related problem that prevents sending messages.
 */
public class Log4J2InternalLogger
implements InternalLogger
{
    private AbstractAppender<?,?,?,?> appender;


    public Log4J2InternalLogger(AbstractAppender<?,?,?,?> appender)
    {
        this.appender = appender;
    }


    @Override
    public void debug(String message)
    {
        StatusLogger.getLogger().debug(message);
    }


    @Override
    public void warn(String message)
    {
        StatusLogger.getLogger().warn(message);
    }


    @Override
    public void warn(String message, Throwable ex)
    {
        StatusLogger.getLogger().warn(message, ex);
    }


    @Override
    public void error(String message, Throwable ex)
    {
        if (appender != null)   appender.error(message, ex);
        else                    StatusLogger.getLogger().error(message, ex);
    }
}
