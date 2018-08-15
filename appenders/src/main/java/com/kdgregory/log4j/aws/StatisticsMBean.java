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

package com.kdgregory.log4j.aws;


/**
 *  This class provides a bridge between an appenders and an MBeanServer. When
 *  an instance of this class has been registered, it will in turn register all
 *  appenders loaded by the same classloader hierarchy.
 *  <p>
 *  <h1>Example</strong>
 *  <p>
 *  <strong>Support for multiple Log4J hierarchies</strong>
 *  <p>
 *  For a stand-alone application, you would typically register both the Log4J
 *  <code>HierarchyDynamicMBean</code> and this bean with the platform MBean
 *  server. For a multi-container deployment such as an app-server, however,
 *  you may have a container-specific MBean server. However, there's no simple
 *  way to identify which of possibly many MBean servers may be the one we use
 *  to register, or if indeed we should register.
 *  <p>
 *  The solution I've chosen is to maintain two static collections: one holds
 *  registrations for instances of this class, the other holds references to
 *  each of the configured appenders. When one or the other of these collections
 *  changes then the appropriate JMX registrations/deregistrations are performed.
 *  <p>
 *  If you never register an instance of this class, then JMX is effectively
 *  disabled.
 */
public class StatisticsMBean
{

}
