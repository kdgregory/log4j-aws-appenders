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

package com.kdgregory.log4j.aws.internal;

import com.kdgregory.logging.common.jmx.AbstractJMXManager;


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

    /**
     *  Lazily instantiates the singleton instance.
     */
    public static synchronized JMXManager getInstance()
    {
        if (singleton == null)
            singleton = new JMXManager();
        return (JMXManager)singleton;
    }


    /**
     *  Resets the singleton instance. This is intended for testing.
     */
    public static synchronized void reset()
    {
        singleton = null;
    }


    private JMXManager()
    {
        super(new Log4JInternalLogger("JMXManager"));
    }
}
