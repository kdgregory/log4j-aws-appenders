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

/**
 *  TODO - implement
 */
public class LogbackInternalLogger
implements InternalLogger
{
    private String appenderName;
    
    public LogbackInternalLogger(String appenderName)
    {
        this.appenderName = appenderName;
    }
    
    public void setAppenderName(String loggerName)
    {
        this.appenderName = loggerName;
    }
    
    
    @Override
    public void debug(String message)
    {
        // TODO - implement
    }

    @Override
    public void warn(String message)
    {
        // TODO - implement
    }

    @Override
    public void error(String message, Throwable ex)
    {
        // TODO - implement
    }
}
