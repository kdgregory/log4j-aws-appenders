# Simple Notification Service (SNS)

The SNS appender is intended to support real-time error notifications, operating concurrently
with other logging outputs. You would configure the appender to only respond to messages with
the ERROR level, and then hook the destination SNS topic to feed a messaging application.

The SNS appender provides the following features:

* Configurable destination topic, with substitution variables to specify topic name.
* Optional message subjects, useful for email reporting.
* Auto-creation of topics.
* Configurable discard in case of network connectivity issues.


## Configuration

The appender provides the following properties (also described in the JavaDoc):

Name                | Description
--------------------|----------------------------------------------------------------
`topicName`         | The name of the SNS topic that will receive messages; may use [substitutions](substitutions.md). No default value. See below for more information.
`topicArn`          | The ARN of the SNS topic that will receive messages; may use [substitutions](substitutions.md). No default value. See below for more information.
`autoCreate`        | If present and "true", the topic will be created if it does not already exist. This may only be used when specifying topic by name, not ARN.
`subject`           | If used, attaches a subject to each message sent; no default value. See below for more information.
`discardThreshold`  | The threshold count for discarding messages; default is 10,000. See [design doc](design.md#message-discard) for more information.
`discardAction`     | Which messages will be discarded once the threshold is passed: `oldest` (the default), `newest`, or `none`.
`clientFactory`     | Specifies the fully-qualified name of a static method that will be used to create the AWS service client via reflection. See [service client doc](service-client.md) for more information.
`clientEndpoint`    | Specifies a non-default endpoint for the client (eg, "logs.us-west-2.amazonaws.com"). See [service client doc](service-client.md) for more information.

Note: the `batchDelay` parameter is not used (although it can be configured); the SNS appender attempts to send messages immediately.


### Example

Note the `threshold` setting; this is a Log4J feature that allows different appenders to receive different levels of output.

```
log4j.rootLogger=ERROR, sns

log4j.appender.sns=com.kdgregory.log4j.aws.SNSAppender
log4j.appender.sns.threshold=ERROR
log4j.appender.sns.topicArn=arn:aws:sns:us-east-1:123456789012:LoggingExample
log4j.appender.sns.subject=Error from {env:APPNAME}

log4j.appender.sns.layout=org.apache.log4j.PatternLayout
log4j.appender.sns.layout.ConversionPattern=%d [%t] %-5p %c %x - %m%n
```


## Permissions

To use this appender you will need to grant the following IAM permissions:

* `sns:ListTopics`
* `sns:Publish`

In addition, to auto-create a topic you need to grant the following permissions:

* `sns:CreateTopic`


## Operation

The SNS appender writes messages as simple text strings, formatted according to the layout manager;
it does not support platform-specific payloads. Messages can have an optional subject, and this text
may use [substitutions](substitutions.md). This is useful to identify the source of a message when
sending to an email address. Note that substitutions are applied when the appender starts, not on a
per-message basis.

You can specify the destination topic either by ARN or name. You would normally use ARN to reference
a topic in a different account or region, name to reference a topic in the current account/region. If
you specify the topic by name, you may also enable `autoCreate`, which will create the topic if it
does not already exist (this is only appropriate for development/test environments).

> The appender assumes that, when listing topics, it will only receive topics for the current region.
  That constraint is _not_ explicitly stated in the [AWS API docs](http://docs.aws.amazon.com/sns/latest/api/API_ListTopics.html)
  but is the current observed behavior. If this behavior ever changes, the appender may choose an
  unexpected topic if configured by name.

You may use [substitutions](substitutions.md) in either the topic name or ARN. When constructing an
ARN it's particularly useful to use `{env:AWS_REGION}` or `{ec2:region}` along with `{aws:accountId}`.

While the appender exposes the batch delay configuration parameters, these are ignored. Each message
will be sent as soon as possible after it's passed to the appender (SNS does not support message batching).
