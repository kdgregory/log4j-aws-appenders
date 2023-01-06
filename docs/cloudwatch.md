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
`retentionPeriod`           | (optional) Specifies a non-default retention period for created CloudWatch log groups.
`synchronous`               | If `true`, the appender will operate in [synchronous mode](design.md#synchronous-mode), sending messages from the invoking thread on every call to `append()`. This is _extremely_ inefficient.
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
that you will need. Instead, this appender automatically creates groups and streams (and,
unlike the Kinesis and SNS appenders, there is no way to disable this behavior).

As a best practice, log group names should usually identify the application, with log stream
names that identify the instance of the application (typically some combination of hostname,
process ID, and timestamp).

You can specify a retention period when creating a, and  CloudWatch will automatically delete
messages older messages. Beware, however, that CloudWatch does _not_ delete the log streams
that held those messages; you may end up with a lot of empty streams (to delete those streams,
you can use [this Lambda](https://github.com/kdgregory/aws-misc/tree/master/lambda/cloudwatch-log-cleanup)).

Also be aware that you can't pick an arbitrary number of days for this parameter: the
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

Prior to January 2023, the `PutLogEvents` API call required a sequence token. You would get
the initial token from `DescribeLogStreams`, and subsequent tokens from the `PutLogEvents`
response. If two (or more) writers wrote to the same log stream, they would need to constantly
call `DescribeLogStreams` to refresh their tokens, and be prepared for `InvalidSequenceTokenException`
in the event that another writer managed to write its batch between the first writer's describe
and put.

The original version of this library performed such checks on every write. This was a performance
hit in the common case, where each appender wrote to its own stream. In release 2.2.2, the
`dedicatedWriter` configuration property was added: if `true`, the writer would assume that
it was the only writer on a stream, and only retrieve a new sequence token if it received an
exception (ie, it was not actually the only writer). In that release, the default value was
`false` for consistency with earlier behavior; in release 3.0.0 the default value was set to
`true`.

At this point in time, you no longer need to use the `dedicatedWriter` property. However,
**if you are running version 2.x you should upgrade to version 3.x**. If you don't, then
you'll get the (now completely pointless) behavior of retrieving a sequence token before
each write.
