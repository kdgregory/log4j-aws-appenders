# Simple Notification Service (SNS)

The SNS appender is intended to support real-time error notifications, operating concurrently
with other logging outputs. You would configure the appender to only respond to messages with
the ERROR level, and then hook the destination SNS topic to feed a messaging application.

The SNS appender provides the following features:

* [x] Configurable destination topic, with substitution variables to specify topic name
* [x] Optional message subjects, useful for email reporting.
* [x] Auto-creation of topics
* [x] Configurable discard in case of network connectivity issues


## Configuration

Your Log4J configuration will look something like this (note the `threshold` setting):

```
log4j.rootLogger=ERROR, sns

log4j.appender.sns=com.kdgregory.log4j.aws.SNSAppender
log4j.appender.sns.threshold=ERROR
log4j.appender.sns.topicArn=arn:aws:sns:us-east-1:123456789012:LoggingExample
log4j.appender.sns.subject=Error from {env:APPNAME}

log4j.appender.sns.layout=org.apache.log4j.PatternLayout
log4j.appender.sns.layout.ConversionPattern=%d [%t] %-5p %c %x - %m%n
```

The appender provides the following properties (also described in the JavaDoc):

Name                | Description
--------------------|----------------------------------------------------------------
`topicName`         | The name of the SNS topic that will receive messages; no default value. See below for more information.
`topicArn`          | The ARN of the SNS topic that will receive messages; no default value. See below for more information.
`subject`           | If used, attaches a subject to each message sent; no default value. See below for more information.
`discardThreshold`  | The threshold count for discarding unsent messages; default is 1,000. See [design doc](design.md#message-discard) for more information.
`discardAction`     | Which messages will be discarded once the threshold is passed: `oldest` (the default), `newest`, or `none`.


## Permissions

To use this appender you will need to grant the following IAM permissions:

* `sns:CreateTopic` (may be omitted if topic already exists)
* `sns:ListTopics`
* `sns:Publish`


## Operation

The SNS appender writes messages as simple text strings, formatted according to the layout manager;
it does not support platform-specific payloads. Messages can have an optional subject, and this text
may use [substitutions](substitutions.md). This is useful to identify the source of a message when
sending to an email address. Note that substitutions are applied when the appender starts, not on a
per-message basis.

You can specify the destination topic either by ARN or name. When specifying topic by ARN, the topic
must already exist. If you specify the topic by name, the appender will attempt to find an existing
topic with the specified name. If unable to find an existing topic it will create the topic.

> The appender assumes that, when listing topics, it will only receive topics for the current region.
  That constraint is _not_ explicitly stated in the [API doc](http://docs.aws.amazon.com/sns/latest/api/API_ListTopics.html)o
  but is the current observed behavior. If this behavior ever changes, the appender may choose aan
  unexpected topic.

You may use [substitutions](substitutions.md) in either the topic name or ARN. When constructing an
ARN it's particularly useful to use `{env:AWS_REGION}` or `{ec2:region}` along with `{aws:accountId}`.

While the appender exposes the batch delay configuration parameters, these are ignored. Each message
will be sent as soon as possible after it's passed to the appender (SNS does not support message batching).
