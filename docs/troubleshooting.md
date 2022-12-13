# Enabling Debug Output

This library logs its operation and any errors using the "internal" logger provided by the
logging framework. Each of the supported frameworks does things a little differently. 

## Log4J 1.x

For Log4J, you set the system property `log4j.configDebug` to "true". The easiest way to do
this is when starting your Java application:

```
java -Dlog4j.configDebug=true ...
```

The Log4J internal logger always writes output to standard error; if you're running your
program as a daemon you will need to redirect this output.


### Successful configuration

For this example, I configured the [Log4J1 example program](../examples/log4j1-example)
to use only the CloudWatch Logs appender.

```
log4j: Trying to find [log4j.xml] using context classloader sun.misc.Launcher$AppClassLoader@74a14482.
log4j: Trying to find [log4j.xml] using sun.misc.Launcher$AppClassLoader@74a14482 class loader.
log4j: Trying to find [log4j.xml] using ClassLoader.getSystemResource().
log4j: Trying to find [log4j.properties] using context classloader sun.misc.Launcher$AppClassLoader@74a14482.
log4j: Using URL [jar:file:/home/kgregory/Workspace/log4j-aws-appenders/examples/log4j1-example/target/log4j1-aws-appenders-example-3.1.0-SNAPSHOT.jar!/log4j.properties] for automatic log4j configuration.
log4j: Reading configuration from URL jar:file:/home/kgregory/Workspace/log4j-aws-appenders/examples/log4j1-example/target/log4j1-aws-appenders-example-3.1.0-SNAPSHOT.jar!/log4j.properties
log4j: Parsing for [root] with value=[WARN, console, cloudwatch].
log4j: Level token is [WARN].
log4j: Category root set to WARN
log4j: Parsing appender named "console".
log4j: Parsing layout options for "console".
log4j: Setting property [conversionPattern] to [%d{ISO8601} %-5p [%t] %c - %m%n].
log4j: End of parsing for "console".
log4j: Parsed "console" options.
log4j: Parsing appender named "cloudwatch".
log4j: Parsing layout options for "cloudwatch".
log4j: Setting property [conversionPattern] to [%d{ISO8601} %-5p [%t] %c - %m%n].
log4j: End of parsing for "cloudwatch".
log4j: Setting property [dedicatedWriter] to [true].
log4j: Setting property [logGroup] to [AppenderExample].
log4j: Setting property [logStream] to [Log4J1-Example-{date}-{hostname}-{pid}].
log4j: Parsed "cloudwatch" options.
log4j: Parsing for [com.kdgregory] with value=[DEBUG].
log4j: Level token is [DEBUG].
log4j: Category com.kdgregory set to DEBUG
log4j: Handling log4j.additivity.com.kdgregory=[null]
log4j: Finished configuring.
log4j: CloudWatchAppender(cloudwatch): log writer starting (thread: com-kdgregory-aws-logwriter-log4j-cloudwatch-1)
log4j: CloudWatchAppender(cloudwatch): checking for existence of CloudWatch log group: AppenderExample
log4j: CloudWatchAppender(cloudwatch): using existing CloudWatch log group: AppenderExample
log4j: CloudWatchAppender(cloudwatch): checking for existence of CloudWatch log stream: Log4J1-Example-20221125-ithilien-114271
log4j: CloudWatchAppender(cloudwatch): creating CloudWatch log stream: Log4J1-Example-20221125-ithilien-114271
log4j: CloudWatchAppender(cloudwatch): log writer initialization complete (thread: com-kdgregory-aws-logwriter-log4j-cloudwatch-1)
```

Messages that begin with `CloudWatchAppender(cloudwatch)` are from the log-writer; the others
are from the Log4J framework. With Log4J1, each appender property is configured separately, and
has its own debug output line; since initialization is single-threaded, you'll see all of the
properties for one appender before Log4J configures the next.


## Log4J 2.x

For Log4J 2.x, you set the level of internal logging in the configuration file:

```
<Configuration status="debug" packages="com.kdgregory.log4j2.aws">

    <!-- configuration omitted -->

</Configuration>
```


### Successful configuration

As before, I'm using [my example program](../examples/log4j2-example), with just the
CloudWatch appender enabled:

```
2022-11-25 08:30:06,878 main DEBUG Apache Log4j Core 2.17.2 initializing configuration XmlConfiguration[location=/home/kgregory/Workspace/log4j-aws-appenders/examples/log4j2-example/target/classes/log4j2.xml]
2022-11-25 08:30:06,894 main DEBUG Took 0.008284 seconds to load 4 plugins from package com.kdgregory.log4j2.aws
2022-11-25 08:30:06,894 main DEBUG PluginManager 'Core' found 130 plugins
2022-11-25 08:30:06,894 main DEBUG PluginManager 'Level' found 0 plugins
2022-11-25 08:30:06,897 main DEBUG PluginManager 'Lookup' found 17 plugins
2022-11-25 08:30:06,901 main DEBUG Building Plugin[name=layout, class=org.apache.logging.log4j.core.layout.PatternLayout].
2022-11-25 08:30:06,909 main DEBUG PluginManager 'TypeConverter' found 26 plugins
2022-11-25 08:30:06,918 main DEBUG PatternLayout$Builder(pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} %-5p [%t] %c - %m%n", PatternSelector=null, Configuration(/home/kgregory/Workspace/log4j-aws-appenders/examples/log4j2-example/target/classes/log4j2.xml), Replace=null, charset="null", alwaysWriteExceptions="null", disableAnsi="null", noConsoleNoAnsi="null", header="null", footer="null")
2022-11-25 08:30:06,919 main DEBUG PluginManager 'Converter' found 45 plugins
2022-11-25 08:30:06,926 main DEBUG Building Plugin[name=appender, class=org.apache.logging.log4j.core.appender.ConsoleAppender].
2022-11-25 08:30:06,932 main DEBUG ConsoleAppender$Builder(target="null", follow="null", direct="null", bufferedIo="null", bufferSize="null", immediateFlush="null", ignoreExceptions="null", PatternLayout(%d{yyyy-MM-dd HH:mm:ss.SSS} %-5p [%t] %c - %m%n), name="CONSOLE", Configuration(/home/kgregory/Workspace/log4j-aws-appenders/examples/log4j2-example/target/classes/log4j2.xml), Filter=null, ={})
2022-11-25 08:30:06,934 main DEBUG Starting OutputStreamManager SYSTEM_OUT.false.false
2022-11-25 08:30:06,935 main DEBUG Building Plugin[name=layout, class=org.apache.logging.log4j.core.layout.PatternLayout].
2022-11-25 08:30:06,935 main DEBUG PatternLayout$Builder(pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} %-5p [%t] %c - %m%n", PatternSelector=null, Configuration(/home/kgregory/Workspace/log4j-aws-appenders/examples/log4j2-example/target/classes/log4j2.xml), Replace=null, charset="null", alwaysWriteExceptions="null", disableAnsi="null", noConsoleNoAnsi="null", header="null", footer="null")
2022-11-25 08:30:06,936 main DEBUG Building Plugin[name=appender, class=com.kdgregory.log4j2.aws.CloudWatchAppender].
2022-11-25 08:30:06,940 main DEBUG CloudWatchAppender$CloudWatchAppenderBuilder(name="CLOUDWATCH", logGroup="AppenderExample", logStream="Log4J2-Example-{date}-{hostname}-112927", retentionPeriod="null", dedicatedWriter="true", PatternLayout(%d{yyyy-MM-dd HH:mm:ss.SSS} %-5p [%t] %c - %m%n), Filter=null, Configuration(/home/kgregory/Workspace/log4j-aws-appenders/examples/log4j2-example/target/classes/log4j2.xml), synchronous="null", batchDelay="null", truncateOversizeMessages="null", discardThreshold="null", discardAction="null", assumedRole="null", clientFactory="null", clientRegion="null", clientEndpoint="null", useShutdownHook="null", initializationTimeout="null")
2022-11-25 08:30:06,941 main DEBUG Building Plugin[name=appenders, class=org.apache.logging.log4j.core.config.AppendersPlugin].
2022-11-25 08:30:06,943 main DEBUG createAppenders(={CONSOLE, com.kdgregory.log4j2.aws.CloudWatchAppender with name CloudWatchAppender})
2022-11-25 08:30:06,943 main DEBUG Building Plugin[name=AppenderRef, class=org.apache.logging.log4j.core.config.AppenderRef].
2022-11-25 08:30:06,946 main DEBUG createAppenderRef(ref="CONSOLE", level="null", Filter=null)
2022-11-25 08:30:06,946 main DEBUG Building Plugin[name=root, class=org.apache.logging.log4j.core.config.LoggerConfig$RootLogger].
2022-11-25 08:30:06,948 main DEBUG LoggerConfig$RootLogger$Builder(additivity="null", level="WARN", levelAndRefs="null", includeLocation="null", ={CONSOLE}, ={}, Configuration(/home/kgregory/Workspace/log4j-aws-appenders/examples/log4j2-example/target/classes/log4j2.xml), Filter=null)
2022-11-25 08:30:06,950 main DEBUG Building Plugin[name=AppenderRef, class=org.apache.logging.log4j.core.config.AppenderRef].
2022-11-25 08:30:06,951 main DEBUG createAppenderRef(ref="CLOUDWATCH", level="null", Filter=null)
2022-11-25 08:30:06,951 main DEBUG Building Plugin[name=logger, class=org.apache.logging.log4j.core.config.LoggerConfig].
2022-11-25 08:30:06,953 main DEBUG LoggerConfig$Builder(additivity="null", level="DEBUG", levelAndRefs="null", name="com.kdgregory", includeLocation="null", ={CLOUDWATCH}, ={}, Configuration(/home/kgregory/Workspace/log4j-aws-appenders/examples/log4j2-example/target/classes/log4j2.xml), Filter=null)
2022-11-25 08:30:06,953 main DEBUG Building Plugin[name=loggers, class=org.apache.logging.log4j.core.config.LoggersPlugin].
2022-11-25 08:30:06,954 main DEBUG createLoggers(={root, com.kdgregory})
2022-11-25 08:30:06,955 main DEBUG Configuration XmlConfiguration[location=/home/kgregory/Workspace/log4j-aws-appenders/examples/log4j2-example/target/classes/log4j2.xml] initialized
2022-11-25 08:30:06,955 main DEBUG Starting configuration XmlConfiguration[location=/home/kgregory/Workspace/log4j-aws-appenders/examples/log4j2-example/target/classes/log4j2.xml]
2022-11-25 08:30:06,966 com-kdgregory-aws-logwriter-log4j2-cloudwatch-1 DEBUG log writer starting (thread: com-kdgregory-aws-logwriter-log4j2-cloudwatch-1)
2022-11-25 08:30:06,967 main DEBUG Started configuration XmlConfiguration[location=/home/kgregory/Workspace/log4j-aws-appenders/examples/log4j2-example/target/classes/log4j2.xml] OK.
2022-11-25 08:30:06,967 main DEBUG Shutting down OutputStreamManager SYSTEM_OUT.false.false-1
2022-11-25 08:30:06,968 main DEBUG OutputStream closed
2022-11-25 08:30:06,968 main DEBUG Shut down OutputStreamManager SYSTEM_OUT.false.false-1, all resources released: true
2022-11-25 08:30:06,968 com-kdgregory-aws-logwriter-log4j2-cloudwatch-1 DEBUG checking for existence of CloudWatch log group: AppenderExample
2022-11-25 08:30:06,968 main DEBUG Appender DefaultConsole-1 stopped with status true
2022-11-25 08:30:06,969 main DEBUG Stopped org.apache.logging.log4j.core.config.DefaultConfiguration@5383967b OK
2022-11-25 08:30:07,031 main DEBUG Registering MBean org.apache.logging.log4j2:type=4e0e2f2a
2022-11-25 08:30:07,033 main DEBUG Registering MBean org.apache.logging.log4j2:type=4e0e2f2a,component=StatusLogger
2022-11-25 08:30:07,034 main DEBUG Registering MBean org.apache.logging.log4j2:type=4e0e2f2a,component=ContextSelector
2022-11-25 08:30:07,035 main DEBUG Registering MBean org.apache.logging.log4j2:type=4e0e2f2a,component=Loggers,name=
2022-11-25 08:30:07,036 main DEBUG Registering MBean org.apache.logging.log4j2:type=4e0e2f2a,component=Loggers,name=com.kdgregory
2022-11-25 08:30:07,037 main DEBUG Registering MBean org.apache.logging.log4j2:type=4e0e2f2a,component=Appenders,name=CLOUDWATCH
2022-11-25 08:30:07,038 main DEBUG Registering MBean org.apache.logging.log4j2:type=4e0e2f2a,component=Appenders,name=CONSOLE
2022-11-25 08:30:07,039 main DEBUG org.apache.logging.log4j.core.util.SystemClock does not support precise timestamps.
2022-11-25 08:30:07,040 main DEBUG Reconfiguration complete for context[name=4e0e2f2a] at URI /home/kgregory/Workspace/log4j-aws-appenders/examples/log4j2-example/target/classes/log4j2.xml (org.apache.logging.log4j.core.LoggerContext@1e4eaf58) with optional ClassLoader: null
2022-11-25 08:30:07,040 main DEBUG Shutdown hook enabled. Registering a new one.
2022-11-25 08:30:07,041 main DEBUG LoggerContext[name=4e0e2f2a, org.apache.logging.log4j.core.LoggerContext@1e4eaf58] started OK.
2022-11-25 08:30:07,936 com-kdgregory-aws-logwriter-log4j2-cloudwatch-1 DEBUG creating CloudWatch log group: AppenderExample
2022-11-25 08:30:08,029 com-kdgregory-aws-logwriter-log4j2-cloudwatch-1 DEBUG checking for existence of CloudWatch log stream: Log4J2-Example-20221125-ithilien-112927
2022-11-25 08:30:08,060 com-kdgregory-aws-logwriter-log4j2-cloudwatch-1 DEBUG creating CloudWatch log stream: Log4J2-Example-20221125-ithilien-112927
2022-11-25 08:30:08,138 com-kdgregory-aws-logwriter-log4j2-cloudwatch-1 DEBUG log writer initialization complete (thread: com-kdgregory-aws-logwriter-log4j2-cloudwatch-1)
```

Everything that happens on the `main` thread is within the Log4J framework. Everything on
the `com-kdgregory-aws-logwriter-log4j2-cloudwatch-1` thread is from the log-writer.

Note that Log4J writes the appender configuration, so you can compare it to what the
log-writer reports doing (note also that Log4J has already applied its own lookups,
in this case for the process ID):

```
2022-11-25 08:30:06,940 main DEBUG CloudWatchAppender$CloudWatchAppenderBuilder(name="CLOUDWATCH", logGroup="AppenderExample", logStream="Log4J2-Example-{date}-{hostname}-112927", retentionPeriod="null", dedicatedWriter="true", PatternLayout(%d{yyyy-MM-dd HH:mm:ss.SSS} %-5p [%t] %c - %m%n), Filter=null, Configuration(/home/kgregory/Workspace/log4j-aws-appenders/examples/log4j2-example/target/classes/log4j2.xml), synchronous="null", batchDelay="null", truncateOversizeMessages="null", discardThreshold="null", discardAction="null", assumedRole="null", clientFactory="null", clientRegion="null", clientEndpoint="null", useShutdownHook="null", initializationTimeout="null")
```


## Logback

Logback also enables debug mode in the configuration file:

```
<configuration debug="true">

    <!-- configuration omitted -->

</configuration>
```

### Successful configuration

As with the earlier examples, I configured [the Logback example ](../examples/logback-example)
with just the CloudWatch appender.

```
09:01:42,184 |-INFO in ch.qos.logback.classic.LoggerContext[default] - Could NOT find resource [logback-test.xml]
09:01:42,184 |-INFO in ch.qos.logback.classic.LoggerContext[default] - Could NOT find resource [logback.groovy]
09:01:42,184 |-INFO in ch.qos.logback.classic.LoggerContext[default] - Found resource [logback.xml] at [jar:file:/home/kgregory/Workspace/log4j-aws-appenders/examples/logback-example/target/logback-aws-appenders-example-3.1.0-SNAPSHOT.jar!/logback.xml]
09:01:42,194 |-INFO in ch.qos.logback.core.joran.spi.ConfigurationWatchList@22927a81 - URL [jar:file:/home/kgregory/Workspace/log4j-aws-appenders/examples/logback-example/target/logback-aws-appenders-example-3.1.0-SNAPSHOT.jar!/logback.xml] is not of type file
09:01:42,246 |-INFO in ch.qos.logback.classic.joran.action.JMXConfiguratorAction - begin
09:01:42,349 |-INFO in ch.qos.logback.core.joran.action.AppenderAction - About to instantiate appender of type [ch.qos.logback.core.ConsoleAppender]
09:01:42,351 |-INFO in ch.qos.logback.core.joran.action.AppenderAction - Naming appender as [CONSOLE]
09:01:42,355 |-INFO in ch.qos.logback.core.joran.action.NestedComplexPropertyIA - Assuming default type [ch.qos.logback.classic.encoder.PatternLayoutEncoder] for [encoder] property
09:01:42,371 |-INFO in ch.qos.logback.core.joran.action.AppenderAction - About to instantiate appender of type [com.kdgregory.logback.aws.CloudWatchAppender]
09:01:42,375 |-INFO in ch.qos.logback.core.joran.action.AppenderAction - Naming appender as [CLOUDWATCH]
09:01:42,390 |-INFO in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - log writer starting (thread: com-kdgregory-aws-logwriter-logback-cloudwatch-1)
09:01:42,391 |-INFO in ch.qos.logback.classic.joran.action.RootLoggerAction - Setting level of ROOT logger to WARN
09:01:42,391 |-INFO in ch.qos.logback.core.joran.action.AppenderRefAction - Attaching appender named [CONSOLE] to Logger[ROOT]
09:01:42,391 |-INFO in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - checking for existence of CloudWatch log group: AppenderExample
09:01:42,392 |-INFO in ch.qos.logback.classic.joran.action.LoggerAction - Setting level of logger [com.kdgregory] to DEBUG
09:01:42,392 |-INFO in ch.qos.logback.core.joran.action.AppenderRefAction - Attaching appender named [CLOUDWATCH] to Logger[com.kdgregory]
09:01:42,392 |-INFO in ch.qos.logback.classic.joran.action.ConfigurationAction - End of configuration.
09:01:42,392 |-INFO in ch.qos.logback.classic.joran.JoranConfigurator@51565ec2 - Registering current configuration as safe fallback point
09:01:43,309 |-INFO in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - using existing CloudWatch log group: AppenderExample
09:01:42,310 |-INFO in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - checking for existence of CloudWatch log group: AppenderExample
09:01:43,336 |-INFO in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - creating CloudWatch log stream: Logback-Example-20221125-ithilien-114448
09:01:43,416 |-INFO in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - log writer initialization complete (thread: com-kdgregory-aws-logwriter-logback-cloudwatch-1)
^C09:01:47,608 |-INFO in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - shutdown hook invoked
09:01:47,650 |-INFO in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - log-writer shut down (thread: com-kdgregory-aws-logwriter-logback-cloudwatch-1 (#11)
logback-example, 510> 
```

All of the messages that mention `ch.qos.logback` are from the framework; those that mention
`com.kdgregory.logback.aws` are from the appender or log-writer. Unlike Log4J, Logback does
_not_ show the logger configuration as part of its logging output.


# Common Failures

In this section I focus on the output that identifies the error. Note that I _do not_ cover
all possible error conditions.


## Incorrect IAM permissions

Each appender specifies the permissions that it requires. Some substitutions require additional
permissions. If you don't have the correct permissions, you'll see an exception message that
says "not authorized" and identifies the missing permission:

```
09:12:20,935 |-ERROR in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - exception during initialization com.kdgregory.logging.aws.facade.CloudWatchFacadeException: findLogGroup(AppenderExample,Logback-Example-20221125-ithilien-114837): service exception: User: arn:aws:iam::717623742438:user/example is not authorized to perform: logs:DescribeLogGroups on resource: arn:aws:logs:us-east-1:717623742438:log-group::log-stream: because no identity-based policy allows the logs:DescribeLogGroups action (Service: AWSLogs; Status Code: 400; Error Code: AccessDeniedException; Request ID: 1a133f3c-2432-4752-9d71-a0dcebe91463; Proxy: null)
```

Note that this may not be the only missing permission. For this example I used a user that had
no permissions whatsoever. If I were to grant just `logs:DescribeLogGroups`, then there would
just be another permission error, as the logwriter tried to describe log streams.


### Missing IAM credentials

For this example I removed my access key. You'll see a similar error if you're runing on an EC2
instance or Lambda that doesn't have an instance/execution role. 

```
log4j: CloudWatchAppender(cloudwatch): log writer starting on thread com-kdgregory-aws-logwriter-log4j-cloudwatch-0
log4j:ERROR CloudWatchAppender(cloudwatch): unable to configure log group/stream
com.amazonaws.SdkClientException: Unable to load AWS credentials from any provider in the chain: [EnvironmentVariableCredentialsProvider: Unable to load AWS credentials from environment variables (AWS_ACCESS_KEY_ID (or AWS_ACCESS_KEY) and AWS_SECRET_KEY (or AWS_SECRET_ACCESS_KEY)), SystemPropertiesCredentialsProvider: Unable to load AWS credentials from Java system properties (aws.accessKeyId and aws.secretKey), com.amazonaws.auth.profile.ProfileCredentialsProvider@6093ac52: profile file cannot be null, com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper@68e808e1: Unable to load credentials from service endpoint]
```


### Incorrect credentials

For this example I used my correct AWS access key but an incorrect secret key.

```
log4j: CloudWatchAppender(cloudwatch): log writer starting on thread com-kdgregory-aws-logwriter-log4j-cloudwatch-0
log4j: CloudWatchAppender(cloudwatch): created client from factory: com.amazonaws.services.logs.AWSLogsClientBuilder.defaultClient
log4j:ERROR CloudWatchAppender(cloudwatch): unable to configure log group/stream
com.amazonaws.services.logs.model.AWSLogsException: The request signature we calculated does not match the signature you provided. Check your AWS Secret Access Key and signing method. Consult the service documentation for details.
```


## Unable to connect to AWS

If you deploy into a private subnet without a NAT or VPC endpoints, the log-writer will be
unable to connect to AWS. This can also happen if you have a firewall or security group that
blocks outbound connections to the Internet. 

There's a lot of content in this message, but the key piece is "connect timed out":

```
07:40:24,365 |-ERROR in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - exception during initialization com.kdgregory.logging.aws.facade.CloudWatchFacadeException: findLogGroup(AppenderExample,Logback-Example-20221122-ithilien-58523): unexpected exception: Unable to execute HTTP request: Connect to 54.162.130.159:443 [/54.162.130.159] failed: connect timed out
	at com.kdgregory.logging.aws.facade.CloudWatchFacadeException: findLogGroup(AppenderExample,Logback-Example-20221122-ithilien-58523): unexpected exception: Unable to execute HTTP request: Connect to 54.162.130.159:443 [/54.162.130.159] failed: connect timed out
	at com.kdgregory.logging.aws.facade.v1.CloudWatchFacadeImpl.transformException(CloudWatchFacadeImpl.java:335)
  at com.kdgregory.logging.aws.facade.v1.CloudWatchFacadeImpl.findLogGroup(CloudWatchFacadeImpl.java:76)
```

It normally takes 1-2 minutes from the start of appender initialization until you see this message.


## Unable to connect to custom endpoint

A related exception happens when you configure a client endpoint with nothing listening (eg, you're
testing with localstack, but didn't start the service). This happens almost immeditately after
startup, and the important piece of information is the "Status Code: 404" in the error message.

```
07:58:26,419 |-ERROR in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - exception during initialization com.kdgregory.logging.aws.facade.CloudWatchFacadeException: findLogGroup(AppenderExample,Logback-Example-20221122-ithilien-58927): service exception: null (Service: AWSLogs; Status Code: 404; Error Code: null; Request ID: null; Proxy: null)
	at com.kdgregory.logging.aws.facade.CloudWatchFacadeException: findLogGroup(AppenderExample,Logback-Example-20221122-ithilien-58927): service exception: null (Service: AWSLogs; Status Code: 404; Error Code: null; Request ID: null; Proxy: null)
```


## Contention during initialization

Each of the AWS services imposes quotas on the number of requests per second that it will accept
(you can find them [here](https://docs.aws.amazon.com/general/latest/gr/aws-service-information.html).
Normally, those quotas are high enough to not cause problems, and both the SDK and the logwriters will
retry operations that are throttled. However, there may be some cases where you have enough happening
in your deployment (eg, running large numbers of simultaneous AWS Batch jobs) that the log-writer
can't recover. This normally happens during initialization, and manifests as a `TimeoutException`:


```
08:13:01,287 |-ERROR in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - exception during initialization com.kdgregory.logging.common.util.TimeoutException: describe did not complete by 2022-11-22T13:12:16.957Z (now 2022-11-22T13:13:01.287Z)
	at com.kdgregory.logging.common.util.TimeoutException: describe did not complete by 2022-11-22T13:12:16.957Z (now 2022-11-22T13:13:01.287Z)
	at 	at com.kdgregory.logging.common.util.RetryManager2.invoke(RetryManager2.java:114)
```

Increasing the `initializationTimeout` configuration property should resolve the problem.


## Contention during operation

Throttling can also occur when writing to the destination. The logwriter can almost always
recover from this: it simply requeues the batch and attempts to send again. If it discovers
consistent throttling, it will warn you (enough of these back-to-back and you might lose
messages):

```
10:26:38,942 |-WARN in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - batch failed: repeated throttling
```

If you are in a situation with high contention, the best solution is to increase the 
`batchDelay` configuration parameter. Be aware, however, that increasing this value
increases the potential of losing messages if your application unexpectedly stops.


## Destination not available and auto-create not enabled

The Kinesis and SNS appenders can optionally create their destination (the CloudWatch
appender will always auto-create). If you don't enable this and the destination doesn't
exist, the appender will tell you during initialization:

```
log4j: KinesisAppender(kinesis): log writer starting on thread com-kdgregory-aws-logwriter-log4j-kinesis-0
log4j: KinesisAppender(kinesis): created client from factory: com.amazonaws.services.kinesis.AmazonKinesisClientBuilder.defaultClient
log4j:ERROR KinesisAppender(kinesis): stream "AppenderExample" does not exist and auto-create not enabled
```


## NoClassDefFoundError

This happens if you don't include the correct AWS SDK JARs in your deployment package. It can
also happen if you don't use the correct facade JAR for your SDK.

The stack trace will be different depending on the appender, but all will look similar:

```
java.lang.NoClassDefFoundError: com/amazonaws/services/logs/model/InvalidSequenceTokenException
	at com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterFactory.newLogWriter(CloudWatchWriterFactory.java:34)
	at com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterFactory.newLogWriter(CloudWatchWriterFactory.java:28)
	at com.kdgregory.log4j.aws.internal.AbstractAppender.startWriter(AbstractAppender.java:572)
	at com.kdgregory.log4j.aws.internal.AbstractAppender.initialize(AbstractAppender.java:555)
	at com.kdgregory.log4j.aws.internal.AbstractAppender.append(AbstractAppender.java:458)
	at org.apache.log4j.AppenderSkeleton.doAppend(AppenderSkeleton.java:251)
	at org.apache.log4j.helpers.AppenderAttachableImpl.appendLoopOnAppenders(AppenderAttachableImpl.java:66)
	at org.apache.log4j.Category.callAppenders(Category.java:206)
	at org.apache.log4j.Category.forcedLog(Category.java:391)
	at org.apache.log4j.Category.debug(Category.java:260)
	at com.kdgregory.log4j.aws.example.Main$LogGeneratorRunnable.updateValue(Main.java:119)
	at com.kdgregory.log4j.aws.example.Main$LogGeneratorRunnable.run(Main.java:93)
	at java.lang.Thread.run(Thread.java:748)
Caused by: java.lang.ClassNotFoundException: com.amazonaws.services.logs.model.InvalidSequenceTokenException
	at java.net.URLClassLoader.findClass(URLClassLoader.java:382)
	at java.lang.ClassLoader.loadClass(ClassLoader.java:424)
	at sun.misc.Launcher$AppClassLoader.loadClass(Launcher.java:349)
	at java.lang.ClassLoader.loadClass(ClassLoader.java:357)
```


## "Unable to unmarshall exception response with the unmarshallers provided" error

   You're probably using the AWS v1 SDK on Java 17 or later. This SDK [does not support Java 17
   or later](https://github.com/aws/aws-sdk-java#maintenance-and-support-for-java-versions), so
   you will need to upgrade to the v2 SDK.

   The reason that you might see this in the appenders and not your own code is that the appenders
   library performs some operations with the expectation that AWS will report an error. However,
   [it does affect everyone](https://github.com/aws/aws-sdk-java/issues/2795).

# Batch Logging

After initialization, the log-writers don't normally log their activity unless there's an error.
However, you can configure them to log every batch, by setting the `enableBatchLogging` property
to `true`. When you do this, and turn on the logging framework's debug logging, you'll see
output like this:

```
06:38:08,584 |-INFO in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - log writer initialization complete (thread: com-kdgregory-aws-logwriter-logback-cloudwatch-1)
...
06:38:10,585 |-INFO in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - about to write batch of 6 message(s)
06:38:10,636 |-INFO in com.kdgregory.logback.aws.CloudWatchAppender[CLOUDWATCH] - wrote batch of 6 message(s)
```

In the case of the Kinesis log-writer, this logging also tells you if any messages in a batch
were rejected by the stream (indicating high contention on a particular partition key):

```
06:45:14,548 |-INFO in com.kdgregory.logback.aws.KinesisAppender[KINESIS] - about to write batch of 16 message(s)
06:45:14,659 |-INFO in com.kdgregory.logback.aws.KinesisAppender[KINESIS] - wrote batch of 16 message(s); 0 rejected
```

You should _not_ enable this parameter for normal usage, as you will see these messages every
few seconds for as long as your program runs.
