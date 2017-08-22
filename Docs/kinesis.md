# Kinesis Streams

The Kinesis Streams appender is intended to be an entry point for log analytics, either
as a direct feed to an analytics application, or via Kinesis Firehose to ElasticSearch
or other destinations (note that this can also be an easy way to back-up logs to S3).

The Kinesis implementation provides (will provide) the following features:

* [x] Configurable destination stream, with substitution variables to specify stream name
* [ ] Auto-creation of streams, with configurable number of shards
* [ ] JSON messages (via layout)

## Configuration

Your Log4J configuration will look something like this:

    log4j.rootLogger=DEBUG, default

    log4j.appender.default=com.kdgregory.log4j.aws.KinesisAppender
    log4j.appender.default.layout=org.apache.log4j.PatternLayout
    log4j.appender.default.layout.ConversionPattern=%d [%t] %-5p %c %x - %m%n

    log4j.appender.default.streamName={env:APP_NAME}
    log4j.appender.default.batchDelay=1000
    log4j.appender.default.rotationMode=daily


The appender provides the following properties (also described in the JavaDoc):

Name                | Description
--------------------|----------------------------------------------------------------
`batchDelay`        | The time, in milliseconds, that the writer will wait to accumulate messages for a batch. See below for more information.
`partitionKey`      | A string used to assign messages to shards; see below for more information.


`shards`            | When creating a stream, the number of shards to use. Defaults to 1.
`retentionPeriod`   | When creating a stream, the number of hours to set as its retention period. Defaults to 24.
