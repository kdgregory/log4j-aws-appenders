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

import ch.qos.logback.core.Context;
import ch.qos.logback.core.spi.ContextAware;
import ch.qos.logback.core.status.*;


/**
 *  This object is used to create a {@link LogbackInternalLogger} instance for
 *  non-appender-related logging. It is constructed with a <code>Context</code>
 *  instance (how we get that is situation-dependent) and a name that's used
 *  for log messages.
 */
public class SimpleContextAware
implements ContextAware
{
    private Context context;
    private String originName;


    public SimpleContextAware(Context context, String originName)
    {
        this.context = context;
        this.originName = originName;
    }


    @Override
    public void setContext(Context value)
    {
        this.context = value;
    }


    @Override
    public Context getContext()
    {
        return context;
    }


    @Override
    public void addStatus(Status status)
    {
        StatusManager sm = (context != null) ? context.getStatusManager() : null;
        if (sm != null)
            sm.add(status);
    }


    @Override
    public void addInfo(String msg)
    {
        addStatus(new InfoStatus(msg, originName));
    }


    @Override
    public void addInfo(String msg, Throwable ex)
    {
        addStatus(new InfoStatus(msg, originName, ex));
    }


    @Override
    public void addWarn(String msg)
    {
        addStatus(new WarnStatus(msg, originName));
    }


    @Override
    public void addWarn(String msg, Throwable ex)
    {
        addStatus(new WarnStatus(msg, originName, ex));
    }


    @Override
    public void addError(String msg)
    {
        addStatus(new ErrorStatus(msg, originName));
    }


    @Override
    public void addError(String msg, Throwable ex)
    {
        addStatus(new ErrorStatus(msg, originName, ex));
    }
}
