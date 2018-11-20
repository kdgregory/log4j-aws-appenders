# Webapp Example

This directory contains an example web-application that is intended to be deployed on Tomcat
or another J2EE app-server. It is highlights the following best practices:

* Using a `ContextListener` to shut down Log4J when the application is undeployed. This is a
  critical part of avoiding out-of-memory errors when repeatedly redeploying an application.
* Using the same `ContextListener` to initialize Log4J when the application is deployed. This
  allows use of an external file, which may be shared across applications.
* Using the mapped diagnostic context to track a request through the processing stack.

*BEWARE!* You will be charged for all AWS services used by this example. I have tried to keep
the costs minimal, and some services (such as CloudWatch) may be covered under a free tier.
However, *you are responsible for all charges*.


## Building and running the example

To build the example program, use Maven:

    mvn clean package

This will produce a WAR in the `target` directory, which you can then deploy to Tomcat or
another application server (the WAR name will start with `log4j1-appenders-example`, with
the current library version number).


## Features

### Shutting down Log4J when the application is undeployed

As descriped in more detail [here](../../docs/tomcat.md), you must explicitly shut down
Log4J when undeploying the web-application. The example `ContextListener` does this in
its [contextDestroyed()](src/main/java/com/kdgregory/log4j/aws/example/ExampleContextListener.java#L57)
method.


### Initializing Log4J with an external configuration file

You can simplify deployment by managing logging configuration outside of the application: the
deployed application would include a basic logging configuration for fallback, but the actual
configuration exists on the filesystem. This is also the responsibility of a `ContextListener`,
and the example's [contextInitialized()](src/main/java/com/kdgregory/log4j/aws/example/ExampleContextListener.java#L40)
method looks for an initialization parameter that contains the configuration filename. If it
finds the parameter, and that parameter represents an actual file, then the listener uses
that file to configure Log4J.

Initialization parameters are specified in [web.xml](src/main/webapp/WEB-INF/web.xml#L5). In
the case of the example, it looks for the file `/tmp/log4j.properties` (production deployments
would use a location like `/etc/log4j.properties`).


### Tracking requests with the Mapped Diagnostic Context

When looking at a web-app's logs, it's extremely useful to isolate all messages that belong to
a single request. If you add a unique identifier to Log4J's mapped diagnostic context, this
will happen automatically. The easiest way to do this is in a request filter, and the example
[includes such a filter](src/main/java/com/kdgregory/log4j/aws/example/RequestIdFilter.java)
that creates a UUID for each request.

Similarly, if you use sessions it can be very useful to add the session ID into the MDC, to
track repeated requests for the same user. And if you use micro-services, where one server
may call another, you can store the identifier in a request header and pass it between
services (Amazon's [Application Load Balancer](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-request-tracing.html)
will generate this header for you).
