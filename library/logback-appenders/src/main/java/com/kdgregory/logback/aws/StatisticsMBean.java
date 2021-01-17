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

package com.kdgregory.logback.aws;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.kdgregory.logback.aws.internal.JMXManager;
import com.kdgregory.logging.aws.internal.AbstractMarkerBean;


/**
 *  This class provides a bridge between the writer statistics exposed by an
 *  appender and an MBeanServer. When an instance of this class has been
 *  registered, it will in turn register all statistics beans loaded by the
 *  same classloader hierarchy.
 *  <p>
 *  <h1>Example</strong>
 *  <pre>
 *      ManagementFactory.getPlatformMBeanServer().createMBean(
 *              StatisticsMBean.class.getName(),
 *              new ObjectName("ch.qos.logback.classic:name=Statistics"));
 *  </pre>
 *  Note that the object names for this bean can be whatever you want. For the
 *  example I picked a name that will ensure that the statistics are grouped
 *  with the Logback configuration beans.
 */
public class StatisticsMBean
extends AbstractMarkerBean
{

    @Override
    protected void onRegister(MBeanServer server, ObjectName beanName)
    {
        JMXManager.getInstance().addMarkerBean(this, myServer, myName);
    }


    @Override
    protected void onDeregister(MBeanServer server, ObjectName beanName)
    {
        JMXManager.getInstance().removeMarkerBean(this);
    }
}
