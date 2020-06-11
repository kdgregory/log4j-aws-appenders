These tests run multiple scenarios against actual AWS resources.

There are two groups of tests:

* Logging framework tests exercise all appenders in a single framework-specific module.
* Writer tests exercise the core functionality, outside of a logging framework. Each
  destination has its own test module, to ensure that there are no cross-destination
  dependencies (#71). These tests also verify that we can run using the base SDK
  version (1.11.0).

To run the full suite of tests, first run `mvn clean install` from the project root,
then run `mvn clean test` from this directory. To run the tests for an individual
framework, run the same command from within the framework directory.

You must have valid AWS credentials, with permissions shown [here](../docs/build.md#aws-permissions-needed-for-integration-tests).

**BEWARE:** these tests create resources and do not delete them after running (intentionally,
for debugging).  If you choose to run the tests, use the AWS Console or command line to delete
all created resources. There will also be a negligible charge to interact with the resources.

*I am not responsible for your AWS bill.*
