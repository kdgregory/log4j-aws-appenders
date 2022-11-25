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


/**
 *  Base configuration object, representing the config elements managed
 *  by {@link AbstractAppenderBuilder}.
 */
public interface AbstractAppenderConfig
{
    String getName();
    Layout<String> getLayout();
    Filter getFilter();
    Configuration getConfiguration();

    long getBatchDelay();
    boolean getTruncateOversizeMessages();
    int getDiscardThreshold();
    String getDiscardAction();
    boolean isSynchronous();
    boolean isUseShutdownHook();    // yeah, I hate that name too
    long getInitializationTimeout();

    String getAssumedRole();
    String getClientFactory();
    String getClientRegion();
    String getClientEndpoint();
}
