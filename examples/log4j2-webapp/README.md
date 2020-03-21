# Log4J 2.x Webapp Example

This directory contains an example web-application that can be deployed on Tomcat or another
J2EE app-server. It highlights several best practices, described [below](#features).


## Preparation

Start by editing `src/main/resources/log4j2.xml`, to enable your desired appenders. This
is a two-step process: first you have to uncomment the appender elements themselves,
then you have to uncomment the references to those appenders in the "com.kdgregory"
`<logger>` element. 

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
another application server (the WAR name starts with `log4j2-aws-appenders-webapp`, and
ends with the current library version number).


## Features

### Rely on Log4J to properly start and stop the logging framework

Unlike Log4J 1.x and Logback, Log4J 2.x [provides out of the box support for web-apps that
follow the 3.0 servlet spec or above](https://logging.apache.org/log4j/2.x/manual/webapp.html):
you don't need to provide an explicit `ContextListener` to initialize and shut down the
framework.

However, the Log4J1 and Logback examples also use the context listener to specify an out-of-WAR
configuration file.

*** TODO ***



### Track requests via the Mapped Diagnostic Context

When looking at a web-app's logs, it's extremely useful to isolate all messages that belong to
a single request. If you add a unique identifier to Logback's mapped diagnostic context, this
will happen automatically.

An easy way to do this is with a [servlet filter](src/main/java/com/kdgregory/logback/aws/example/RequestIdFilter.java):
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


