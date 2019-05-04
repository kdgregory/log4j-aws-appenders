# Logback Webapp Example

This directory contains an example web-application that can be deployed on Tomcat or another
J2EE app-server. It highlights several best practices, described [below](#features).


## Preparation

Start by editing `src/main/resources/logback.xml`, to enable your desired appenders. This
is a two-step process: first you have to uncomment the `<appender>` elements themselves,
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
another application server (the WAR name starts with `logback-aws-appenders-webapp`, and
ends with the current library version number).


## Features

### Use a Context Listener to initialize and shut down Logback

A [context listener](https://docs.oracle.com/javaee/6/api/javax/servlet/ServletContextListener.html)
provides a way for the application to set up and tear down its environment. The 
[example listener](src/main/java/com/kdgregory/logback/aws/example/ExampleContextListener.java)
does the following things:

* On initialization, look for an external Logback configuration file  

  While it's easy to include logging configuration in the JAR, as I do in this example,
  that is not necessarily a good idea because it means you have to rebuild and redeploy
  to change that configuration (for example, to enable debugging). A better approach is
  to store the logging configuration in an external file, which can be updated without
  a full redeploy.

  To support that use case, the example looks for a servlet initialization parameter
  named `logback.config.location`. If it finds that parameter, and the parameter's value
  is a file on the filesystem, the context listener will initialize Logback using that
  file. If the parameter isn't set, or doesn't point to a file, the application will
  fall back to using the compiled-in configuration.

* On termination, shut down Logback

  As descriped in more detail [here](../../docs/tomcat.md), you must explicitly shut
  down Logback when undeploying a web-application. If you don't then the writer thread
  will keep running, holding onto the old deployment, and Tomcat will eventually run
  out of memory.

  Logback provides `LogbackServletContextListener` to do this for you; all you have to
  do is reference it in your `web.xml`. The example subclasses this listener to provide
  the initialization code, but calls the superclass to shut down logging.


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


## Using JsonAccessLayout

Logback provides [the logback-access library](https://logback.qos.ch/access.html) to capture
access logs for Tomcat and Jetty. You can write these logs to AWS with the appenders, and use
[JsonAccessLayout](../../docs/jsonaccesslayout.md) to format the logs for use with Elasticsearch.

The difficult part of using these appenders with logback-access is getting all of the dependencies:
not only do you need to include the appenders library, you also need to include the AWS JARs and
all of their dependencies. However, the Maven dependency plugin lets you download and install
project dependencies to an arbitrary directory, and the example project gives you all the JARs
you need (including `logback-access`, which isn't required for the web-app itself).

The first step is to verify that there are no dependency conflicts (there aren't for appenders
release 2.1.0 and Tomcat 8.5.3, but you should check anyway):

* Run `mvn dependency:list` from within this project to get a listing of all dependencies. This
  produces output that look like `com.amazonaws:aws-java-sdk-core:jar:1.11.405:compile`; the
  JAR name is the second field.
* Run `ls -l $CATALINA_HOME/lib` to list the JARs already installed in your Tomcat distribution.

Assuming that there are no conflicts, run Maven again to copy the dependencies:

```
mvn dependency:copy-dependencies -DoutputDirectory=$CATALINA_HOME/lib
```

To configure access logging, refer to the documentation linked above.
