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

package com.kdgregory.logback.aws.internal;

import com.kdgregory.logging.common.util.InternalLogger;

import ch.qos.logback.core.spi.ContextAware;


/**
 *  An implementation of <code>InternalLogger</code> that passes all messages
 *  to the Logback status buffer.
 *  <p>
 *  Instances are provided with the destination for log messages. This is normally
 *  the appender itself, and the status buffer will use the appender's class and
 *  name in all logging messages. For non-appender use (eg, JMXManager), you will
 *  need to provide a destination.
 */
public class LogbackInternalLogger
implements InternalLogger
{
    private ContextAware destination;


    public LogbackInternalLogger(ContextAware destination)
    {
        this.destination = destination;
    }


    @Override
    public void debug(String message)
    {
        destination.addInfo(message);
    }


    @Override
    public void warn(String message)
    {
        destination.addWarn(message);
    }


    @Override
    public void error(String message, Throwable ex)
    {
        if (ex != null)
            destination.addError(message, ex);
        else
            destination.addError(message);
    }
}
