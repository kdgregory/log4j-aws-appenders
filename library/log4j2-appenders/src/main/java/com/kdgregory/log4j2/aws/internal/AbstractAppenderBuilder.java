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
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginConfiguration;
import org.apache.logging.log4j.core.config.plugins.PluginElement;

import com.kdgregory.logging.aws.internal.AbstractWriterConfig;


/**
 *  Manages the configuration elements that are common across appenders.
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


    @PluginConfiguration
    private Configuration configuration;

    @Override
    public Configuration getConfiguration()
    {
        return configuration;
    }

    public void setConfiguration(Configuration configuration)
    {
        this.configuration = configuration;
    }


    @PluginBuilderAttribute("synchronous")
    private boolean synchronous = AbstractWriterConfig.DEFAULT_IS_SYNCHRONOUS;

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


    @PluginBuilderAttribute("batchDelay")
    private long batchDelay = AbstractWriterConfig.DEFAULT_BATCH_DELAY;

    /**
     *  Sets the <code>batchDelay</code> configuration property.
     */
    public T setBatchDelay(long value)
    {
        this.batchDelay = value;
        return (T)this;
    }

    /**
     *  Returns the <code>batchDelay</code> configuration property.
     */
    @Override
    public long getBatchDelay()
    {
        return isSynchronous() ? 0 : batchDelay;
    }


    @PluginBuilderAttribute("truncateOversizeMessages")
    private boolean truncateOversizeMessages = AbstractWriterConfig.DEFAULT_TRUNCATE_OVERSIZE;


    /**
     *  Sets the <code>truncateOversizeMessages</code> configuration property.
     */
    public void setTruncateOversizeMessages(boolean value)
    {
        this.truncateOversizeMessages = value;
    }


    /**
     *  Returns the <code>truncateOversizeMessages</code> configuration property.
     */
    @Override
    public boolean getTruncateOversizeMessages()
    {
        return this.truncateOversizeMessages;
    }


    @PluginBuilderAttribute("discardThreshold")
    private int discardThreshold = AbstractWriterConfig.DEFAULT_DISCARD_THRESHOLD;

    /**
     *  Sets the <code>discardThreshold</code> configuration property.
     */
    public T setDiscardThreshold(int value)
    {
        this.discardThreshold = value;
        return (T)this;
    }

    /**
     *  Retyrns the <code>discardThreshold</code> configuration property.
     */
    @Override
    public int getDiscardThreshold()
    {
        return discardThreshold;
    }


    @PluginBuilderAttribute("discardAction")
    private String discardAction = AbstractWriterConfig.DEFAULT_DISCARD_ACTION.name();

    /**
     *  Sets the <code>discardAction</code> configuration property.
     */
    public T setDiscardAction(String value)
    {
        this.discardAction = value;
        return (T)this;
    }

    /**
     *  Returns the <code>discardAction</code> configuration property.
     */
    @Override
    public String getDiscardAction()
    {
        return discardAction;
    }


    @PluginBuilderAttribute("assumedRole")
    private String assumedRole;

    /**
     *  Sets the <code>clientFactory</code> configuration property.
     */
    public T setAssumedRole(String value)
    {
        this.assumedRole = value;
        return (T)this;
    }

    /**
     *  Returns the <code>clientFactory</code> configuration property.
     */
    @Override
    public String getAssumedRole()
    {
        return assumedRole;
    }


    @PluginBuilderAttribute("clientFactory")
    private String clientFactoryMethod;

    /**
     *  Sets the <code>clientFactory</code> configuration property.
     */
    public T setClientFactory(String value)
    {
        this.clientFactoryMethod = value;
        return (T)this;
    }

    /**
     *  Returns the <code>clientFactory</code> configuration property.
     */
    @Override
    public String getClientFactory()
    {
        return clientFactoryMethod;
    }


    @PluginBuilderAttribute("clientRegion")
    private String clientRegion;

    /**
     *  Sets the <code>clientRegion</code> configuration property.
     */
    public T setClientRegion(String value)
    {
        this.clientRegion = value;
        return (T)this;
    }

    /**
     *  Returns the <code>clientRegion</code> configuration property.
     */
    @Override
    public String getClientRegion()
    {
        return clientRegion;
    }


    @PluginBuilderAttribute("clientEndpoint")
    private String clientEndpoint;

    /**
     *  Sets the <code>clientEndpoint</code> configuration property.
     */
    public T setClientEndpoint(String value)
    {
        this.clientEndpoint = value;
        return (T)this;
    }

    /**
     *  Returns the <code>clientEndpoint</code> configuration property.
     */
    @Override
    public String getClientEndpoint()
    {
        return clientEndpoint;
    }


    @PluginBuilderAttribute("useShutdownHook")
    private boolean useShutdownHook = AbstractWriterConfig.DEFAULT_USE_SHUTDOWN_HOOK;

    /**
     *  Sets the <code>useShutdownHook</code> configuration property.
     */
    public T setUseShutdownHook(boolean value)
    {
        this.useShutdownHook = value;
        return (T)this;
    }

    /**
     *  Returns the <code>useShutdownHook</code> configuration property.
     */
    @Override
    public boolean isUseShutdownHook()
    {
        return useShutdownHook;
    }


    @PluginBuilderAttribute("initializationTimeout")
    private long initializationTimeout = 60000; // will be overridden by subclasses

    /**
     *  Sets the <code>initializationTimeout</code> configuration property.
     */
    public T setInitializationTimeout(long value)
    {
        this.initializationTimeout = value;
        return (T)this;
    }

    /**
     *  Returns the <code>initializationTimeout</code> configuration property.
     */
    @Override
    public long getInitializationTimeout()
    {
        return initializationTimeout;
    }


    @PluginBuilderAttribute("enableBatchLogging")
    private boolean enableBatchLogging = AbstractWriterConfig.DEFAULT_ENABLE_BATCH_LOGGING;

    /**
     *  Sets the <code>enableBatchLogging</code> configuration property.
     */
    public T setEnableBatchLogging(boolean value)
    {
        this.enableBatchLogging = value;
        return (T)this;
    }

    /**
     *  Returns the <code>enableBatchLogging</code> configuration property.
     */
    @Override
    public boolean isEnableBatchLogging()
    {
        return enableBatchLogging;
    }
}
