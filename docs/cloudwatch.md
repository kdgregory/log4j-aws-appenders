# CloudWatchAppender

The CloudWatch appender provides the following features:

* User-specified log-group and log-stream names.
* Substitution variables to customize log-group and log-stream names.
* JSON messages (via [JsonLayout](jsonlayout.md)).
* Configurable discard in case of network connectivity issues.


## Configuration

This appender provides the following configuration properties, along with the common [connection properties](client.md#configuration-properties).

Name                        | Description
----------------------------|----------------------------------------------------------------
`logGroup`                  | Name of the CloudWatch log group where messages are sent; may use [substitutions](substitutions.md). If this group doesn't exist it will be created. No default.
`logStream`                 | Name of the CloudWatch log stream where messages are sent; may use [substitutions](substitutions.md). If this stream doesn't exist it will be created. Defaults to `{startupTimestamp}`.
`retentionPeriod`           | Specifies a non-default retention period for auto-created CloudWatch log groups. If omitted, the groups retain messages forever. See [below](#retention-policy) for more information.
`dedicatedWriter`           | Obsolete. See [below](#sequence-tokens) for details.
`synchronous`               | If `true`, the appender operates in [synchronous mode](design.md#synchronous-mode), sending messages from the invoking thread on every call to `append()`. This is _extremely_ inefficient.
`batchDelay`                | The time, in milliseconds, that the writer will wait to accumulate messages for a batch. See the [design doc](design.md#message-batches) for more information.
`truncateOversizeMessages`  | If `true` (the default), truncate any messages that are too large for CloudWatch; if `false`, it discards them. See [below](#oversize-messages) for more information.
`discardThreshold`          | The maximum number of messages that can remain queued before they're discarded; default is 10,000. See the [design doc](design.md#message-discard) for more information.
`discardAction`             | Which messages will be discarded once the threshold is passed: `oldest` (the default), `newest`, or `none`.
`useShutdownHook`           | Controls whether the appender uses a shutdown hook to attempt to process outstanding messages when the JVM exits. This is `true` by default; set to `false` to disable. Ignored for Log4J2, which has its own shutdown hook. See [docs](design.md#shutdown) for more information.
`initializationTimeout`     | The number of milliseconds to wait for initialization; default is 60000 (60 seconds). See [docs](design.md#initialization) for more information.


### Example: Log4J 1.x

```
log4j.appender.cloudwatch=com.kdgregory.log4j.aws.CloudWatchAppender
log4j.appender.cloudwatch.logGroup={env:APP_NAME}-{sysprop:deployment:dev}
log4j.appender.cloudwatch.logStream={hostname}-{startupTimestamp}

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
    <PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %p - %c - %m" />
</CloudWatchAppender>
```


### Example: Logback

```
<appender name="CLOUDWATCH" class="com.kdgregory.logback.aws.CloudWatchAppender">
    <logGroup>{env:APP_NAME}-{sysprop:deployment:dev}</logGroup>
    <logStream>{hostname}-{startupTimestamp}</logStream>
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

As a general best practice, I recommend that log group names identify the application,
and log stream names identify the instance of the application (typically some combination
of hostname, process ID, and timestamp).

### Auto-Create

This appender automatically creates log groups and log streams if they do not already
exist (and, unlike the Kinesis and SNS appenders, there is no way to disable this
behavior).

However, I recommend creating your log _groups_ in advance, especially if you will have
multiple processes writing to the same log group. This will avoid several behaviors
described in [issue 184](https://github.com/kdgregory/log4j-aws-appenders/issues/184):

* CloudWatch Logs Insights is unable to retrieve events with timestamps earlier than
  the log group creation time. This should only affect the Log4J1 appender, which
  auto-creates the group and stream with the first batch of messages (unlike Log4J2
  and Logback, Log4J1 does not provide an explicit appender initialization phase).
* I have observed a situation in which the `GetLogEvents` and `FilterLogEvents` API
  calls do not retrieve early log events from a stream, while Insights and S3 export
  do. This appears to be caused by concurrent attempts to create log groups, and may
  be limited to writes that don't include sequence tokens (which this version of the
  library does not do, see [below](#sequence-tokens)).

### Retention Policy

You can specify a retention period when creating a log group, and  CloudWatch will delete
delete older messages automatically. Beware, however, that CloudWatch does _not_ delete
the log streams that held those messages; you may end up with a lot of empty streams. To
delete those streams, you can use [this Lambda](https://github.com/kdgregory/aws-misc/tree/master/lambda/cloudwatch-log-cleanup).

Also be aware that you can't pick an arbitrary number of days for retention: the
[CloudWatch API doc](https://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_PutRetentionPolicy.html)
lists allowable values, and the appender will check your configured value against the
list. If you pick an incorrect value, an error will be logged and the setting will be
ignored.

If you don't have permission to set the retention policy, that will also be logged as
an error and the setting ignored.


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


## Sequence Tokens

Historically, CloudWatch Logs required a sequence token with every `PutLogEvents`
request. You would get an initial token from `DescribeLogStreams`, and subsequent
tokens from `PutLogEvents`. If there were concurrent writers, then only one would
succeed; the others would receive `InvalidSequenceTokenException`, and would have
to call `DescribeLogStreams` (which was rate limited) to get a new token.

The initial versions of this library retrieved sequence tokens before every write,
which was inefficient (and could fail due to rate limits). In version 2.2.2, it
introduced the `dedicatedWriter` parameter: if `true`, then the appender would reuse
the value from `PutLogEvents`; if `false`, it would retrieve a new value for each
write. To maintain backwards compatibility, the default value was `false`; in
release 3.0.0, the default value became `true`.

In January 2023, AWS changed their API so that it no longer used sequence tokens;
any token provided in the request would be ignored.

This libary has not yet been updated, due to [observed issues](https://github.com/kdgregory/log4j-aws-appenders/issues/184)
with concurrent writes. However, if you're explicitly setting `dedicatedWriter`
to `false` you can remove it from your logging config. And if you're using an
old version of the appender, you should upgrade to the latest 3.x version.
