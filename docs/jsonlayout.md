# JSON Layout

While the standard Log4J [PatternLayout](http://logging.apache.org/log4j/1.2/apidocs/org/apache/log4j/PatternLayout.html)
works well for humans grepping logs, large-scale log management revolves around tools such as 
[ElasticSearch](https://www.elastic.co/products/elasticsearch), which can filter and visualize
log data. AWS provides a hosted ElasticSearch solution, and can direct Kinesis traffic to an ES
cluster using the [Kinesis Firehose](http://docs.aws.amazon.com/firehose/latest/dev/create-destination.html#create-destination-elasticsearch).
However, ElasticSearch wants JSON as input; and while you can [use Lambda to transform
records](http://docs.aws.amazon.com/firehose/latest/dev/data-transformation.html#lambda-blueprints),
that will increase your logging costs.

You can find several solutions for writing JSON via Log4J. I decided to write my own so that I
could tailor its features and also include AWS-only data (right now limited to the EC2 instance
ID).


## Usage

You can use the JSON layout with any appender (including the standard Log4J appenders, but beware
that you will need the AWS SDK in your classpath). However, it's most useful with the Kinesis
appender, feeding a [Kinesis Firehose](http://docs.aws.amazon.com/firehose/latest/dev/what-is-this-service.html)
delivery stream that feeds an ElasticSearch cluster. Setting up such a cluster is beyond the scope
of this document, but see the [example CloudFormation template](../example/cloudformation.json)
for a starting point.

Your appender configuration will look something like this:

    log4j.appender.kinesis=com.kdgregory.log4j.aws.KinesisAppender
    log4j.appender.kinesis.streamName=AppenderExample
    log4j.appender.kinesis.layout=com.kdgregory.log4j.aws.JsonLayout
    log4j.appender.kinesis.layout.tags=applicationName=Example,runDate={date}
    log4j.appender.kinesis.layout.enableHostname=true
    log4j.appender.kinesis.layout.enableInstanceId=true
    log4j.appender.kinesis.layout.enableLocation=true

The various "enable" properties are used to enable content that is potentially expensive to
generate.  In particular, `enableInstanceId` enables the inclusion of the EC2 instance ID;
if you aren't running on EC2, this will introduce a delay of possibly several minutes while
the SDK tries to retrieve the value.

The `tags` property allows you to specify application-specific tags, including the use of
[substitutions](substitutions.md). These are specified as a comma-separated list of
`NAME=VALUE` pairs. Needless to say, you can't embed either `=` or `,` in the value.


## Data

The JSON layout transforms the Log4J `LoggingEvent` into JSON, with the addition of data
from the layout configuration. The resulting JSON will have the following items:

 Key            | Value
----------------|------------------------------------------------------------------------------------------------------------------------
 `timestamp`    | The date/time that the message was logged, formatted as an [ISO-8601](https://en.wikipedia.org/wiki/ISO_8601) timestamp with milliseconds (example: `2017-10-15T23:19:02.123Z`)
 `thread`       | The name of the thread where the message was logged.
 `logger`       | The name of the logger (normally the class that's invoking the logger).
 `level`        | The level of the log message.
 `message`      | The message itself.
 `processId`    | The PID of the invoking process, if available (this is retrieved from `RuntimeMxBean` and may not be available on all platforms).
 `exception`    | The stack trace of an associated exception, if one exists. This is exposed as an array of strings, with the first element being the location where the exception was caught.
 `mdc`          | The mapped diagnostic context, if it exists. This is a child map containing whatever entries are in the MDC.
 `ndc`          | The nested diagnostic context, if it exists. This is a single string that contains each of the pushed entries separated by spaces (yes, that's how Log4J provides it).
 `locationInfo` | The location where the logger was called. This is a child object with the following components: `className`, `methodName`, `fileName`, `lineNumber`.
 `instanceId`   | The EC2 instance ID of the machine where the logger is running. WARNING: do not enable this elsewhere, as the operation to retrieve this value may take a long time.
 `hostname`     | The name of the machine where the logger is running, if available (this is currently retrieved from `RuntimeMxBean` and may not be available on all platforms).
 `tags`         | Tags defined as part of the logger configuration (omitted if not defined). This is a child object.


## Example

The raw output looks like this:

```
{"hostname":"ip-172-30-1-182","instanceId":"i-0a287ad2dc13d9d2e","level":"DEBUG","locationInfo":{"className":"com.kdgregory.log4j.aws.example.Main$1","fileName":"Main.java","lineNumber":"50","methodName":"run"},"logger":"com.kdgregory.log4j.aws.example.Main","message":"value is 60","processId":"3012","tags":{"applicationName":"Example","runDate":"20171016"},"thread":"example-0","timestamp":"2017-10-16T00:24:56.998Z"}
```

Running it through a pretty-printer, you get this:

```
{
	"hostname": "ip-172-30-1-182",
	"instanceId": "i-0a287ad2dc13d9d2e",
	"level": "DEBUG",
	"locationInfo": {
		"className": "com.kdgregory.log4j.aws.example.Main$1",
		"fileName": "Main.java",
		"lineNumber": "50",
		"methodName": "run"
	},
	"logger": "com.kdgregory.log4j.aws.example.Main",
	"message": "value is 60",
	"processId": "3012",
	"tags": {
		"applicationName": "Example",
		"runDate": "20171016"
	},
	"thread": "example-0",
	"timestamp": "2017-10-16T00:24:56.998Z"
}
```
