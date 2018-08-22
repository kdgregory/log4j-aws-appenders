# JMX Support

The appenders will optionally register JMX "statistics beans" with one or more MBeanServers.
These beans provide the following information:

* The post-substitution name of the destination (stream/topic).
* The number of messages that have been successfully sent to the destination.
* The number of messages that have been discarded due to inability to send.
* The most recent error (initialization or runtime), with timestamp and stacktrace.


## Example

This is a screenshot of JConsole, running the example application with just the CloudWatch appender.

![jconsole mbean view](jmx.png)

Under the top-level `log4j` key there are sub-trees for the two registered beans (see below), along
with the root logger and each appender (by name).

Under each appender are the exposed attributes and operations for that appender (you can check your
configuration via the attributes), as well as a bean for the appender's layout manager. For the AWS
appenders, there is also a `statistics` bean: its attribute show the appender's reported statistics.


## Enabling

To enable JMX reporting you must register an instance of `com.kdgregory.log4j.aws.StatisticsMBean`
with one or more MBeanServers. At the same time, you will probably want to register Log4J's
`HierarchyDynamicMBean` so that you can examine and update your application's loggers.

```
ManagementFactory.getPlatformMBeanServer().createMBean(
        HierarchyDynamicMBean.class.getName(),
        new ObjectName("log4j:name=Config"));

ManagementFactory.getPlatformMBeanServer().createMBean(
        StatisticsMBean.class.getName(),
        new ObjectName("log4j:name=Statistics"));
```

The `ObjectName` that you use to register these beans can be anything. However, the statistics beans
and Log4J's appender and layout beans assume that the domain will be `log4j`, so that's what I use.

The example program, as a stand-alone application, uses the JVM's platform MBeanServer. Depending on
the framework that your application uses, you may want to register with a framework-supplied server
(for example, Spring provides its own server and expects you to
[register your JMX beans](https://docs.spring.io/spring/docs/current/spring-framework-reference/integration.html#jmx)
with the rest of your beans).

If your application explicitly unregisters the `StatisticsMBean`, then the appenders will unregister
themselves from the same server(s). The appenders will also unregister themselves when closed. Normally
you'd never do this: appenders only get closed when the application shuts down or is unloaded.


## Classpath Isolation

Internally, the appenders library maintains the associations between StatisticMBeans, appender MBeans,
and MBeanServers in statically-referenced data structures. This is necessary to avoid the inevitable
race conditions between appender creation and `StatisticMBean` registration without creating explicit
links between them.

To avoid strange JMX behavior -- for example, beans registered under one web-app but not another
running in the same server -- you should load the appenders library in the same classloader as Log4J.
You should do this anyway, to avoid Log4J configuration errors.
