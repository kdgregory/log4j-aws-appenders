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

import com.kdgregory.logging.common.util.DiscardAction;


/**
 *  Holds common configuration; writer-specific config objects are subclasses.
 *  See documentation pages for information about specific properties.
 */
public class AbstractWriterConfig<T extends AbstractWriterConfig<T>>
{
    private volatile boolean        truncateOversizeMessages;
    private volatile long           batchDelay;
    private volatile int            discardThreshold;
    private volatile DiscardAction  discardAction;
    private String                  clientFactoryMethod;
    private String                  assumedRole;
    private String                  clientRegion;
    private String                  clientEndpoint;


    public boolean getTruncateOversizeMessages()
    {
        return truncateOversizeMessages;
    }

    public T setTruncateOversizeMessages(boolean value)
    {
        truncateOversizeMessages = value;
        return (T)this;
    }


    public long getBatchDelay()
    {
        return batchDelay;
    }

    public T setBatchDelay(long value)
    {
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
}
