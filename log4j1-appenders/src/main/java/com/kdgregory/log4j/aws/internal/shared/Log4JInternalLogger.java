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

import com.kdgregory.aws.logging.internal.InternalLogger;


/**
 *  An implementation of <code>InternalLogger</code> that passes all messages
 *  to the Log4J <code>LogLog</code> object.s
 */
public class Log4JInternalLogger implements InternalLogger
{
    @Override
    public void debug(String message)
    {
        LogLog.debug(message);
    }


    @Override
    public void error(String message, Throwable ex)
    {
        if (ex != null)
            LogLog.error(message, ex);
        else
            LogLog.error(message);
    }

}
