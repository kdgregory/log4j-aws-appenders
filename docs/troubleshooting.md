# When Things Go Wrong

If you don't see logging output, it is almost always due to incorrect IAM permissions. The documentation
for each appender lists the permissions that you will need to use that appender. It's also possible, when
using the Kinesis and SNS appenders, that the destination doesn't exist (or is in another region).

Fortunately, both logging frameworks support debug output, and the appenders report both successful and
non-successful operation.


## Log4J 1.x

For Log4J, you set the system property `log4j.configDebug` to "true". The easiest way to do this is when
starting your Java application:

```
java -Dlog4j.configDebug=true ...
```

The Log4J internal logger always writes output to standard error; if you're running your program as a
daemon you will need to redirect this output.

### Example: Successful configuration

For this example, I configured the [example program](../examples/log4j1-example) to
use only the CloudWatch Logs appender. When it fist starts you'll see the following
output, indicating that it was able to configure the appender. However, this doe
_not_ mean that the appender is able to write to the destination, because it's
initialized when the first message is written.

```
log4j: Trying to find [log4j.xml] using context classloader sun.misc.Launcher$AppClassLoader@70dea4e.
log4j: Trying to find [log4j.xml] using sun.misc.Launcher$AppClassLoader@70dea4e class loader.
log4j: Trying to find [log4j.xml] using ClassLoader.getSystemResource().
log4j: Trying to find [log4j.properties] using context classloader sun.misc.Launcher$AppClassLoader@70dea4e.
log4j: Using URL [jar:file:/tmp/examples/log4j1-example/target/log4j1-aws-appenders-example-2.1.0-SNAPSHOT.jar!/log4j.properties] for automatic log4j configuration.
log4j: Reading configuration from URL jar:file:/tmp/examples/log4j1-example/target/log4j1-aws-appenders-example-2.1.0-SNAPSHOT.jar!/log4j.properties
log4j: Parsing for [root] with value=[WARN, console, cloudwatch].
log4j: Level token is [WARN].
log4j: Category root set to WARN
log4j: Parsing appender named "console".
log4j: Parsing layout options for "console".
log4j: Setting property [conversionPattern] to [%d [%t] %-5p %c %x - %m%n].
log4j: End of parsing for "console".
log4j: Parsed "console" options.
log4j: Parsing appender named "cloudwatch".
log4j: Parsing layout options for "cloudwatch".
log4j: Setting property [conversionPattern] to [%d [%t] %-5p %c %x - %m%n].
log4j: End of parsing for "cloudwatch".
log4j: Setting property [logGroup] to [AppenderExample].
log4j: Setting property [logStream] to [Example-{date}-{hostname}-{pid}].
log4j: Parsed "cloudwatch" options.
log4j: Parsing for [com.kdgregory.log4j.aws.example] with value=[DEBUG].
log4j: Level token is [DEBUG].
log4j: Category com.kdgregory.log4j.aws.example set to DEBUG
log4j: Handling log4j.additivity.com.kdgregory.log4j.aws.example=[null]
log4j: Finished configuring.
```

Next up, you'll see the following output, as the appender initializes. Note that
this output is interleaved with the log messages sent to the console: that is an
artifact of the appender building a batch of messages.

```
2018-12-26 09:56:48,534 [example-1] WARN  com.kdgregory.log4j.aws.example.Main  - value is 85
log4j: CloudWatchAppender(cloudwatch): log writer starting on thread com-kdgregory-aws-logwriter-log4j-cloudwatch-0
2018-12-26 09:56:48,534 [example-0] DEBUG com.kdgregory.log4j.aws.example.Main  - value is 60
log4j: CloudWatchAppender(cloudwatch): created client from factory: com.amazonaws.services.logs.AWSLogsClientBuilder.defaultClient
log4j: CloudWatchAppender(cloudwatch): using existing CloudWatch log group: AppenderExample
log4j: CloudWatchAppender(cloudwatch): creating CloudWatch log stream: Example-20181226-ithilien-23285
log4j: CloudWatchAppender(cloudwatch): log writer initialization complete (thread com-kdgregory-aws-logwriter-log4j-cloudwatch-0)
2018-12-26 09:56:49,559 [example-1] WARN  com.kdgregory.log4j.aws.example.Main  - value is 88
```


### Example: missing destination

For this example, I enable the Kinesis appender but don't configure it to auto-create
the stream. I've removed the application log messages for clarity.

```
log4j: KinesisAppender(kinesis): log writer starting on thread com-kdgregory-aws-logwriter-log4j-kinesis-0
log4j: KinesisAppender(kinesis): created client from factory: com.amazonaws.services.kinesis.AmazonKinesisClientBuilder.defaultClient
log4j:ERROR KinesisAppender(kinesis): stream "AppenderExample" does not exist and auto-create not enabled
```


### Example: incorrect credentials

For this example I used my correct AWS access key but an incorrect secret key. As before, I
omit the application logging messages, as well as the full exception trace for the error.

```
log4j: CloudWatchAppender(cloudwatch): log writer starting on thread com-kdgregory-aws-logwriter-log4j-cloudwatch-0
log4j: CloudWatchAppender(cloudwatch): created client from factory: com.amazonaws.services.logs.AWSLogsClientBuilder.defaultClient
log4j:ERROR CloudWatchAppender(cloudwatch): unable to configure log group/stream
com.amazonaws.services.logs.model.AWSLogsException: The request signature we calculated does not match the signature you provided. Check your AWS Secret Access Key and signing method. Consult the service documentation for details.
```


### Example: missing credentials

For this example I removed my access key. You'll see a similar error if you're runing on an EC2
instance or Lambda that doesn't have an instance/execution role. 

```
log4j: CloudWatchAppender(cloudwatch): log writer starting on thread com-kdgregory-aws-logwriter-log4j-cloudwatch-0
log4j:ERROR CloudWatchAppender(cloudwatch): unable to configure log group/stream
com.amazonaws.SdkClientException: Unable to load AWS credentials from any provider in the chain: [EnvironmentVariableCredentialsProvider: Unable to load AWS credentials from environment variables (AWS_ACCESS_KEY_ID (or AWS_ACCESS_KEY) and AWS_SECRET_KEY (or AWS_SECRET_ACCESS_KEY)), SystemPropertiesCredentialsProvider: Unable to load AWS credentials from Java system properties (aws.accessKeyId and aws.secretKey), com.amazonaws.auth.profile.ProfileCredentialsProvider@6093ac52: profile file cannot be null, com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper@68e808e1: Unable to load credentials from service endpoint]

```


## Logback

For Logback, you enable debug mode in the configuration file. The [example program](../examples/logback-example)
already has this set.

```
<configuration debug="true">

    <!-- configuration omitted -->

</configuration>
```


### Example: successful configuration

As with the earlier "succcess" example, I just configured CloudWatch appender. Note that Logback initialization
completes before the first message is logged from the application:

```
10:12:20,214 |-INFO in ch.qos.logback.classic.LoggerContext[default] - Could NOT find resource [logback-test.xml]
10:12:20,214 |-INFO in ch.qos.logback.classic.LoggerContext[default] - Could NOT find resource [logback.groovy]
10:12:20,214 |-INFO in ch.qos.logback.classic.LoggerContext[default] - Found resource [logback.xml] at [jar:file:/tmp/examples/logback-example/target/logback-aws-appenders-example-2.1.0-SNAPSHOT.jar!/logback.xml]
10:12:20,224 |-INFO in ch.qos.logback.core.joran.spi.ConfigurationWatchList@3d71d552 - URL [jar:file:/tmp/examples/logback-example/target/logback-aws-appenders-example-2.1.0-SNAPSHOT.jar!/logback.xml] is not of type file
10:12:20,272 |-INFO in ch.qos.logback.classic.joran.action.JMXConfiguratorAction - begin
10:12:20,321 |-INFO in ch.qos.logback.core.joran.action.AppenderAction - About to instantiate appender of type [ch.qos.logback.core.ConsoleAppender]
10:12:20,323 |-INFO in ch.qos.logback.core.joran.action.AppenderAction - Naming appender as [CONSOLE]
10:12:20,326 |-INFO in ch.qos.logback.core.joran.action.NestedComplexPropertyIA - Assuming default type [ch.qos.logback.classic.encoder.PatternLayoutEncoder] for [encoder] property
10:12:20,343 |-INFO in ch.qos.logback.core.joran.action.AppenderAction - About to instantiate appender of type [com.kdgregory.logback.aws.CloudWatchAppender]
10:12:20,346 |-INFO in ch.qos.logback.core.joran.action.AppenderAction - Naming appender as [CLOUDWATCH]
10:12:20,358 |-INFO in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - log writer starting on thread com-kdgregory-aws-logwriter-logback-cloudwatch-0
10:12:20,359 |-INFO in ch.qos.logback.classic.joran.action.RootLoggerAction - Setting level of ROOT logger to WARN
10:12:20,359 |-INFO in ch.qos.logback.core.joran.action.AppenderRefAction - Attaching appender named [CONSOLE] to Logger[ROOT]
10:12:20,360 |-INFO in ch.qos.logback.classic.joran.action.LoggerAction - Setting level of logger [com.kdgregory.logback.aws.example] to DEBUG
10:12:20,360 |-INFO in ch.qos.logback.core.joran.action.AppenderRefAction - Attaching appender named [CLOUDWATCH] to Logger[com.kdgregory.logback.aws.example]
10:12:20,360 |-INFO in ch.qos.logback.classic.joran.action.ConfigurationAction - End of configuration.
10:12:20,361 |-INFO in ch.qos.logback.classic.joran.JoranConfigurator@30c7da1e - Registering current configuration as safe fallback point
10:12:20,794 |-INFO in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - created client from factory: com.amazonaws.services.logs.AWSLogsClientBuilder.defaultClient
10:12:21,198 |-INFO in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - using existing CloudWatch log group: AppenderExample
10:12:21,223 |-INFO in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - creating CloudWatch log stream: Example-20181226-ithilien-23682
10:12:21,282 |-INFO in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - log writer initialization complete (thread com-kdgregory-aws-logwriter-logback-cloudwatch-0)
10:12:21.366 [example-1] WARN  c.kdgregory.logback.aws.example.Main - value is 85
10:12:21.366 [example-0] DEBUG c.kdgregory.logback.aws.example.Main - value is 60
```


### Example: user does not have permissions

For this example I created a user that did not have any permissions; you'd see the same output if
you have not granted the correct permissions to the EC2 instance profile or Lambda execution role.

```
10:15:02,972 |-INFO in ch.qos.logback.classic.LoggerContext[default] - Could NOT find resource [logback-test.xml]
10:15:02,972 |-INFO in ch.qos.logback.classic.LoggerContext[default] - Could NOT find resource [logback.groovy]
10:15:02,972 |-INFO in ch.qos.logback.classic.LoggerContext[default] - Found resource [logback.xml] at [jar:file:/tmp/examples/logback-example/target/logback-aws-appenders-example-2.1.0-SNAPSHOT.jar!/logback.xml]
10:15:02,982 |-INFO in ch.qos.logback.core.joran.spi.ConfigurationWatchList@3d71d552 - URL [jar:file:/tmp/examples/logback-example/target/logback-aws-appenders-example-2.1.0-SNAPSHOT.jar!/logback.xml] is not of type file
10:15:03,032 |-INFO in ch.qos.logback.classic.joran.action.JMXConfiguratorAction - begin
10:15:03,082 |-INFO in ch.qos.logback.core.joran.action.AppenderAction - About to instantiate appender of type [ch.qos.logback.core.ConsoleAppender]
10:15:03,084 |-INFO in ch.qos.logback.core.joran.action.AppenderAction - Naming appender as [CONSOLE]
10:15:03,087 |-INFO in ch.qos.logback.core.joran.action.NestedComplexPropertyIA - Assuming default type [ch.qos.logback.classic.encoder.PatternLayoutEncoder] for [encoder] property
10:15:03,105 |-INFO in ch.qos.logback.core.joran.action.AppenderAction - About to instantiate appender of type [com.kdgregory.logback.aws.CloudWatchAppender]
10:15:03,108 |-INFO in ch.qos.logback.core.joran.action.AppenderAction - Naming appender as [CLOUDWATCH]
10:15:03,119 |-INFO in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - log writer starting on thread com-kdgregory-aws-logwriter-logback-cloudwatch-0
10:15:03,120 |-INFO in ch.qos.logback.classic.joran.action.RootLoggerAction - Setting level of ROOT logger to WARN
10:15:03,120 |-INFO in ch.qos.logback.core.joran.action.AppenderRefAction - Attaching appender named [CONSOLE] to Logger[ROOT]
10:15:03,121 |-INFO in ch.qos.logback.classic.joran.action.LoggerAction - Setting level of logger [com.kdgregory.logback.aws.example] to DEBUG
10:15:03,121 |-INFO in ch.qos.logback.core.joran.action.AppenderRefAction - Attaching appender named [CLOUDWATCH] to Logger[com.kdgregory.logback.aws.example]
10:15:03,121 |-INFO in ch.qos.logback.classic.joran.action.ConfigurationAction - End of configuration.
10:15:03,122 |-INFO in ch.qos.logback.classic.joran.JoranConfigurator@30c7da1e - Registering current configuration as safe fallback point
10:15:03,552 |-INFO in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - created client from factory: com.amazonaws.services.logs.AWSLogsClientBuilder.defaultClient
10:15:03,806 |-ERROR in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - unable to configure log group/stream com.amazonaws.services.logs.model.AWSLogsException: User: arn:aws:iam::123456789012:user/bogus is not authorized to perform: logs:DescribeLogGroups on resource: arn:aws:logs:us-east-1:123456789012:log-group::log-stream: (Service: AWSLogs; Status Code: 400; Error Code: AccessDeniedException; Request ID: 053990c8-0921-11e9-a644-2b737622361a)
	at com.amazonaws.services.logs.model.AWSLogsException: User: arn:aws:iam::123456789012:user/bogus is not authorized to perform: logs:DescribeLogGroups on resource: arn:aws:logs:us-east-1:123456789012:log-group::log-stream: (Service: AWSLogs; Status Code: 400; Error Code: AccessDeniedException; Request ID: 053990c8-0921-11e9-a644-2b737622361a)
```
