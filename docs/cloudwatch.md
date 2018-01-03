# CloudWatch Logs

The CloudWatch implementation provides the following features:

* User-specified log-group and log-stream names
* Substitution variables to customize log-group and log-stream names
* Auto-rotation of log streams, based either on a time delay (explicity, hourly, or daily) or number of messages
* Configurable discard in case of network connectivity issues


## Configuration

Your Log4J configuration will look something like this:

```
log4j.rootLogger=DEBUG, cloudwatch

log4j.appender.cloudwatch=com.kdgregory.log4j.aws.CloudWatchAppender
log4j.appender.cloudwatch.logGroup={env:APP_NAME}
log4j.appender.cloudwatch.logStream={date}-{hostname}-{pid}
log4j.appender.cloudwatch.batchDelay=1000
log4j.appender.cloudwatch.rotationMode=daily

log4j.appender.cloudwatch.layout=org.apache.log4j.PatternLayout
log4j.appender.cloudwatch.layout.ConversionPattern=%d [%t] %-5p %c %x - %m%n
```

The appender provides the following properties (also described in the JavaDoc):

Name                | Description
--------------------|----------------------------------------------------------------
`logGroup`          | Name of the CloudWatch log group where messages are sent; may use substitutions. If this group doesn't exist it will be created. No default.
`logStream`         | Name of the CloudWatch log stream where messages are sent; may use substitutions. Defaults to `{startTimestamp}`.
`rotationMode`      | Controls whether auto-rotation is enabled. Values are `none`, `count`, `interval`, `hourly`, and `daily`; default is `none`. See below for more information.
`rotationInterval`  | Used only for `count` and `interval` rotation modes: for the former, the maximum number of messages, and for the latter, the number of milliseconds between automatic rotations.
`sequence`          | A value that is incremented each time the stream is rotated. Defaults to 0.
`batchDelay`        | The time, in milliseconds, that the writer will wait to accumulate messages for a batch. See [design doc](design.md#message-batches) for more information.
`discardThreshold`  | The threshold count for discarding messages; default is 10,000. See [design doc](design.md#message-discard) for more information.
`discardAction`     | Which messages will be discarded once the threshold is passed: `oldest` (the default), `newest`, or `none`.
`clientFactory`     | Specifies the fully-qualified name of a static method that will be used to create the AWS service client via reflection. See [design doc](design.md#service-client) for more information.

The `logGroup` and `logStream` properties may use [substitutions](substitutions.md).


## Permissions

To use this appender you will need to grant the following IAM permissions:

* `logs:CreateLogGroup`
* `logs:CreateLogStream`
* `logs:DescribeLogGroups`
* `logs:DescribeLogStreams`
* `logs:PutLogEvents`


## Logstream rotation

While CloudWatch allows you to select arbitrary date ranges when viewing log messages, it's often easier to drill down to
events if there's a separate log stream organized by time range. The `rotationMode` and `rotationInterval` parameters 
control how the appender switches to a new stream:

* `none`  
  Automatic log rotation is disabled, although you can explicitly call the appender's `rotate()` method.
* `count`  
  The log will be rotated after a specified number of messages have been written. This is intended primarily for testing,
  although there may be cases where you want to have relatively equally-sized chunks of log data (for example, if you were to
  export to S3 and analyze with Hadoop). If you use this mode, you should use a `timestamp` or `sequence` substitution in the
  log stream name.
* `interval`  
  The log is rotated after a specific interval, specified in milliseconds. This is probably not that useful, as you'll end
  up with arbitrary log intervals based on when the server was started. If you use this mode, you should use a `timestamp`
  substitution in the log stream name.
* `hourly`  
  The log is rotated at the top of each hour. It is possible that some log messages will be written to the next hour's log,
  due to the time delay between generating the message timestamp and testing for log rotation. If you use this mode, you
  should use the `hourlyTimestamp` substitution in your log stream name.
* `daily`  
  The log is rotated at midnight UTC. As with hourly rotation, it is possible that some log messages will be written to the
  next day's log. If you use this mode, you should use the `date` or `timestamp` substitutions in your log stream name.
