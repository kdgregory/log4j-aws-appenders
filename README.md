# log4j-aws-appenders

Appenders for [Log4J 1.x](http://logging.apache.org/log4j/1.2/index.html),
[Logback](https://logback.qos.ch/), and [Log4J 2.x](https://logging.apache.org/log4j/2.x/)
that write to various AWS destinations:

* [CloudWatch Logs](docs/cloudwatch.md): basic centralized log management, providing keyword and time range search.
* [Kinesis Streams](docs/kinesis.md): the first step in a [logging pipeline](https://www.kdgregory.com/index.php?page=aws.loggingPipeline)
  that feeds [Elasticsearch](https://docs.aws.amazon.com/elasticsearch-service/latest/developerguide/what-is-amazon-elasticsearch-service.html)
  and other analytics destinations.
* [SNS](docs/sns.md): useful for real-time error notifications.

In addition, this library provides the following features:

* [JsonLayout](docs/jsonlayout.md), which lets you send data to an Elasticsearch/Kibana
  cluster without the need for parsing.
* [JMX integration](docs/jmx.md), which allows the appenders to report operational data.
* [Substitions](docs/substitutions.md), which allow you to configure the appenders with
  information from the runtime environment, such as EC2 instance ID.


## Usage

To use these appenders, include the framework-specific appenders JAR in your project, along
with the JAR(s) specific to your logging framework and the relevant AWS JAR(s) as described
[below](#dependencies).

The appender JARs are published on Maven Central. You can find the latest version from the
following links:

* [Log4J 1.x](https://search.maven.org/classic/#search%7Cga%7C1%7Cg%3A%22com.kdgregory.logging%22%20AND%20a%3A%22log4j1-aws-appenders%22)
* [Log4J 2.x](https://search.maven.org/classic/#search%7Cga%7C1%7Cg%3A%22com.kdgregory.logging%22%20AND%20a%3A%22log4j2-aws-appenders%22)
* [Logback](https://search.maven.org/classic/#search%7Cga%7C1%7Cg%3A%22com.kdgregory.logging%22%20AND%20a%3A%22logback-aws-appenders%22)

See the documentation for each appender for configuration. You can also look at the
[example projects](examples).


### Versions

I follow the standard `MAJOR.MINOR.PATCH` versioning scheme:

* `MAJOR` is currently 2, representing the split from single-framework library to front-
   and back-end libraries supporting multiple frameworks.
* `MINOR` is incremented for each change that adds signficant functionality or changes the
  _behavior_ of existing functionality in non-backwards-compatible ways. The API _does not_
  break backwards compatibility for minor releases, so your configurations can remain the
  same.
* `PATCH` is incremented for bugfixes or minor additions to existing features.


### Dependencies

To avoid dependency hell, all dependencies are marked as "provided": you will need
to ensure that your project includes necessary dependencies for your destination(s).

* Your logging framework of choice
* [`aws-java-sdk-logs`](https://search.maven.org/classic/#search%7Cga%7C1%7Cg%3A%22com.amazonaws%22%20AND%20a%3A%22aws-java-sdk-logs%22) to use `CloudWatchAppender`
* [`aws-java-sdk-kinesis`](https://search.maven.org/classic/#search%7Cga%7C1%7Cg%3A%22com.amazonaws%22%20AND%20a%3A%22aws-java-sdk-kinesis%22) to use `KinesisAppender`
* [`aws-java-sdk-sns`](https://search.maven.org/classic/#search%7Cga%7C1%7Cg%3A%22com.amazonaws%22%20AND%20a%3A%22aws-java-sdk-sns%22) to use `SNSAppender`
* [`aws-java-sdk-iam`](https://search.maven.org/classic/#search%7Cga%7C1%7Cg%3A%22com.amazonaws%22%20AND%20a%3A%22aws-java-sdk-iam%22) to use assumed roles.
* [`aws-java-sdk-ssm`](https://search.maven.org/classic/#search%7Cga%7C1%7Cg%3A%22com.amazonaws%22%20AND%20a%3A%22aws-java-sdk-ssm%22) to use the `ssm` substitution.
* [`aws-java-sdk-sts`](https://search.maven.org/classic/#search%7Cga%7C1%7Cg%3A%22com.amazonaws%22%20AND%20a%3A%22aws-java-sdk-sts%22) to use assumed roles or the `aws:accountId` substitution.

The minimum supported dependency versions are:

* **JDK**: 1.7

* **Log4J 1.x**: 1.2.16  
  This is the first version that implements `LoggingEvent.getTimeStamp()`, which
  is needed to order messages when sending to AWS. It's been around since 2010,
  so if you haven't upgraded already you should.

* **Log4J 2.x**: 2.10.0   
  This is the first version that supports custom key/value pairs for `JsonLayout`.
  If that's not important to you, the library will work with version 2.8 (which
  introduced a breaking change in backwards compatibility).

* **Logback**: 1.2.0  
  This version is required to support `JsonAccessLayout`. If you don't use that,
  version 1.0.0 will work.

* **AWS SDK**: 1.11.0  
  The appenders will work with all releases in the 1.11.x sequence, but some
  features require later versions (eg, to access values from Parameter Store,
  you need 1.11.63). If you're using a version that has client builders, they
  will be used to create service clients; if not, the default client constructors
  are used. For more information, see the [FAQ](FAQ.md#whats-with-client-builders-vs-contructors).

I have made an intentional effort to limit dependencies to the bare minimum. This
has in some cases meant that I write internal implementations for functions that
are found in common libraries.


## Contributions

At this time I am not accepting contributions. If you find a bug in the code, please
submit an issue that explains the problem and includes the filename and line number
(or better, a link to the source) where the error exists.


## For more information

[Release History](CHANGES.md)

[Frequently Asked Questions](FAQ.md)

[Design Documentation](docs/design.md) and [Implementation Notes](docs/implementation.md)

If you want to build it yourself, [read this](docs/build.md)
