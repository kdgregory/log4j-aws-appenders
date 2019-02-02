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
`synchonous`        | If `true`, the appender will operate in [synchronous mode](design.md#synchronous-mode), sending messages from the invoking thread on every call to `append()`.
`discardThreshold`  | The threshold count for discarding messages; default is 10,000. See [design doc](design.md#message-discard) for more information.
`discardAction`     | Which messages will be discarded once the threshold is passed: `oldest` (the default), `newest`, or `none`.
`clientFactory`     | Specifies the fully-qualified name of a static method that will be invoked to create the AWS service client. See the [service client doc](service-client.md#client-creation) for more information.
`clientRegion`      | Specifies a non-default region for the client. See the [service client doc](service-client.md#endpoint-configuration) for more information.
`clientEndpoint`    | Specifies a non-default endpoint; only supported for clients created via constructor. See the [service client doc](service-client.md#endpoint-configuration) for more information.
`useShutdownHook`   | Controls whether the appender uses a shutdown hook to attempt to process outstanding messages when the JVM exits. This is `true` by default; set to `false` to disable. See [docs](design.md#shutdown-hooks) for more information.

Note: the `batchDelay` parameter is not used (although it can be configured); the SNS appender attempts to send messages immediately.


### Example: Log4J 1.x

Note the `threshold` setting; this is a Log4J feature that allows different appenders to receive different levels of output.

```
log4j.appender.sns=com.kdgregory.log4j.aws.SNSAppender
log4j.appender.sns.threshold=ERROR
log4j.appender.sns.topicArn=arn:aws:sns:us-east-1:123456789012:LoggingExample
log4j.appender.sns.subject=Error from {env:APPNAME}

log4j.appender.sns.layout=org.apache.log4j.PatternLayout
log4j.appender.sns.layout.ConversionPattern=%d %c - %m%n
```


### Example: Logback

```
<appender name="SNS" class="com.kdgregory.logback.aws.SNSAppender">
    <topicArn>arn:aws:sns:us-east-1:123456789012:LoggingExample</topicArn>
    <subject>Error from {env:APPNAME}</subject>
    <layout class="ch.qos.logback.classic.PatternLayout">
        <pattern>%d{HH:mm:ss.SSS} %logger{36} - %msg%n</pattern>
    </layout>
    <filter class="ch.qos.logback.classic.filter.LevelFilter">
        <level>ERROR</level>
        <onMatch>ACCEPT</onMatch>
        <onMismatch>DENY</onMismatch>
    </filter>
</appender>
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

While the appender exposes the batch delay configuration parameter, it is ignored. Each message is
sent as soon as possible after it's passed to the appender, because SNS does not support message batching.
Note, however, that the messages are still sent on a background thread unless you enable
[synchronous mode](docs/design.md#synchronous-mode).
