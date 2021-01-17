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

import com.kdgregory.logging.aws.internal.AbstractJMXManager;

import ch.qos.logback.core.spi.ContextAware;


/**
 *  This class ensures that appenders will be registered with any MBean servers
 *  where a StatisticsMBean instance has also been registered, regardless of
 *  the order that those objects are initialized/registered.
 *  <p>
 *  This object is implemented as an eagerly-instantiated singleton; for testing
 *  the implementation can be replaced. All methods are synchronized, to avoid
 *  race conditions between appender and StatisticsMBean registration.
 */
public class JMXManager
extends AbstractJMXManager
{
    private static JMXManager singleton;
    private static LateInitializedLogbackInternalLogger singletonLogger;


    /**
     *  Retrieves the singleton instance for a consumer that isn't itself
     *  context-aware, lazily instantiating.
     */
    public static synchronized JMXManager getInstance()
    {
        if (singleton == null)
        {
            singletonLogger = new LateInitializedLogbackInternalLogger("JMXManager");
            singleton = new JMXManager(singletonLogger);
        }
        return (JMXManager)singleton;
    }


    /**
     *  Retrieves the singleton instance for a consumer that is context-aware,
     *  lazily instantiating.
     */
    public static synchronized JMXManager getInstance(ContextAware destination)
    {
        getInstance();
        singletonLogger.setDestination(destination.getContext());
        return singleton;
    }


    /**
     *  Resets the singleton instance. This is intended for testing.
     */
    public static synchronized void reset()
    {
        singleton = null;
    }


    private JMXManager(LateInitializedLogbackInternalLogger internalLogger)
    {
        super(internalLogger);
    }

}
