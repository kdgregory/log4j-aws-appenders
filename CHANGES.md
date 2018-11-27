# Change History

## 2.0.1 (TBD)

* Bugfix: was not limiting wait at shutdown, leaving writer thread dangling
  after app-server redeploy.
  ([#56](https://github.com/kdgregory/log4j-aws-appenders/issues/56))
* Eliminate use of ThreadLocal in JSON conversion.
  (also [#56](https://github.com/kdgregory/log4j-aws-appenders/issues/56))
* CloudWatchLogWriter silently retries on `InvalidSequenceTokenException`,
  which indicates a non-serious race condition between writers.
  ([#59](https://github.com/kdgregory/log4j-aws-appenders/issues/59))
* Add a web-app example, to demonstrate configuring and shutting down Log4J
  via `ContextListener` along with adding unique request IDs to the mapped
  diagnostic context.

## 2.0.0 (2018-10-29)

* Split the library into front-end and back-end components, in preparation for
  adding additional logging frameworks. Included general cleanup and refactoring.
* Changed the Maven group ID from `com.kdgregory.log4j` to `com.kdgregory.logging`.
* JMX integration now uses the "marker bean" name as a base name for statistics
  beans (was formerly tied to Log4J's naming convention).
  ([#53](https://github.com/kdgregory/log4j-aws-appenders/issues/53))

## 1.3.0 (2018-09-09)

* Added [JMX integration](docs/jmx.md): appenders/writers now report
  runtime statistics such as error messages and number of records sent.
  ([#21](https://github.com/kdgregory/log4j-aws-appenders/issues/21))
* Add `autoCreate` property to Kinesis and SNS appenders. Report an
  error if stream/topic doesn't exist and this property isn't set. If
  if is set, create the stream/topic.
  ([#37](https://github.com/kdgregory/log4j-aws-appenders/issues/37),
   [#45](https://github.com/kdgregory/log4j-aws-appenders/issues/45))
* `CloudWatchAppender` will now re-create logstream if it's deleted after
  appender initializes.
  ([#46](https://github.com/kdgregory/log4j-aws-appenders/issues/46))
* `JSONLayout` will optionally add newlines to the end of each record.
  This is useful when processing logs that don't go to ElasticSearch.
  ([#42](https://github.com/kdgregory/log4j-aws-appenders/issues/42))


## 1.2.2 (2018-05-16)

* Bugfix: writer thread was not a daemon, would keep application from shutting down
  unless explicitly terminated.
  ([#38](https://github.com/kdgregory/log4j-aws-appenders/issues/38))


## 1.2.1 (2018-01-06)

* Enable per-record random partition keys for `KinesisAppender`. This can make better
  use of a multi-shard stream for applications with high logging output.
  ([#24](https://github.com/kdgregory/log4j-aws-appenders/issues/24))
* Added the `clientFactory` property, which instructs appenders to call a static factory
  method to create their AWS service client.
  ([#28](https://github.com/kdgregory/log4j-aws-appenders/issues/28))
* Use reflection to create AWS service clients from default factory methods. This will
  be the default behavior for most SDK versions.
  ([#30](https://github.com/kdgregory/log4j-aws-appenders/issues/30))
* Allow explicit endpoint configuration. This is intended to support clients using older
  AWS SDKs that don't want to direct output to the `us-east-1` region.
  ([#30](https://github.com/kdgregory/log4j-aws-appenders/issues/30))
* When creating a service client via constructor, attempt to set the region from the
  `AWS_REGION` environment variable. This is an alternative to specifying `endpoint`
  in your logger config, but in my opinion isn't very useful because the list of
  available regions is dependent on your SDK version. For example, SDK 1.11.0 doesn't
  know about the `us-east-2` region.
  ([#30](https://github.com/kdgregory/log4j-aws-appenders/issues/30))


## 1.2.0 (2017-12-30)

* Added [SNS](docs/sns.md) as a destination.
* `CloudWatchAppender` and `KinesisAppender` now refuse to initialize if given an invalid
  stream/group name. In earlier revisions they would try to strip out invalid characters,
  but this meant that you could send messages to the wrong place. Better to fail fast.
* If unable to initialize, set internal queue to discard all messages. This avoids
  unnecessary memory consumption for messages that will never be sent.
* Allowed discard threshold and action to be reconfigured at runtime. This was added to
  support handling initialization errors, but may be useful for some applications.


## 1.1.3 (2017-12-12)

* Bugfix: `CloudWatchAppender` now handles multi-part results from `DescribeLogGroups`
  and `DescribeLogStreams`. This was unlikely to happen in the real world, but could
  affect sites with large numbers of similarly-named groups or streams.
* Bugfix: `KinesisAppender` is now more resilient to rate limiting during initialization.
  This was primarily an issue with the integration tests, which create and delete streams
  at a high rate. In normal operations it could happen if a large number of applications
  started logging at the same time (such as a fleet of instances starting).
* `AbstractLogWriter` now tracks whether an error happened during initialization. This is
  currently used for testing, but will be exposed via JMX at some future point.


## 1.1.2 (2017-10-16)

* Added [JsonLayout](docs/jsonlayout.md).
* Added an example application that sends output to all appenders.


## 1.1.1 (2017-10-10)

* Added configurable message discard, to avoid out-of-memory errors when
  connectivity to AWS is spotty.
  ([#15](https://github.com/kdgregory/log4j-aws-appenders/issues/15))


## 1.1.0 (2017-09-29)

* Added [Kinesis](docs/kinesis.md) as a destination.
* Improved implementation of `CloudWatchAppender`, including integration tests.


## 1.0.1 (2017-09-02)

* Bugfix: CloudWatch SDK dependency not marked as provided; might cause conflict
  with a project's own dependency versions.
* Bugfix: Default configuration was incorrect: log stream name was "{startTimestamp}",
  should be "{startupTimestamp}"
* Bugix: more resilient to rate limiting of logstream/loggroup creation.


## 1.0.0 (2017-08-12)

* Initial release: [CloudWatch Logs](docs/cloudwatch.md) as a destination.
