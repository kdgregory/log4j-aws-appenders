# When Things Go Wrong

If you don't see logging output, it is almost always due to incorrect IAM permissions. The documentation
for each appender lists the permissions that you will need to use that appender. It's also possible, when
using the Kinesis and SNS appenders, that the destination doesn't exist (or is in another region).

Fortunately, the logging frameworks support debug output, and the appenders report both successful and
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
output, indicating that it was able to configure the appender.

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

However, successfully initializing the appender _does not_ mean that it is able to write
to the destination; you may still see an error when the log-writer attempts to write its
first batch.

The following output shows sucessful log-writer operation. Note that this output is
interleaved with the log messages sent to the console: that is an artifact of the
appender building a batch of messages.

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


## Log4J 2.x

For Log4J 2.x, you can set the level of internal logging in the configuration file.

```
<Configuration status="debug" packages="com.kdgregory.log4j2.aws">

    <!-- configuration omitted -->

</Configuration>
```


### Example: successful configuration

Unlike Log4J 1.x, Log4J 2.x starts the log-writer at the time of initialization. This
example shows successful initialization along with the first batch of messages.

```
2020-03-21 16:05:01,303 main DEBUG Initializing configuration XmlConfiguration[location=jar:file:/tmp/log4j2-example/target/log4j2-aws-appenders-example-2.3.0-SNAPSHOT.jar!/log4j2.xml]
2020-03-21 16:05:01,306 main DEBUG Installed 1 script engine
2020-03-21 16:05:01,542 main DEBUG Oracle Nashorn version: 1.8.0_192, language: ECMAScript, threading: Not Thread Safe, compile: true, names: [nashorn, Nashorn, js, JS, JavaScript, javascript, ECMAScript, ecmascript], factory class: jdk.nashorn.api.scripting.NashornScriptEngineFactory
2020-03-21 16:05:01,721 main DEBUG Took 0.177783 seconds to load 4 plugins from package com.kdgregory.log4j2.aws
2020-03-21 16:05:01,721 main DEBUG PluginManager 'Core' found 119 plugins
2020-03-21 16:05:01,721 main DEBUG PluginManager 'Level' found 0 plugins
2020-03-21 16:05:01,723 main DEBUG PluginManager 'Lookup' found 14 plugins
2020-03-21 16:05:01,724 main DEBUG Building Plugin[name=layout, class=org.apache.logging.log4j.core.layout.PatternLayout].
2020-03-21 16:05:01,727 main DEBUG PluginManager 'TypeConverter' found 26 plugins
2020-03-21 16:05:01,734 main DEBUG PatternLayout$Builder(pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] - %c %p - %m%n", PatternSelector=null, Configuration(jar:file:/tmp/log4j2-example/target/log4j2-aws-appenders-example-2.3.0-SNAPSHOT.jar!/log4j2.xml), Replace=null, charset="null", alwaysWriteExceptions="null", disableAnsi="null", noConsoleNoAnsi="null", header="null", footer="null")
2020-03-21 16:05:01,734 main DEBUG PluginManager 'Converter' found 42 plugins
2020-03-21 16:05:01,737 main DEBUG Building Plugin[name=appender, class=org.apache.logging.log4j.core.appender.ConsoleAppender].
2020-03-21 16:05:01,739 main DEBUG ConsoleAppender$Builder(target="null", follow="null", direct="null", bufferedIo="null", bufferSize="null", immediateFlush="null", ignoreExceptions="null", PatternLayout(%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] - %c %p - %m%n), name="CONSOLE", Configuration(jar:file:/tmp/log4j2-example/target/log4j2-aws-appenders-example-2.3.0-SNAPSHOT.jar!/log4j2.xml), Filter=null)
2020-03-21 16:05:01,740 main DEBUG Starting OutputStreamManager SYSTEM_OUT.false.false
2020-03-21 16:05:01,740 main DEBUG Building Plugin[name=layout, class=org.apache.logging.log4j.core.layout.PatternLayout].
2020-03-21 16:05:01,741 main DEBUG PatternLayout$Builder(pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] - %c %p - %m", PatternSelector=null, Configuration(jar:file:/tmp/log4j2-example/target/log4j2-aws-appenders-example-2.3.0-SNAPSHOT.jar!/log4j2.xml), Replace=null, charset="null", alwaysWriteExceptions="null", disableAnsi="null", noConsoleNoAnsi="null", header="null", footer="null")
2020-03-21 16:05:01,741 main DEBUG Building Plugin[name=appender, class=com.kdgregory.log4j2.aws.CloudWatchAppender].
2020-03-21 16:05:01,744 main DEBUG CloudWatchAppender$CloudWatchAppenderBuilder(name="CLOUDWATCH", logGroup="AppenderExample", logStream="Example-{date}-{hostname}-{pid}", retentionPeriod="null", dedicatedWriter="true", rotationMode="null", rotationInterval="null", sequence="null", PatternLayout(%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] - %c %p - %m), Filter=null, Configuration(jar:file:/tmp/log4j2-example/target/log4j2-aws-appenders-example-2.3.0-SNAPSHOT.jar!/log4j2.xml), synchronous="null", batchDelay="null", discardThreshold="null", discardAction="null", clientFactory="null", clientRegion="null", clientEndpoint="null", useShutdownHook="null")
2020-03-21 16:05:01,746 main DEBUG Building Plugin[name=appenders, class=org.apache.logging.log4j.core.config.AppendersPlugin].
2020-03-21 16:05:01,746 main DEBUG createAppenders(={CONSOLE, com.kdgregory.log4j2.aws.CloudWatchAppender with name CloudWatchAppender})
2020-03-21 16:05:01,747 main DEBUG Building Plugin[name=AppenderRef, class=org.apache.logging.log4j.core.config.AppenderRef].
2020-03-21 16:05:01,749 main DEBUG createAppenderRef(ref="CONSOLE", level="null", Filter=null)
2020-03-21 16:05:01,749 main DEBUG Building Plugin[name=root, class=org.apache.logging.log4j.core.config.LoggerConfig$RootLogger].
2020-03-21 16:05:01,749 main DEBUG createLogger(additivity="null", level="WARN", includeLocation="null", ={CONSOLE}, ={}, Configuration(jar:file:/tmp/log4j2-example/target/log4j2-aws-appenders-example-2.3.0-SNAPSHOT.jar!/log4j2.xml), Filter=null)
2020-03-21 16:05:01,750 main DEBUG Building Plugin[name=AppenderRef, class=org.apache.logging.log4j.core.config.AppenderRef].
2020-03-21 16:05:01,750 main DEBUG createAppenderRef(ref="CLOUDWATCH", level="null", Filter=null)
2020-03-21 16:05:01,751 main DEBUG Building Plugin[name=logger, class=org.apache.logging.log4j.core.config.LoggerConfig].
2020-03-21 16:05:01,752 main DEBUG createLogger(additivity="true", level="DEBUG", name="com.kdgregory", includeLocation="null", ={CLOUDWATCH}, ={}, Configuration(jar:file:/tmp/log4j2-example/target/log4j2-aws-appenders-example-2.3.0-SNAPSHOT.jar!/log4j2.xml), Filter=null)
2020-03-21 16:05:01,752 main DEBUG Building Plugin[name=loggers, class=org.apache.logging.log4j.core.config.LoggersPlugin].
2020-03-21 16:05:01,753 main DEBUG createLoggers(={root, com.kdgregory})
2020-03-21 16:05:01,753 main DEBUG Configuration XmlConfiguration[location=jar:file:/tmp/log4j2-example/target/log4j2-aws-appenders-example-2.3.0-SNAPSHOT.jar!/log4j2.xml] initialized
2020-03-21 16:05:01,753 main DEBUG Starting configuration XmlConfiguration[location=jar:file:/tmp/log4j2-example/target/log4j2-aws-appenders-example-2.3.0-SNAPSHOT.jar!/log4j2.xml]
2020-03-21 16:05:01,758 com-kdgregory-aws-logwriter-log4j2-cloudwatch-1 DEBUG log writer starting (thread: com-kdgregory-aws-logwriter-log4j2-cloudwatch-1)
2020-03-21 16:05:01,770 com-kdgregory-aws-logwriter-log4j2-cloudwatch-1 DEBUG creating client via SDK builder
2020-03-21 16:05:02,503 com-kdgregory-aws-logwriter-log4j2-cloudwatch-1 DEBUG creating CloudWatch log group: AppenderExample
2020-03-21 16:05:02,611 com-kdgregory-aws-logwriter-log4j2-cloudwatch-1 DEBUG creating CloudWatch log stream: Example-20200321-ithilien-3397
2020-03-21 16:05:02,673 com-kdgregory-aws-logwriter-log4j2-cloudwatch-1 DEBUG log writer initialization complete (thread: com-kdgregory-aws-logwriter-log4j2-cloudwatch-1)
2020-03-21 16:05:02,766 main DEBUG Started configuration XmlConfiguration[location=jar:file:/tmp/log4j2-example/target/log4j2-aws-appenders-example-2.3.0-SNAPSHOT.jar!/log4j2.xml] OK.
```


### Example: missing credentials

In this example, the initial configuration looks like the previous example, but there's
an exception thrown when the log writer starts executing.

```
2020-03-21 16:09:42,907 com-kdgregory-aws-logwriter-log4j2-cloudwatch-1 DEBUG log writer starting (thread: com-kdgregory-aws-logwriter-log4j2-cloudwatch-1)
2020-03-21 16:09:42,920 com-kdgregory-aws-logwriter-log4j2-cloudwatch-1 DEBUG creating client via SDK builder
2020-03-21 16:09:45,409 com-kdgregory-aws-logwriter-log4j2-cloudwatch-1 ERROR unable to configure log group/stream com.amazonaws.SdkClientException: Unable to load AWS credentials from any provider in the chain: [EnvironmentVariableCredentialsProvider: Unable to load AWS credentials from environment variables (AWS_ACCESS_KEY_ID (or AWS_ACCESS_KEY) and AWS_SECRET_KEY (or AWS_SECRET_ACCESS_KEY)), SystemPropertiesCredentialsProvider: Unable to load AWS credentials from Java system properties (aws.accessKeyId and aws.secretKey), com.amazonaws.auth.profile.ProfileCredentialsProvider@589830a6: profile file cannot be null, com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper@4224d099: Unable to load credentials from service endpoint]
```


## Logback

For Logback, you enable debug mode in the configuration file.

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


## Missing SDK JARs on Classpath

This error is the same for all of the appenders, although its specifics may differ depending on the
appender type. Here's an example of using the CloudWatch appender without the `aws-java-sdk-logs` JAR:

```
Exception in thread "example-0" 2020-05-30 09:05:58,526 DEBUG [example-1] com.kdgregory.log4j.aws.example.Main - value is 52
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

The key to this errors is the `ClassNotFoundException`, referencing a class in the SDK JAR (in this
case, `InvalidSequenceTokenException`). Another clue is that it's thrown by the writer factory, when
calling `newLogWriter()`.

What's happening behind the scenes is that the log writer has hard references to objects within the
SDK. When the JVM loads the log-writer class, it also tries to resolve the classes it references.
If the SDK JAR isn't on the classpath, those references will fail.
