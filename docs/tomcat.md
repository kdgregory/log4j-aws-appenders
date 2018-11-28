# Tomcat Dangling Thread Warnings

You may see the following warning in the Tomcat logs when you undeploy an application that uses
the AWS appenders:

```
16-Nov-2018 21:15:57.834 WARNING [http-nio-8080-exec-37] org.apache.catalina.loader.WebappClassLoaderBase.clearReferencesThreads The web application [log4j1-aws-appenders-webapp-2.1.0-SNAPSHOT] appears to have started a thread named [com-kdgregory-aws-logwriter-log4j-cloudwatch-0] but has failed to stop it. This is very likely to create a memory leak. Stack trace of thread:
 sun.misc.Unsafe.park(Native Method)
 java.util.concurrent.locks.LockSupport.parkNanos(LockSupport.java:215)
 java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.awaitNanos(AbstractQueuedSynchronizer.java:2078)
 java.util.concurrent.LinkedBlockingDeque.pollFirst(LinkedBlockingDeque.java:522)
 java.util.concurrent.LinkedBlockingDeque.poll(LinkedBlockingDeque.java:684)
 com.kdgregory.logging.common.util.MessageQueue.dequeue(MessageQueue.java:174)
 com.kdgregory.logging.aws.internal.AbstractLogWriter.buildBatch(AbstractLogWriter.java:312)
 com.kdgregory.logging.aws.internal.AbstractLogWriter.run(AbstractLogWriter.java:180)
 java.lang.Thread.run(Thread.java:748)
```


## Why this happens

When Tomcat undeploys an application, it essentially releases all references to that
application's classes and data, and relies on the JVM garbage collector to remove the
application from memory. However, threads started by the application can interfere with
this process: they hold references to application objects, which in turn hold references
to the application's class(es), which prevent the JVM from collecting those classes. For
this reason, when Tomcat undeploys a web application it examines each running thread to
determine whether it was started by that application, and warns you if it finds any.

If you just use Log4J with a `FileAppender` or `ConsoleAppender`, there are no threads to
shut down, so you don't need to take any explicit action when undeploying an application.
However, the AWS appenders use a background thread to communicate with AWS, and this
background thread runs as long as the appender is alive. To stop it you need to explicitly
shut down the Log4J framework.


## Solution #1: Use a Context Listener

[Context listeners](https://docs.oracle.com/javaee/6/api/javax/servlet/ServletContextListener.html)
are classes that are invoked when an application is deployed or undeployed. To shut
down Log4J requires a single line in the listener's `contextDestroyed()` method:

```
public class ExampleContextListener
implements ServletContextListener
{
    @Override
    public void contextInitialized(ServletContextEvent event)
    {
        // initialization code, if needed, goes here
    }

    @Override
    public void contextDestroyed(ServletContextEvent event)
    {
        LogManager.shutdown();
    }
}
```

Then you reference this listener in the applications `web.xml`:

```
<?xml version="1.0" encoding="UTF-8"?>
<web-app version="2.4" xmlns="http://java.sun.com/xml/ns/j2ee" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/web-app_2_4.xsd">

    <listener>
        <listener-class>com.kdgregory.log4j.aws.example.ExampleContextListener</listener-class>
    </listener>

    <!-- the rest of your web-app configuration -->

</web-app>
```

Be aware that you will still see the error even if you shut down Log4J using a context
listener. As long as you're using version 2.0.1 or later of the appender libraries this
is a non-issue: the background thread remains running for a short time after being shut
down to ensure that it sends any queued messages. If you use `jstack` to look at the
running threads, you will see the log-writer thread disappear after a few seconds.

> Note: version 2.0.0 and earlier had a bug in which the log-writer would wait for an
  excessively long time before shutting down. If using one of these versions in a
  web-application you should upgrade.


## Solution #2: Don't redeploy

Rather than repeatedly deploying to a long-running Tomcat server, restart Tomcat for each
deployment. While this may seem like snarky advice, it's arguably the "cloud native" solution,
one that treats your servers as [cattle, not pets](https://www.engineyard.com/blog/pets-vs-cattle).

If you're uing Amazon's [Elastic Beanstalk](https://docs.aws.amazon.com/elasticbeanstalk/latest/dg/Welcome.html),
or a containerized Tomcat deployment, then you're already using this model (and may be wondering why
this page exists). If not, I suggest looking into it; in my opinion it makes life easier in general.

That said, I recognize that it's not for everyone. In particular, if you have a large number of
micro-services, it can be far more efficient to run them on one (or a few) large servers rather
than many containers. And this solution also gets in the way when developing the application,
when you may be frequently redeploying (although in that case memory leaks aren't so important).


## Solution #3: move Log4J and the AWS appenders into Tomcat's `lib` directory

I don't particularly like this solution, and only consider it appropriate if you want to direct
the container logs to AWS along with application logs (and note that the Tomcat 8.5 documentation
no longer tells you how to use Log4J as Tomcat's internal logger). However, it _is_ a solution: if
you never need to restart the logging framework you won't have to worry about dangling threads.

If you do this, make sure that you copy _all_ of the necessary libraries from Maven Central. When
building an application you only need to reference `log4j1-aws-appenders` and it will bring in
`aws-shared` as a transitive dependency. When configuring the default Tomcat logger, you must
install both of these libraries, as well as the AWS libraries relevant to your destination(s).

