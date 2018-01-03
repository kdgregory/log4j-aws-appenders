# Change History


## 1.2.1 (TBD)

* Added the `clientFactory` property, which instructs appenders to call a static factory
  method to create their AWS service client.
  ([#28](https://github.com/kdgregory/log4j-aws-appenders/issues/28)


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
