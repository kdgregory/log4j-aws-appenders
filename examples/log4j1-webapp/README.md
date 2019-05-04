# Log4J Webapp Example

This directory contains an example web-application that can be deployed on Tomcat or another
J2EE app-server. It highlights several best practices, described [below](#features).


## Preparation

Start by editing `src/main/resources/log4j.properties`, adding your desired appenders to the
"com.kdgregory" logger configuration. For example, to enable CloudWatch Logs output:

```
log4j.logger.com.kdgregory=DEBUG, cloudwatch
```

If you enable Kinesis or SNS, you will also need to create the destinations; for CloudWatch
Logs the destination is auto-created. You can create them using the CloudFormation templates
[here](../cloudformation), manually, or by enabling the auto-create feature on the appenders.

> *BEWARE!* You will be charged for all AWS services used by this example. I have tried to keep
  the costs minimal, and some services (such as CloudWatch) may be covered under a free tier.
  However, *you are responsible for all charges*.


## Building and running the example

To build the example program, use Maven:

    mvn clean package

This will produce a WAR in the `target` directory, which you can then deploy to Tomcat or
another application server (the WAR name starts with `log4j1-aws-appenders-webapp`, and
ends with the current library version number).


## Features

### Use a Context Listener to initialize and shut down Log4J

A [context listener](https://docs.oracle.com/javaee/6/api/javax/servlet/ServletContextListener.html)
provides a way for the application to set up and tear down its environment. The 
[example listener](src/main/java/com/kdgregory/log4j/aws/example/ExampleContextListener.java)
does the following things:

* On initialization, look for an external Log4J configuration file  

  While it's easy to include logging configuration in the JAR, as I do in this example,
  that is not necessarily a good idea because it means you have to rebuild and redeploy
  to change that configuration (for example, to enable debugging). A better approach is
  to store the logging configuration in an external file, which can be updated without
  a full redeploy.

  To support that use case, the example looks for a servlet initialization parameter
  named `log4j.config.location`. If it finds that parameter, and the parameter's value
  is a file on the filesystem, the context listener will initialize Log4J using that
  file. If the parameter isn't set, or doesn't point to a file, the application will
  fall back to using the compiled-in configuration.

* On termination, shut down Log4J

  As descriped in more detail [here](../../docs/tomcat.md), you must explicitly shut
  down Log4J when undeploying a web-application. If you don't then the writer thread
  will keep running, holding onto the old deployment, and Tomcat will eventually run
  out of memory.


### Track requests via the Mapped Diagnostic Context

When looking at a web-app's logs, it's extremely useful to isolate all messages that belong to
a single request. If you add a unique identifier to Log4J's mapped diagnostic context, this
will happen automatically.

An easy way to do this is with a [servlet filter](src/main/java/com/kdgregory/log4j/aws/example/RequestIdFilter.java):
when a request arrives the filter generates a UUID and stores it in the MDC; when the request
is done the filter removes the UUID from the MDC. Note that the filter checks the request's
dispatcher type: we want to generate an ID for initial requests, but reuse an existing ID
for forwards and includes that are triggered by the original request.

The filter also adds the UUID as a request attribute. In a micro-service architecture, you could
then add the attribute as a header for any subsidiary requests, and track its progress through
the various services that it calls. To do that, the filter would first look for the header, and
use its value rather than generate a UUID. And if you use an Application Load Balancer, you can
[configure it](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-request-tracing.html)
to generate a unique ID for you when the request is first handled.
