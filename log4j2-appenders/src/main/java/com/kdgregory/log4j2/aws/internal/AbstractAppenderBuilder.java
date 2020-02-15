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

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;

import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.common.util.RotationMode;


/**
 * Manages the configuration elements that are common across appenders.
 */
public abstract class AbstractAppenderBuilder<T extends AbstractAppenderBuilder<T>>
implements AbstractAppenderConfig
{
    @PluginElement("Layout")
    private Layout<String> layout;

    @Override
    public Layout<String> getLayout()
    {
        return layout;
    }

    public T setLayout(Layout<String> value)
    {
        this.layout = value;
        return (T)this;
    }


    @PluginElement("Filter")
    private Filter filter;

    @Override
    public Filter getFilter()
    {
        return filter;
    }

    public T setFilter(Filter value)
    {
        this.filter = value;
        return (T)this;
    }


    @PluginBuilderAttribute("batchDelay")
    private long batchDelay = 2000;

    @Override
    public long getBatchDelay()
    {
        return isSynchronous() ? 0 : batchDelay;
    }

    public T setBatchDelay(long value)
    {
        this.batchDelay = value;
        return (T)this;
    }


    @PluginBuilderAttribute("discardThreshold")
    private int discardThreshold = 10000;

    @Override
    public int getDiscardThreshold()
    {
        return discardThreshold;
    }

    public T setDiscardThreshold(int value)
    {
        this.discardThreshold = value;
        return (T)this;
    }


    @PluginBuilderAttribute("discardAction")
    private String discardAction = DiscardAction.oldest.name();

    @Override
    public String getDiscardAction()
    {
        return discardAction;
    }

    public T setDiscardAction(String value)
    {
        this.discardAction = value;
        return (T)this;
    }


    @PluginBuilderAttribute("clientFactory")
    private String clientFactoryMethod;

    @Override
    public String getClientFactory()
    {
        return clientFactoryMethod;
    }

    public T setClientFactory(String value)
    {
        this.clientFactoryMethod = value;
        return (T)this;
    }


    @PluginBuilderAttribute("clientRegion")
    private String clientRegion;

    @Override
    public String getClientRegion()
    {
        return clientRegion;
    }

    public T setClientRegion(String value)
    {
        this.clientRegion = value;
        return (T)this;
    }


    @PluginBuilderAttribute("clientEndpoint")
    private String clientEndpoint;

    @Override
    public String getClientEndpoint()
    {
        return clientEndpoint;
    }

    public T setClientEndpoint(String value)
    {
        this.clientEndpoint = value;
        return (T)this;
    }


    @PluginBuilderAttribute("synchronous")
    private boolean synchronous;

    @Override
    public boolean isSynchronous()
    {
        return synchronous;
    }

    public T setSynchronous(boolean value)
    {
        this.synchronous = value;
        return (T)this;
    }


    @PluginBuilderAttribute("useShutdownHook")
    private boolean useShutdownHook = true;

    @Override
    public boolean isUseShutdownHook()
    {
        return useShutdownHook;
    }

    public T setUseShutdownHook(boolean value)
    {
        this.useShutdownHook = value;
        return (T)this;
    }

    // the following getters return default values here, are overridden by appenders that use them

    @Override
    public int getSequence()
    {
        return 0;
    }


    @Override
    public String getRotationMode()
    {
        return RotationMode.none.name();
    }


    @Override
    public long getRotationInterval()
    {
        return -1;
    }
}
