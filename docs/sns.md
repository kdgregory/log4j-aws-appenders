# Simple Notification Service (SNS) Appender

The SNS appender is intended to provide real-time error notifications, operating concurrently
with other logging outputs. You would configure the appender to only respond to messages with
the ERROR level, and then configure the destination SNS topic to feed a messaging application
such as PagerDuty.

The SNS appender provides the following features:

* Configurable destination topic, with substitution variables to specify topic name.
* Optional message subjects, useful for email reporting.
* Auto-creation of topics.
* Configurable discard in case of network connectivity issues.


## Configuration

This appender provides the following configuration properties, along with the common [connection properties](client.md#configuration-properties).

Name                        | Description
----------------------------|----------------------------------------------------------------
`topicName`                 | The name of the SNS topic that will receive messages; may use [substitutions](substitutions.md). No default value. See below for more information.
`topicArn`                  | The ARN of the SNS topic that will receive messages; may use [substitutions](substitutions.md). No default value. See below for more information.
`autoCreate`                | If present and "true", the topic will be created if it does not already exist. This may only be used when specifying topic by name, not ARN.
`subject`                   | If used, attaches a subject to each message sent; no default value. See below for more information.
`synchronous`               | If `true`, the appender will operate in [synchronous mode](design.md#synchronous-mode), sending messages from the invoking thread on every call to `append()`.
`truncateOversizeMessages`  | If `true` (the default), truncate any messages that are too large for SNS; if `false`, discard them. See [below](#oversize-messages) for more information.
`discardThreshold`          | The threshold count for discarding messages; default is 10,000. See [design doc](design.md#message-discard) for more information.
`discardAction`             | Which messages will be discarded once the threshold is passed: `oldest` (the default), `newest`, or `none`.
`useShutdownHook`           | Controls whether the appender uses a shutdown hook to attempt to process outstanding messages when the JVM exits. This is `true` by default, set to `false` to disable. Ignored for Log4J2. See [docs](design.md#shutdown) for more information.

Note: the `batchDelay` parameter exists but is ignored; the SNS appender attempts to send messages immediately.


### Example: Log4J 1.x

Note: the `threshold` setting ensures that this appender only receives ERROR-level messages.

```
log4j.appender.sns=com.kdgregory.log4j.aws.SNSAppender
log4j.appender.sns.threshold=ERROR
log4j.appender.sns.topicArn=arn:aws:sns:us-east-1:123456789012:LoggingExample
log4j.appender.sns.subject=Error from {env:APPNAME}

log4j.appender.sns.layout=org.apache.log4j.PatternLayout
log4j.appender.sns.layout.ConversionPattern=%d %c - %m%n
```


### Example: Log4J2

Note: the `ThresholdFilter` ensures that this appender only receives ERROR-level messages.

Note also that this example uses a Log4J [lookup](https://logging.apache.org/log4j/2.x/manual/lookups.html#EnvironmentLookup)
for the application name rather than the library-provided substitutions.

```
<SNSAppender name="SNS">
    <topicArn>arn:aws:sns:us-east-1:123456789012:LoggingExample</topicArn>
    <subject>Error from ${env:APP_NAME}</subject>
    <PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] - %c %p - %m%n" />
    <ThresholdFilter level="ERROR" onMatch="ACCEPT" onMismatch="DENY"/>
</SNSAppender>
```


### Example: Logback

Note: the `filter` configuration ensures that this appender only receives ERROR-level messages.

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

To use this appender you need the following IAM permissions:

* `sns:ListTopics`
* `sns:Publish`

To auto-create a topic you must also have the following permission:

* `sns:CreateTopic`


## Operation

You can specify the destination topic either by ARN or name. You would normally use ARN to reference
a topic in a different account or region, name to reference a topic in the current account/region. If
you specify the topic by name, you may also enable `autoCreate`, which will create the topic if it
does not already exist (this is only appropriate for development/test environments).

> When constructing an ARN it's particularly useful to use the `{env:AWS_REGION}` or `{ec2:region}`
  substitutions, along with `{aws:accountId}`.

The SNS appender writes messages as simple text strings, formatted according to the layout manager;
it does not support platform-specific payloads. Messages can have an optional subject, and this text
may use [substitutions](substitutions.md). This is useful to identify the source of a message when
sending to an email address.

> Note that substitutions are applied when the appender starts, not on a per-message basis. While
  you can programmatically change the subject in Log4J1 and Logback (and reconfigure the logger
  for Log4J2), all messages will have the same subject.

While the appender exposes the batch delay configuration parameter, it is ignored. Each message is
sent as soon as possible after it's passed to the appender, because SNS does not support message
batching. Note, however, that the messages are still sent on a background thread unless you enable
[synchronous mode](design.md#synchronous-mode).


## Oversize Messages

SNS has a maximum message size of 262,144 bytes ([doc](https://docs.aws.amazon.com/sns/latest/api/API_Publish.html).
While most logged messages won't exceed this limit, some (in particular, Spring exception traces)
might. How the appender handles this depends on the `truncateOversizeMessages` configuration setting:

* If `true` (the default), the message is truncated to the maximum allowed size. This is appropriate
  for simple text messages, as it preserves as much information as possible. However, it will corrupt
  messages formatted using JSON.
* If `false`, the message is discarded.

In either case, the oversize message is logged in the framework's internal status logger. The number
of oversize messages is available through the JMX `oversizeMessages` attribute.
