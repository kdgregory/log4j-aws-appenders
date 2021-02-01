# Examples

This directory contains several examples that show how the appenders are configured.
Each has its own README file that goes into further detail on its operation.

* [log4j1-example](log4j1-example): writes logging messages to each of the destinations
  using Log4J 1.x and the AWS version 1 SDK.
* [log4j1-webapp](log4j1-webapp): a web-app that demonstrates how to initialize and shut
  down Log4J 1.x with a `ContextListener`, along with adding a unique request token to
  the mapped diagnostic context to track each request's progress. This example also uses
  the version 1 AWS SDK.
* [log4j2-example](log4j2-example): writes logging messages to each of the destinations
  using Log4J 2.x and the AWS version 2 SDK.
* [log4j2-webapp](log4j2-webapp): a web-app that uses Log4J 2.x. This example relies on
  Log4J's integration with Servlet 3.0 for configuration and shutdown, rather than using
  an explicit `ContextListener`. Like the other webapp examples, it includes a servlet
  filter that adds a unique request token to the mapped diagnostic context to track each
  request's progress. This example also uses the version 2 AWS SDK.
* [logback-example](logback-example): writes logging messages to each of the destinations
  using Logback and the AWS version 1 SDK.
* [logback-webapp](logback-webapp): a web-app that demonstrates how to initialize and shut
  down Logback with a `ContextListener`, along with adding a unique request token to the
  mapped diagnostic context to track each request's progress. This example also uses the
  version 1 AWS SDK.

In addition, there are [CloudFormation templates](cloudformation) to create the destinations used
by these examples, along with templates for an Elasticsearch cluster and Kinesis Firehose to create
a complete [logging pipeline](https://www.kdgregory.com/index.php?page=aws.loggingPipeline).
