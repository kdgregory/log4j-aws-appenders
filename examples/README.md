# Examples

This directory contains several examples that show how the appenders are configured.
Each has its own README file that goes into further detail on its operation.

* [log4j1-example](log4j1-example): writes logging messages to each of the destinations
  using Log4J 1.x.
* [log4j1-webapp](log4j1-webapp): a web-app that demonstrates how to initialize and shut
  down Log4J 1.x with a `ContextListener`, along with adding a unique request token to
  the mapped diagnostic context to track each request's progress.
* [logback-example](logback-example): writes logging messages to each of the destinations
  using Logback.
* [logback-webapp](logback-webapp): a web-app that demonstrates how to initialize and shut
  down Logback with a `ContextListener`, along with adding a unique request token to the
  mapped diagnostic context to track each request's progress.

In addition, there are [CloudFormation templates](cloudformation) to create the destinations
used by these examples.

To build the examples, run `mvn clean package` from either this directory (to build all of
them) or the individual project directories.
