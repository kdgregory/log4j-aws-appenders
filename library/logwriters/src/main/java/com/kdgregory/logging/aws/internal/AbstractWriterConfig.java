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

package com.kdgregory.logging.aws.internal;

import com.kdgregory.logging.common.util.MessageQueue.DiscardAction;


/**
 *  Holds common configuration; writer-specific config objects are subclasses.
 *  See documentation pages for information about specific properties.
 *  <p>
 *  Note: some fields are marked volatile, because they may be changed during
 *  runtime.
 */
public class AbstractWriterConfig<T extends AbstractWriterConfig<T>>
implements Cloneable
{
    public final static boolean         DEFAULT_TRUNCATE_OVERSIZE   = true;
    public final static boolean         DEFAULT_IS_SYNCHRONOUS      = false;    // making this explicit
    public final static long            DEFAULT_BATCH_DELAY         = 2000;
    public final static int             DEFAULT_DSICARD_THRESHOLD   = 10000;
    public final static DiscardAction   DEFAULT_DISCARD_ACTION      = DiscardAction.oldest;
    public final static boolean         DEFAULT_USE_SHUTDOWN_HOOK   = true;


    private boolean                     truncateOversizeMessages    = DEFAULT_TRUNCATE_OVERSIZE;
    private boolean                     isSynchronous               = DEFAULT_IS_SYNCHRONOUS;
    private volatile long               batchDelay                  = DEFAULT_BATCH_DELAY;
    private volatile int                discardThreshold            = DEFAULT_DSICARD_THRESHOLD;
    private volatile DiscardAction      discardAction               = DEFAULT_DISCARD_ACTION;
    private String                      clientFactoryMethod;
    private String                      assumedRole;
    private String                      clientRegion;
    private String                      clientEndpoint;
    private boolean                     useShutdownHook             = DEFAULT_USE_SHUTDOWN_HOOK;


    @Override
    public AbstractWriterConfig<T> clone()
    {
        try
        {
            return (AbstractWriterConfig<T>)super.clone();
        }
        catch (CloneNotSupportedException e)
        {
            throw new RuntimeException("failed to expose Object.clone(); should never happen", e);
        }
    }


    public boolean getTruncateOversizeMessages()
    {
        return truncateOversizeMessages;
    }

    public T setTruncateOversizeMessages(boolean value)
    {
        truncateOversizeMessages = value;
        return (T)this;
    }


    public boolean getSynchronousMode()
    {
        return isSynchronous;
    }

    public T setSynchronousMode(boolean value)
    {
        isSynchronous = value;

        if (isSynchronous)
        {
            batchDelay = 0;
        }
        else if (batchDelay == 0)
        {
            batchDelay = DEFAULT_BATCH_DELAY;
        }

        return (T)this;
    }


    public long getBatchDelay()
    {
        return batchDelay;
    }

    public T setBatchDelay(long value)
    {
        if (isSynchronous)
        {
            value = 0;
        }
        batchDelay = value;
        return (T)this;
    }


    public int getDiscardThreshold()
    {
        return discardThreshold;
    }

    public T setDiscardThreshold(int value)
    {
        discardThreshold = value;
        return (T)this;
    }


    public DiscardAction getDiscardAction()
    {
        return discardAction;
    }

    public T setDiscardAction(DiscardAction value)
    {
        discardAction = value;
        return (T)this;
    }


    public String getClientFactoryMethod()
    {
        return clientFactoryMethod;
    }

    public T setClientFactoryMethod(String value)
    {
        clientFactoryMethod = value;
        return (T)this;
    }


    public String getAssumedRole()
    {
        return assumedRole;
    }

    public T setAssumedRole(String value)
    {
        assumedRole = value;
        return (T)this;
    }


    public String getClientRegion()
    {
        return clientRegion;
    }

    public T setClientRegion(String value)
    {
        clientRegion = value;
        return (T)this;
    }


    public String getClientEndpoint()
    {
        return clientEndpoint;
    }

    public T setClientEndpoint(String value)
    {
        clientEndpoint = value;
        return (T)this;
    }


    public boolean getUseShutdownHook()
    {
        return useShutdownHook;
    }

    public T setUseShutdownHook(boolean value)
    {
        useShutdownHook = value;
        return (T)this;
    }
}
