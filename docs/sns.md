# Simple Notification Service (SNS)

The SNS appender is intended to support real-time error notifications, operating concurrently
with other logging outputs. You would configure the appender to only respond to messages with
the ERROR level, and then hook the destination SNS topic to feed a messaging application.


## Configuration

Your Log4J configuration will look something like this:

    log4j.rootLogger=INFO, sns

    log4j.appender.sns=com.kdgregory.log4j.aws.SNSAppender
    log4j.appender.sns.layout=org.apache.log4j.PatternLayout
    log4j.appender.sns.layout.ConversionPattern=%d [%t] %-5p %c %x - %m%n

    log4j.appender.sns.topicArn=arn:aws:sns:us-east-1:123456789012:LoggingExample


The appender provides the following properties (also described in the JavaDoc):

Name                | Description
--------------------|----------------------------------------------------------------
`topicArn`          | The SNS topic that will receive messages. You can use [substitutions](substitutions.md) in this value; `aws:accountId` is particularly useful.
`retries`           | The number of times that the appender will try to send a message before dropping it. Default is 3.


## Operation

At present the SNS appender writes messages as simple text strings, formatted according to the layout
manager; it does not support platform-specific payloads.

Unlike other appenders in this library, the SNS appender does not attempt to batch messages; it will send
them as soon as possible after they are logged. While you can configure batching and discard parameters
(they are common to all appenders) such configuration will have no effect.

Like the other appenders, this appender will attempt to create the topic if it does not already exist.
This functionality is of dubious utility: the newly created topic won't have any subscriptions, so any
messages sent to it will be lost.


## Permissions

To use this appender you will need to grant the following IAM permissions:

* `sns:CreateTopic`
* `sns:ListTopics`
* `sns:Publish`
