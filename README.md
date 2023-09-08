# log4j-aws-appenders

Appenders for [Log4J 1.x](http://logging.apache.org/log4j/1.2/index.html),
[Log4J 2.x](https://logging.apache.org/log4j/2.x/)
and [Logback](https://logback.qos.ch/)
that write to various AWS destinations:

* [CloudWatch Logs](docs/cloudwatch.md): AWS-native centralized log management, providing keyword and time range search.
* [Kinesis Streams](docs/kinesis.md): the first step in a [logging pipeline](https://www.kdgregory.com/index.php?page=aws.loggingPipeline)
  that feeds [Elasticsearch](https://docs.aws.amazon.com/elasticsearch-service/latest/developerguide/what-is-amazon-elasticsearch-service.html)
  and other analytics destinations.
* [SNS](docs/sns.md): useful for real-time error notifications.

In addition to basic log output, this library also provides:

* [JsonLayout](docs/jsonlayout.md), which lets you send data to an Elasticsearch/Kibana
  cluster without the need for parsing.
* [JMX integration](docs/jmx.md), which allows the appenders to report operational data.
* [Substitions](docs/substitutions.md), which allow you to configure the appenders with
  information from the runtime environment, such as EC2 instance ID.


## Usage

To use these appenders, you must add the following libraries to your build:

* The AWS SDK libraries for whatever destinations and supporting code you need:

  * CloudWatch Logs
  * Kinesis
  * SNS
  * IAM (in order to use assumed roles)
  * STS (in order to use assumed roles or retrieve current account information)
  * EC2 (in order to retrieve instance tags)
  * Systems Manager (in order to retrieve values from Parameter Store)

* The facade library for whatever version of the AWs SDK you're using.

  * [1.x](https://central.sonatype.com/artifact/com.kdgregory.logging/aws-facade-v1)
  * [2.x](https://central.sonatype.com/artifact/com.kdgregory.logging/aws-facade-v2)

* The appenders library for your logging framework

  * [Log4J 1.x](https://central.sonatype.com/artifact/com.kdgregory.logging/log4j1-aws-appenders)
  * [Log4J 2.x](https://central.sonatype.com/artifact/com.kdgregory.logging/log4j2-aws-appenders)
  * [Logback](https://central.sonatype.com/artifact/com.kdgregory.logging/logback-aws-appenders)

Then grant your program the [IAM permissions](docs/permissions.md) required by your
chosen destination(s) and features.

Lastly, configure your logging framework using its configuration mechanism. See the
documentation for each appender for the configuration parameters that it uses.

There are [example projects](examples) that provide typical configurations and Maven POMs.


## Versions

I follow the standard `MAJOR.MINOR.PATCH` versioning scheme:

* `MAJOR` is currently 3, representing support for multiple AWS SDKs.
* `MINOR` is incremented for each change that adds signficant functionality or changes the
  _behavior_ of existing functionality in non-backwards-compatible ways. The API _does not_
  break backwards compatibility for minor releases, so your configurations can remain the
  same.
* `PATCH` is incremented for bugfixes or minor additions to existing features.


## Dependencies

To avoid dependency hell, **this library does not specify any transitive dependencies**.
You must explicitly add all required dependencies into your build. I have made an
intentional effort to limit dependencies to the bare minimum.

The minimum supported dependency versions are:

* **JDK**: 1.8

* **Log4J 1.x**: 1.2.16  

  This is the first version that implements `LoggingEvent.getTimeStamp()`, which
  is needed to order messages when sending to AWS. It's been around since 2010,
  so if you haven't upgraded already you should.

* **Log4J 2.x**: 2.10.0

  This is the first version that supports custom key/value pairs for `JsonLayout`.
  If that's not important to you, the library will work with version 2.8 (which
  introduced a breaking change in backwards compatibility).

  *Note*: due to CVE-2021-44228, the recommended minimum version is 2.15.0.

* **Logback**: 1.2.0  

  This version is required to support `JsonAccessLayout`. If you don't use that,
  version 1.0.0 will work.

* **AWS v1 SDK**: 1.11.716

  This is the version that I have tested with. You can use an earlier version,
  but not all features may be available.

* **AWS v2 SDK**: 2.10.43

  This is the version that I have tested with. You can use an earlier version,
  but not all features may be available.


## Contributions

At this time I am not accepting contributions. If you find a bug in the code, please
submit an issue that explains the problem and provides steps to replicate. Or better,
the file and line number where the error exists.

> Please note: not being able to specify AWS credentials in your configuration file
  is _not_ a bug, nor is it an enhancement that I am willing to consider.


## For more information

[Release History](CHANGES.md)

[Frequently Asked Questions](FAQ.md)

[Design](docs/design.md) and [Implementation](docs/implementation.md) docs

[How to build](docs/build.md)
