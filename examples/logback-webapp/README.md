# Logging Webapp Example

This directory contains an example web-application that is intended to be deployed on Tomcat
or another J2EE app-server. It is intended to highlight the following best practices:

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

Or, you can do it in one step with the Maven Tomcat plugin:


### Configuring an external logfile


## Tracking requests with the Mapped Diagnostic Context
