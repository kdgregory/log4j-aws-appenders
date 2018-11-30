# log4j-aws-appenders

Appenders for [Log4J 1.x](http://logging.apache.org/log4j/1.2/index.html) that write to
various AWS destinations:

* [CloudWatch Logs](docs/cloudwatch.md): basic centralized log management, providing keyword and time range search.
* [Kinesis Streams](docs/kinesis.md): the first step in a [logging pipeline](https://www.kdgregory.com/index.php?page=aws.loggingPipeline)
  that feeds [Elasticsearch](https://docs.aws.amazon.com/elasticsearch-service/latest/developerguide/what-is-amazon-elasticsearch-service.html)
  and other analytics destinations.
* [SNS](docs/sns.md): useful for real-time error notifications.

In addition to the appenders, this library provides:

* [JsonLayout](docs/jsonlayout.md), which lets you send data to an Elasticsearch/Kibana
  cluster without the need for parsing.
* [JMX integration](docs/jmx.md), which allows the appenders to report operational data.


## Usage

To use these appenders, include the appenders JAR in your project, along with Log4J and
the relevant AWS JAR(s) as described [below](#dependencies). The JARs are available from
from Maven Central; you can find the latest version
[here](https://search.maven.org/classic/#search%7Cga%7C1%7Cg%3A%22com.kdgregory.logging%22).

> **Note:** the Maven group and artifact IDs have changed between version 1.x and 2.x.
  The appender classnames, however, are unchanged; you need to update your POMs, but
  your config files are not affected.

Next, configure the desired appender in your Log4J properties. Each appender's documentation
describes the complete set of configuration properties and shows a typical configuration.

See the documentation for each appender to see how to configure that appender. You can
also look at the [example projects](examples).


### Versions

I follow the standard `MAJOR.MINOR.PATCH` versioning scheme:

* `MAJOR` is currently 2, representing the split from single-framework library to front-
   and back-end libraries supporting multiple frameworks.
* `MINOR` is incremented for each change that adds signficant functionality or changes the
  _behavior_ of existing functionality in non-backwards-compatible ways. The API _does not_
  break backwards compatibility for minor releases, so your configurations can remain the
  same.
* `PATCH` is incremented for reflect bugfixes or additional features that don't change the
  existing behavior (although they may add behavior).


### Dependencies

To avoid dependency hell, all dependencies are marked as "provided": you will need
to ensure that your project includes necessary dependencies for your destination(s):

* `log4j`
* `aws-java-sdk-logs` to use `CloudWatchAppender`
* `aws-java-sdk-kinesis` to use `KinesisAppender`
* `aws-java-sdk-sns` to use `SNSAppender`
* `aws-java-sdk-sts` to use the `aws:accountId` substitution variable.

The minimum supported dependency versions are:

* JDK: 1.7
  The build script generates 1.6-compatible classfiles, and the appender code does
  not rely on standard library classes/methods introduced after 1.6. However, it's
  become increasingly difficult to set up a JDK 1.6 test environment, so I use 1.7
  as a baseline. If you're still running 1.6 you should be able to use the library,
  but really, it's time to upgrade your JVM.
* Log4J: 1.2.16
  This is the first version that implements `LoggingEvent.getTimeStamp()`, which
  is needed to order messages when sending to AWS. It's been around since 2010,
  so if you haven't upgraded already you should.
* AWS SDK: 1.11.0
  The appenders will work with all releases in the 1.11.x sequence. If you're using
  a version that has client builders, they will be used to create service clients;
  if not, the default client constructors will be used. For more information, see the
  [FAQ](docs/faq.md#whats-with-client-builders-vs-contructors).

I have made an intentional effort to limit dependencies to the bare minimum. This
has in some cases meant that I write internal implementations for functions that
are found in common libraries.


## For more information

[Release History](CHANGES.md)

[Frequently Asked Questions](docs/faq.md)

[Design Documentation](docs/design.md)

[If you want to build it yourself](docs/build.md)
