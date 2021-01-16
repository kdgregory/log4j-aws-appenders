# CloudWatchAppender

The CloudWatch appender provides the following features:

* User-specified log-group and log-stream names.
* Substitution variables to customize log-group and log-stream names.
* Configurable discard in case of network connectivity issues.


## Configuration

This appender provides the following configuration properties, along with the common [connection properties](client.md#configuration-properties).

Name                        | Description
----------------------------|----------------------------------------------------------------
`logGroup`                  | Name of the CloudWatch log group where messages are sent; may use [substitutions](substitutions.md). If this group doesn't exist it will be created. No default.
`logStream`                 | Name of the CloudWatch log stream where messages are sent; may use [substitutions](substitutions.md). If this stream doesn't exist it will be created. Defaults to `{startupTimestamp}`.
`retentionPeriod`           | (optional) Specifies a non-default retention period for created CloudWatch log groups.
`dedicatedWriter`           | If `true` (the default), the appender assumes that it will be the only writer to the log stream, and will not retrieve a sequence token before each write. Defaults to `false` for legacy behavior. See [below](#invalidsequencetokenexception-and-logstream-throttling) for more information.
`synchronous`               | If `true`, the appender will operate in [synchronous mode](design.md#synchronous-mode), sending messages from the invoking thread on every call to `append()`.
`batchDelay`                | The time, in milliseconds, that the writer will wait to accumulate messages for a batch. See the [design doc](design.md#message-batches) for more information.
`truncateOversizeMessages`  | If `true` (the default), truncate any messages that are too large for CloudWatch; if `false`, discard them. See [below](#oversize-messages) for more information.
`discardThreshold`          | The threshold count for discarding messages; default is 10,000. See the [design doc](design.md#message-discard) for more information.
`discardAction`             | Which messages will be discarded once the threshold is passed: `oldest` (the default), `newest`, or `none`.
`useShutdownHook`           | Controls whether the appender uses a shutdown hook to attempt to process outstanding messages when the JVM exits. This is `true` by default, set to `false` to disable. Ignored for Log4J2. See [docs](design.md#shutdown) for more information.


### Example: Log4J 1.x

```
log4j.appender.cloudwatch=com.kdgregory.log4j.aws.CloudWatchAppender
log4j.appender.cloudwatch.logGroup={env:APP_NAME}-{sysprop:deployment:dev}
log4j.appender.cloudwatch.logStream={hostname}-{startupTimestamp}
log4j.appender.cloudwatch.dedicatedWriter=true

log4j.appender.cloudwatch.layout=org.apache.log4j.PatternLayout
log4j.appender.cloudwatch.layout.ConversionPattern=%d [%t] %-5p - %c - %m%n
```


### Example: Log4J2

Note that this example uses Log4J [lookups](https://logging.apache.org/log4j/2.x/manual/lookups.html#EnvironmentLookup)
in addition to library-provided substitutions.

```
<CloudWatchAppender name="CLOUDWATCH">
    <logGroup>${env:APP_NAME}-${sys:deployment:-dev}</logGroup>
    <logStream>{hostname}-{startupTimestamp}</logStream>
    <dedicatedWriter>true</dedicatedWriter>
    <PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %p - %c - %m" />
</CloudWatchAppender>
```


### Example: Logback

```
<appender name="CLOUDWATCH" class="com.kdgregory.logback.aws.CloudWatchAppender">
    <logGroup>{env:APP_NAME}-{sysprop:deployment:dev}</logGroup>
    <logStream>{hostname}-{startupTimestamp}</logStream>
    <dedicatedWriter>true</dedicatedWriter>
    <layout class="ch.qos.logback.classic.PatternLayout">
        <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level - %logger{36} - %msg%n</pattern>
    </layout>
</appender>
```


## Permissions

To use this appender you need the following IAM permissions:

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
the log streams that held those messages; you may end up with a lot of empty streams (to
delete those streams, you use [this Lambda](https://github.com/kdgregory/aws-misc/tree/master/lambda/cloudwatch-log-cleanup)).

Also be aware that you can't pick an arbitrary number of days for this parameter: the
[CloudWatch API doc](https://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_PutRetentionPolicy.html)
lists allowable values, and the appender will check your configured value against the
list. If you pick an incorrect value, an error will be logged and the setting will be
ignored.

If you don't have permission to set the retention policy, that will also be logged as
an error and the setting ignored.


## InvalidSequenceTokenException and Logstream Throttling

When writing a batch of events to CloudWatch Logs, you must provide a sequence token. You
get this token from either the response to the previous call to `PutLogEvents`, or by calling
`DescribeLogStreams`. If possible, you want to avoid the latter, because it's limited to five
calls per second. However, if you have multiple appenders writing to the same stream, it's the
only way to ensure that you get the latest token.

The original (pre-2.2.2) implementation of the appender would always call `DescribeLogStreams`,
because it assumed that another appender might be writing to the same log stream. However, if
you _did_ give each appender its own stream, this was unnecessary, and could result in throttling,
especially when deploying a large cluster.

To resolve that problem, the `dedicatedWriter` configuration parameter was introduced in the
2.2.2 release. It defaults to `true`, which tells the appender to cache the sequence token from
the previous request, rather than describing the stream prior to each write.

> The appender will continue to work properly with this default if there are multiple writers:
  it will try to write using the cached sequence token (which fails), then retrieve the current
  sequence token and retry.

If you do have multiple writers, however, the two-step process is still necessary. And it has
the possibility of a race condition: if two appenders write batches at the same time they might
both get the same sequence token, but only one of the writes will succeed. As with any batch-level
error, the appender will try again; there will be no message loss unless the error happens so
frequently that the appender fills its message queue (which is extremely unlikely, as the chance
of winning this race is the same for all appenders).

In releases prior to 2.0.1, the appender would report this situation as an error, which was
distracting. Retries are now transparent, unless they happen repeatedly, in which case they'll
be reported as a warning using the logging framework's internal logger (again, the batch is
retried, so the messages won't be lost). Race retries and batches required due to repeated
retries are also reported as logger statistics, available via JMX.

If you do have a large number of writer races, you can either switch to a single stream per
appender (preferred), or increase the batch delay so that writes happen less frequently.


## Oversize Messages

CloudWatch has a maximum message size of 262,118 bytes (the 
[documented event size](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/cloudwatch_limits_cwl.html)
is 256k, but this includes 26 bytes of overhead). While most logged messages won't exceed
this limit, some (in particular, Spring exception traces) might. How the appender handles
this depends on the `truncateOversizeMessages` configuration setting:

* If `true` (the default), the message is truncated to the maximum allowed size. This is appropriate
  for simple text messages, as it preserves as much information as possible. However, it will corrupt
  messages formatted using JSON.
* If `false`, the message is discarded.

In either case, the oversize message is logged in the framework's internal status logger. The number
of oversize messages is available through the JMX `oversizeMessages` attribute.
