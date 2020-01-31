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
import org.apache.logging.log4j.core.util.Builder;

import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.common.util.RotationMode;


/**
 *  Manages the configuration elements that are common across appenders.
 */
public abstract class AbstractAppenderBuilder<T>
implements Builder<T>, AbstractAppenderConfig
{
        @PluginElement("Layout")
        private Layout<String> layout;

        @Override
        public Layout<String> getLayout()
        {
            return layout;
        }

        public void setLayout(Layout<String> value)
        {
            this.layout = value;
        }

        @PluginElement("Filter")
        private Filter filter;

        @Override
        public Filter getFilter()
        {
            return filter;
        }

        public void setFilter(Filter value)
        {
            this.filter = value;
        }

        @PluginBuilderAttribute("batchDelay")
        private long batchDelay = 2000;

        @Override
        public long getBatchDelay()
        {
            return isSynchronous() ? 0 : batchDelay;
        }

        public void setBatchDelay(long value)
        {
            this.batchDelay = value;
        }

        @PluginBuilderAttribute("discardThreshold")
        private int discardThreshold = 10000;

        @Override
        public int getDiscardThreshold()
        {
            return discardThreshold;
        }

        public void setDiscardThreshold(int value)
        {
            this.discardThreshold = value;
        }

        @PluginBuilderAttribute("discardAction")
        private String discardAction = DiscardAction.oldest.name();

        @Override
        public String getDiscardAction()
        {
            return discardAction;
        }

        public void setDiscardAction(String value)
        {
            this.discardAction = value;
        }

        @PluginBuilderAttribute("clientFactory")
        private String clientFactoryMethod;

        @Override
        public String getClientFactory()
        {
            return clientFactoryMethod;
        }

        public void setClientFactory(String value)
        {
            this.clientFactoryMethod = value;
        }

        @PluginBuilderAttribute("clientRegion")
        private String clientRegion;

        @Override
        public String getClientRegion()
        {
            return clientRegion;
        }

        public void setClientRegion(String value)
        {
            this.clientRegion = value;
        }

        @PluginBuilderAttribute("clientEndpoint")
        private String clientEndpoint;

        @Override
        public String getClientEndpoint()
        {
            return clientEndpoint;
        }

        public void setClientEndpoint(String value)
        {
            this.clientEndpoint = value;
        }

        @PluginBuilderAttribute("synchronous")
        private boolean synchronous;

        @Override
        public boolean isSynchronous()
        {
            return synchronous;
        }

        public void setSynchronous(boolean value)
        {
            this.synchronous = value;
        }

        @PluginBuilderAttribute("useShutdownHook")
        private boolean useShutdownHook = true;

        @Override
        public boolean isUseShutdownHook()
        {
            return useShutdownHook;
        }

        public void setUseShutdownHook(boolean value)
        {
            this.useShutdownHook = value;
        }

        // these return default values here, are implemented by appenders that uses them
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
