# Kinesis Appender

The Kinesis Streams appender is intended to be an entry point for log analytics, either
as a direct feed to an analytics application, or via Kinesis Firehose to ElasticSearch
or other destinations (which is also an easy way to back-up logs to S3).

The Kinesis appender provides the following features:

* Configurable destination stream, with substitution variables to specify stream name.
* Auto-creation of streams, with configurable number of shards and retention period.
* JSON messages (via [JsonLayout](jsonlayout.md)).
* Configurable discard in case of network connectivity issues.


## Configuration

This appender provides the following configuration properties, along with the common [connection properties](client.md#configuration-properties).

Name                        | Description
----------------------------|----------------------------------------------------------------
`streamName`                | The name of the Kinesis stream that will receive messages; may use [substitutions](substitutions.md). No default value.
`partitionKey`              | A string used to assign messages to shards; see below for more information.
`autoCreate`                | If present and "true", the stream will be created if it does not already exist.
`shardCount`                | When creating a stream, specifies the number of shards to use. Defaults to 1.
`retentionPeriod`           | When creating a stream, specifies the retention period for messages in hours. Range is 24 to 8760; default is 24.
`synchronous`               | If `true`, the appender will operate in [synchronous mode](design.md#synchronous-mode), sending messages from the invoking thread on every call to `append()`.
`batchDelay`                | The time, in milliseconds, that the writer will wait to accumulate messages for a batch. See the [design doc](design.md#message-batches) for more information.
`truncateOversizeMessages`  | If `true` (the default), truncate any messages that are too large for Kinesis; if `false`, discard them. See [below](#oversize-messages) for more information.
`discardThreshold`          | The maximum number of messages that can remain queued before they're discarded; default is 10,000. See the [design doc](design.md#message-discard) for more information.
`discardAction`             | Which messages will be discarded once the threshold is passed: `oldest` (the default), `newest`, or `none`.
`useShutdownHook`           | Controls whether the appender uses a shutdown hook to attempt to process outstanding messages when the JVM exits. This is `true` by default; set to `false` to disable. Ignored for Log4J2, which has its own shutdown hook. See [docs](design.md#shutdown) for more information.
`initializationTimeout`     | The number of milliseconds to wait for initialization; default is 120000 (120 seconds). See [docs](design.md#initialization) for more information.


### Example: Log4J 1.x

```
log4j.appender.kinesis=com.kdgregory.log4j.aws.KinesisAppender
log4j.appender.kinesis.streamName=logging-stream
log4j.appender.kinesis.partitionKey={pid}
log4j.appender.kinesis.batchDelay=500

log4j.appender.kinesis.layout=com.kdgregory.log4j.aws.JsonLayout
log4j.appender.kinesis.layout.enableHostname=true
log4j.appender.kinesis.layout.enableLocation=true
log4j.appender.kinesis.layout.tags=applicationName={env:APP_NAME},deployment={sysprop:deployment:dev}
```


### Example: Log4J2

Note that this example uses Log4J [lookups](https://logging.apache.org/log4j/2.x/manual/lookups.html#EnvironmentLookup)
in addition to library-provided substitutions. It also uses the Log4J [JsonLayout](https://logging.apache.org/log4j/2.x/manual/layouts.html#JSONLayout)
with additional fields, including a `timestamp` field that's compatible with the Log4J1 and Logback
`JsonLayout` implementations.

```
<KinesisAppender name="KINESIS">
    <streamName>logging-stream</streamName>
    <partitionKey>{pid}</partitionKey>
    <batchDelay>500</batchDelay>
    <JsonLayout complete="false" compact="true" eventEol="true" properties="true" locationInfo="true">
        <KeyValuePair key="applicationName" value="${env:APP_NAME}" />
        <KeyValuePair key="environment" value="${sys:deployment:-dev}" />
        <KeyValuePair key="processId" value="${awslogs:pid}" />
        <KeyValuePair key="hostname" value="${awslogs:hostname}" />
        <KeyValuePair key="timestamp" value="$${date:yyyy-MM-dd'T'HH:mm:ss.SSS'Z'}" />
    </JsonLayout>
</KinesisAppender>
```


### Example: Logback

```
<appender name="KINESIS" class="com.kdgregory.logback.aws.KinesisAppender">
    <streamName>logging-stream</streamName>
    <partitionKey>{pid}</partitionKey>
    <batchDelay>500</batchDelay>
    <layout class="com.kdgregory.logback.aws.JsonLayout">
        <enableHostname>true</enableHostname>
        <enableLocation>true</enableLocation>
        <tags>applicationName={env:APP_NAME},deployment={sysprop:deployment:dev}</tags>
    </layout>
</appender>
```


## Permissions

To use this appender you need following IAM permissions:

* `kinesis:DescribeStreamSummary`
* `kinesis:PutRecords`

To auto-create a stream you need these additional permissions:

* `kinesis:CreateStream`
* `kinesis:IncreaseStreamRetentionPeriod`


## Stream management

You will normally pre-create the Kinesis stream and adjust its retention period and number of
shards based on your deployment's needs.

To support testing (and because it was the original behavior, copied from the CloudWatch appender),
you can optionally configure the appender to create the stream if it does not already exist. Unlike
the CloudWatch appender, if you delete the stream during use it will not be re-created even if you
enable this parameter.


## Partition Keys

Kinesis supports high-performance parallel writes via multiple shards per stream: each shard
can accept up to 1,000 records and/or 1 MB of data per second. To distribute data between
shards, Kinesis requires each record to have a partition key, and hashes that partition key
to determine which shard is used to store the record.

The Kinesis appender can be configured with an explicit partition key, which is applied to
all messages generated by that appender. It can also be configured with a "random" parttion
key, to distribute messages to all available shared.

> The current implementation generates a six-digit numeric partition key. However, this
  behavior is an implementation detail and is subject to change in future releases.

Example configuration, explicit partition key (you could also use substitutions in this name):

```
log4j.appender.kinesis.partitionKey=MyApplication
```

Example configuration, random partition keys:

```
log4j.appender.kinesis.partitionKey={random}
```

Note: `{random}` is a special keyword, not a substitution value. Substitutions are applied
when the appender is created, not on a per-message basis.


## Retention Period

By default, a Kinesis stream retains messages for 24 hours. For an extra charge, you can
retain messages for up to 8760 hours (365 days). Prior to November 2020, this was 168 hours
(7 days).


## Oversize Messages

Kinesis has a maximum message size of 1,048,576 bytes minus the length of the partion key
([doc](https://docs.aws.amazon.com/kinesis/latest/APIReference/API_PutRecordsRequestEntry.html)).
While most logged messages won't exceed this limit, some (in particular, Spring exception traces)
might. How the appender handles this depends on the `truncateOversizeMessages` configuration setting:

* If `true` (the default), the message is truncated to the maximum allowed size. This is appropriate
  for simple text messages, as it preserves as much information as possible. However, it will corrupt
  messages formatted using JSON.
* If `false`, the message is discarded.

In either case, the oversize message is logged in the framework's internal status logger. The number
of oversize messages is available through the JMX `oversizeMessages` attribute.
