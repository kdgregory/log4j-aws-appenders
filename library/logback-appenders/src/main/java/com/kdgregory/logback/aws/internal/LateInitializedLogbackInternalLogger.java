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

import java.util.concurrent.ConcurrentLinkedQueue;

import com.kdgregory.logging.common.util.InternalLogger;

import ch.qos.logback.core.Context;
import ch.qos.logback.core.spi.ContextAware;
import ch.qos.logback.core.spi.ContextAwareImpl;
import ch.qos.logback.core.status.ErrorStatus;
import ch.qos.logback.core.status.InfoStatus;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.WarnStatus;


/**
 *  An implementation of <code>InternalLogger</code> that passes all messages
 *  to the Logback status buffer. Unlike the base {@link LogbackInternalLogger},
 *  this class is instantiated without a destination, and queues messages until
 *  the destination is set. It's intended to support {@link JMXManager}, which
 *  may be created before the logging context.
 */
public class LateInitializedLogbackInternalLogger
implements InternalLogger
{
    private volatile ContextAware destination;
    private String origin;

    private ConcurrentLinkedQueue<Status> deferredMessages = new ConcurrentLinkedQueue<Status>();


    public LateInitializedLogbackInternalLogger(String origin)
    {
        this.origin = origin;
    }


    /**
     *  Sets the logger's destination, if one has not already been set.
     */
    public synchronized void setDestination(Context context)
    {
        // no need to overwrite
        if (destination != null)
            return;

        destination = new ContextAwareImpl(context, origin);
        for (Status message : deferredMessages)
        {
            destination.addStatus(message);
        }
    }


    @Override
    public void debug(String message)
    {
        Status status = new InfoStatus(message, origin);
        if (destination == null)
            deferredMessages.add(status);
        else
            destination.addStatus(status);
    }


    @Override
    public void warn(String message)
    {
        Status status = new WarnStatus(message, origin);
        if (destination == null)
            deferredMessages.add(status);
        else
            destination.addStatus(status);
    }


    @Override
    public void warn(String message, Throwable ex)
    {
        Status status = (ex != null)
                      ? new WarnStatus(message, origin, ex)
                      : new WarnStatus(message, origin);
        if (destination == null)
            deferredMessages.add(status);
        else
            destination.addStatus(status);
    }


    @Override
    public void error(String message, Throwable ex)
    {
        Status status = (ex != null)
                      ? new ErrorStatus(message, origin, ex)
                      : new ErrorStatus(message, origin);

        if (destination == null)
            deferredMessages.add(status);
        else
            destination.addStatus(status);
    }
}
