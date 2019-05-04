# CloudWatch

The CloudWatch implementation provides the following features:

* User-specified log-group and log-stream names.
* Substitution variables to customize log-group and log-stream names.
* Auto-rotation of log streams, based either on a time delay (specified interval, hourly, daily) or number of messages.
* Configurable discard in case of network connectivity issues.


## Configuration

The appender provides the following properties (also described in the JavaDoc):

Name                | Description
--------------------|----------------------------------------------------------------
`logGroup`          | Name of the CloudWatch log group where messages are sent; may use [substitutions](substitutions.md). If this group doesn't exist it will be created. No default.
`logStream`         | Name of the CloudWatch log stream where messages are sent; may use [substitutions](substitutions.md). If this stream doesn't exist it will be created. Defaults to `{startupTimestamp}`.
`retentionPeriod`   | (optional) Specifies a non-default retention period for created CloudWatch log groups.
`rotationMode`      | Controls whether auto-rotation is enabled. Values are `none`, `count`, `interval`, `hourly`, and `daily`; default is `none`. See below for more information.
`rotationInterval`  | Used only for `count` and `interval` rotation modes: for the former, the number of messages, and for the latter, the number of milliseconds between rotations.
`sequence`          | A value that is incremented each time the stream is rotated. Defaults to 0.
`synchonous`        | If `true`, the appender will operate in [synchronous mode](design.md#synchronous-mode), sending messages from the invoking thread on every call to `append()`.
`batchDelay`        | The time, in milliseconds, that the writer will wait to accumulate messages for a batch. See the [design doc](design.md#message-batches) for more information.
`discardThreshold`  | The threshold count for discarding messages; default is 10,000. See the [design doc](design.md#message-discard) for more information.
`discardAction`     | Which messages will be discarded once the threshold is passed: `oldest` (the default), `newest`, or `none`.
`clientFactory`     | Specifies the fully-qualified name of a static method that will be invoked to create the AWS service client. See the [service client doc](service-client.md#client-creation) for more information.
`clientRegion`      | Specifies a non-default region for the client. See the [service client doc](service-client.md#endpoint-configuration) for more information.
`clientEndpoint`    | Specifies a non-default endpoint; only supported for clients created via constructor. See the [service client doc](service-client.md#endpoint-configuration) for more information.
`useShutdownHook`   | Controls whether the appender uses a shutdown hook to attempt to process outstanding messages when the JVM exits. This is `true` by default; set to `false` to disable. See [docs](design.md#shutdown-hooks) for more information.


### Example: Log4J 1.x

```
log4j.appender.cloudwatch=com.kdgregory.log4j.aws.CloudWatchAppender
log4j.appender.cloudwatch.logGroup={env:APP_NAME}-{sysprop:deployment}
log4j.appender.cloudwatch.logStream={hostname}-{startupTimestamp}
log4j.appender.cloudwatch.rotationMode=daily

log4j.appender.cloudwatch.layout=org.apache.log4j.PatternLayout
log4j.appender.cloudwatch.layout.ConversionPattern=%d [%t] %-5p %c %x - %m%n
```


### Example: Logback

```
<appender name="CLOUDWATCH" class="com.kdgregory.logback.aws.CloudWatchAppender">
    <logGroup>{env:APP_NAME}-{sysprop:deployment}</logGroup>
    <logStream>{hostname}-{startupTimestamp}</logStream>
    <rotationMode>daily</rotationMode>
    <layout class="ch.qos.logback.classic.PatternLayout">
        <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </layout>
</appender>
```


## Permissions

To use this appender you will need to grant the following IAM permissions:

* `logs:CreateLogGroup`
* `logs:CreateLogStream`
* `logs:DescribeLogGroups`
* `logs:DescribeLogStreams`
* `logs:PutLogEvents`
* `logs:PutRetentionPolicy`


## LogGroup and LogStream management

In a large deployment, it would be onerous to manually create all the log groups and streams
that you will need. Instead, this appender will automatically create groups and streams (and,
unlike the Kinesis and SNS appenders, there is no way to disable this behavior).

As a best practice, log group names should usually identify the application, with log stream
names that identify the instance of the application (typically some combination of hostname,
process ID, and timestamp). However, if you're writing JSON output and using CloudWatch Logs
Insights to analyze the data, you may find it more convenient to write all log streams under
a single log group.

You can specify an optional retention period for a newly-created log group. CloudWatch will
automatically delete any older messages. Beware, however, that CloudWatch does _not_ delete
the log streams that held those messages; you may end up with a lot of empty streams.

Also be aware that you can't pick any arbitrary number of days for this parameter: the
[CloudWatch API](: see https://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_PutRetentionPolicy.html)
lists allowable values, and the appender will check your configured value against the
list.


## LogStream rotation

While CloudWatch allows you to select arbitrary date ranges when viewing log messages, it's often easier
to drill down to events if there's a separate log stream organized by time range. The `rotationMode`
parameter, in concert with `rotationInterval`, controls how the appender switches to a new stream:

* `none`

  Automatic log rotation is disabled, although you can explicitly call the appender's `rotate()` method.

* `count`

  The log will be rotated after a specified number of messages have been written. This is intended
  primarily for testing, although there may be cases where you want to have relatively equally-sized
  chunks of log data (for example, if you were to export to S3 and analyze with Hadoop). If you use
  this mode, you should use a `timestamp` or `sequence` substitution in the log stream name.

* `interval`

  The log is rotated after a specific interval, specified in milliseconds. This is probably not that
  useful, as you'll end up with arbitrary log intervals based on when the server was started. If you
  use this mode, you should use a `timestamp` substitution in the log stream name.

* `hourly`

  The log is rotated at the top of each hour. It is possible that some log messages will be written to
  the next hour's log, due message batching. If you use this mode, you should use the `hourlyTimestamp`
  substitution in your stream name.

* `daily`

  The log is rotated at midnight UTC. As with hourly rotation, it is possible that some log messages
  will be written to the next day's log. If you use this mode, you should use the `date` or `timestamp`
  substitutions in your log stream name.


## InvalidSequenceTokenException and Logstream Throttling

Writing to CloudWatch Logs is a two step process: first you retrieve a sequence token, which identifies
the spot to insert new entries, and then you call `PutLogEvents` with this token. These are separate
API calls, which means that there's a race condition: two processes can get the same sequence token and
attempt to write. If this happens, only one succeeds; the other receives `InvalidSequenceTokenException`.

This can happen in any deployment, but is more likely if large numbers of applications are writing to
the same logstream. In the case of the appenders library, it is extremely likely if you start multiple
writers at the same time, with the same batch delay (ie, with the same logging config). Which happens
in Hadoop or Spark clusters.

As with any batch-level error, the appender will try again. There will be no message loss unless the
error happens so frequently that the appender consumes its entire message queue (which is extremely
unlikely, as any given process is likely to win the race). However, prior to release 2.0.1, the
appender would report this exception as an error, which was distracting.

Since the 2.0.1 release, this exception is handled by retrying the batch after a short delay, without
requeueing the messages. It does not report an error, although it does track the number of retries
and reports that statistic via [JMX](jmx.md). This retry process also reduces the likelihood of the
exception, as the retry delays will change the times that the appenders attempt to write to
CloudWatch (ie, instead of all appenders writing at start + 2000 milliseconds, one will write at
start + 2000, another will write at start + 2100, and so on).

That said, having a large number of writers feeding data to the same logstream is not a good idea,
because each stream is limited to 5 writes per second. Again, the appenders will retry, so you
won't lose log messages, but it would be better to avoid the collisions entirely.

One approach is to use separate logstream names. The CloudWatch Logs console allows you to search
across all streams within a log group, so there is little benefit to combining logs from multiple
processes. To make this happen, use a [substitution](substitutions.md) in the stream name. For
example, `mystream-{pid}` will include the process ID. If you have multiple independent runs of
your application, `mystream-{startupTimestamp}-{pid}` will order them in the console list.

Another alternative, and one that I think is better (albeit more expensive), is to use the Kinesis
appender, which feeds a [logging pipeline](https://www.kdgregory.com/index.php?page=aws.loggingPipeline)
that ends up in Elasticsearch. You can scale each part of that pipeline to match your needs, and
attach arbitrary tags to the log entries using [JsonLayout](jsonlayout.md).
