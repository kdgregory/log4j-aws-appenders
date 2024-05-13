# JSON Layout

While the standard "pattern" layouts work well for humans grepping logs, large-scale log management
revolves around tools such as [Elasticsearch](https://www.elastic.co/products/elasticsearch) to filter
and visualize log data.  However, Elasticsearch wants JSON as input, and while you can use a Lambda to
parse text logs, it's faster and less error-prone to simply send JSON.

This layout transforms the raw logging event into JSON, adding optional information such as the server's
hostname, EC2 instance ID, and user-defined metadata. You can use it with any appender, not just the
ones in this library.

For Logback, there's also [JsonAccessLayout](jsonaccesslayout.md), which similarly formats access logs.

The Log4J 2.x library does not provide this class; see [below](#log4j2-support) for more information.


## Configuration

The complete list of properties is as follows (also available in the JavaDoc). Boolean properties are
explicitly enabled with the case-insensitive value "true", explicitly disabled with the case-insensitive
value "false", and default to "false" unless otherwise noted.

 Name                   | Type      | Description
------------------------|-----------|----------------------------------------------------------------------------------------------------------------
`appendNewlines`        | Boolean   | If "true", a newline will be appended to each record (default is false). This is useful when sending logging output to a file, particularly one read by an agent.
`enableAccountId`       | Boolean   | If "true", the JSON will include the AWS account ID that the application is running under.
`enableHostname`        | Boolean   | Defaults to "true", including the logging server's hostname in the output; may be disabled by setting to "false".
`enableInstanceId`      | Boolean   | If "true", the JSON will include the EC2 instance ID where the application is running. *WARNING*: This is retrieved from EC2 metadata, and will delay application startup if you're not running on EC2.
`enableKeyValuePairs`   | Boolean   | (Logback only, version >= 1.3.0) If "true", the JSON will include a sub-object named "extra" that contains key-value pairs that were added to event by application.
`enableLocation`        | Boolean   | If "true", the JSON will include a sub-object that holds the location (class, source file, and line number) where the log message was written. This adds to the cost of every logging message so should not be enabled in production.
`tags`                  | String    | If present, the JSON will include a sub-object with specified user metadata. See [below](#metadata) for more information.


## Data

The generated JSON object will have the following properties, some of which are optional:

 Key            | Value
----------------|------------------------------------------------------------------------------------------------------------------------
 `timestamp`    | The date/time that the message was logged, formatted as a UTC [ISO-8601](https://en.wikipedia.org/wiki/ISO_8601) timestamp with milliseconds (example: `2017-10-15T23:19:02.123Z`).
 `thread`       | The name of the thread where the message was logged.
 `logger`       | The name of the logger (normally the class that's writing the message, but you can use custom loggers).
 `level`        | The level of the log message: DEBUG, INFO, WARNING, ERROR.
 `message`      | The logged message.
 `exception`    | The stack trace of an associated exception, if one exists. This is exposed as an array of strings, corresponding to the separate lines from `Throwable.printStackTrace()`: the first line identifies the exception, subsequent lines contain the stack trace.
 `hostname`     | The name of the machine where the logger is running, if available and configured (this is retrieved from `RuntimeMxBean` and may not be available on all platforms).
 `processId`    | The PID of the invoking process, if available (this is retrieved from `RuntimeMxBean` and may not be available on all platforms).
 `mdc`          | The mapped diagnostic context, if it exists. This is a child map containing whatever entries are in the MDC.
 `ndc`          | Log4J only: The nested diagnostic context, if it exists. This is a single string that contains each of the pushed entries separated by spaces (yes, that's how Log4J provides it).
 `locationInfo` | The location where the logger was called, if enabled. This is a child object with the following components: `className`, `methodName`, `fileName`, `lineNumber`.
 `accountId`    | The AWS account ID used by the application, if enabled.
 `instanceId`   | The EC2 instance ID of the machine where the logger is running, if enabled.
 `tags`         | Optional sub-object containing user-specified metadata; see below.


## Metadata

The `tags` property is intended to provide metadata for search-based log analysis. It is specified using
a comma-separated list of `NAME=VALUE` pairs, and results in the creation of a `tags` sub-object in the log
message (see example below). Values may include [substitutions](substitutions.md), which are evaluated when
the layout is instantiated.


## Example

This configuration:

```
log4j.appender.example.layout=com.kdgregory.log4j.aws.JsonLayout
log4j.appender.example.layout.appendNewlines=true
log4j.appender.example.layout.enableHostname=true
log4j.appender.example.layout.enableLocation=true
log4j.appender.example.layout.tags=applicationName=Example,env={sysprop:env:dev},runDate={date}
```

Will produce lines of output that look like this (assuming that the application is invoked with `-Denv=prod`):

```
{"exception":["java.lang.Exception: sample exception","\tat com.kdgregory.log4j.aws.example.Main$LogGeneratorRunnable.run(Main.java:100)","\tat java.lang.Thread.run(Thread.java:745)"],"hostname":"peregrine","level":"ERROR","locationInfo":{"className":"com.kdgregory.log4j.aws.example.Main$LogGeneratorRunnable","fileName":"Main.java","lineNumber":"100","methodName":"run"},"logger":"com.kdgregory.log4j.aws.example.Main","message":"value is 95","processId":"25456","tags":{"applicationName":"Example","env":"prod","runDate":"20180908"},"thread":"example-0","timestamp":"2018-09-08T18:41:22.476Z"}
```

Which, when pretty-printed, looks like this:

```
{
    "exception": [
        "java.lang.Exception: sample exception",
        "at com.kdgregory.log4j.aws.example.Main$LogGeneratorRunnable.run(Main.java:100)",
        "at java.lang.Thread.run(Thread.java:745)"
    ],
    "hostname": "peregrine",
    "level": "ERROR",
    "locationInfo": {
        "className": "com.kdgregory.log4j.aws.example.Main$LogGeneratorRunnable",
        "fileName": "Main.java",
        "lineNumber": "100",
        "methodName": "run"
    },
    "logger": "com.kdgregory.log4j.aws.example.Main",
    "message": "value is 95",
    "processId": "25456",
    "tags": {
        "applicationName": "Example",
        "env": "prod",
        "runDate": "20180908"
    },
    "thread": "example-0",
    "timestamp": "2018-09-08T18:41:22.476Z"
}
```

## Log4J2 Support

Log4J 2.x provides its own [JsonLayout](https://logging.apache.org/log4j/2.x/log4j-core/apidocs/org/apache/logging/log4j/core/layout/JsonLayout.html).
The output of the Log4J layout does not use the same field names as the JSON layout from this
project, but offers the ability to customize the output with additional fields. As a result,
I decided not to implement a Log4J2 JSON layout manager. Instead, you can get almost the same
results using the following configuration:

```
<JsonLayout complete="false" compact="true" eventEol="true" properties="true" locationInfo="true">
    <KeyValuePair key="timestamp" value="$${date:yyyy-MM-dd'T'HH:mm:ss.SSSZ}" />
    <KeyValuePair key="processId" value="${awslogs:pid}" />
    <KeyValuePair key="hostname" value="${awslogs:hostname}" />
    <KeyValuePair key="applicationName" value="Example" />
    <KeyValuePair key="environment" value="${sys:environment:-dev}" />
    <KeyValuePair key="runDate" value="${date:yyyy-MM-dd}" />
</JsonLayout>
```

The output (again pretty-printed) looks like this:

```
{
	"timeMillis": 1584102479182,
	"thread": "example-0",
	"level": "DEBUG",
	"loggerName": "com.kdgregory.log4j2.aws.example.Main",
	"message": "value is 52",
	"endOfBatch": false,
	"loggerFqcn": "org.apache.logging.log4j.spi.AbstractLogger",
	"contextMap": {},
	"threadId": 12,
	"threadPriority": 5,
	"source": {
		"class": "com.kdgregory.log4j2.aws.example.Main$LogGeneratorRunnable",
		"method": "updateValue",
		"file": "Main.java",
		"line": 115
	},
	"timestamp": "2020-03-13T08:27:59.182-0400",
	"processId": "18816",
	"hostname": "ithilien",
	"applicationName": "Example",
	"environment": "dev",
	"runDate": "2020-03-13"
}
```

Some things to note:

* This example uses the `awslogs` substitutions to retrieve process ID and hostname; see
  [substitutions](substitutions.md#log4j2-support) for more information.
* The Log4J2 `JsonLayout` writes the event timestamp as `timeMillis`, a count of milliseconds
  since the epoch. To maintain consistency with the Logback and Log4J1 `JsonLayout` I added a
  `timestamp` field, and used a Log4J2 substitution to format it as ISO-8601 (to avoid
  confusing Elasticsearch, which wants a single timestamp field per index).
* The Log4J2 `JsonLayout` writes the logger name as `loggerName`, thread as `threadId`, MDC as
  `contextMap`, and location information as `source`. These names, along with the fields inside
  the location info, are different from those written by the other appenders in this library.
  If you do field-level searches and have multiple logging libraries in use you will need to
  look for both.
