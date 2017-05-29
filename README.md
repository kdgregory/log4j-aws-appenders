# log4j-aws-appenders

Appenders for Log4J 1.x that write to various AWS services.

This project started because I couldn't find an appender that would write to CloudWatch.
That's not strictly true: I found several for Log4J 2.x, and of course there's the 
appender that AWS provides for Lambda. And then, after I started this project, I found
an appender for 1.x.

But, this seemed to be an easy weekend project, and I'd be able to get exactly what I
wanted if I was willing to reinvent a wheel. After some thought, I expanded the idea:
why not reinvent several wheels, and be able to write to multiple destinations?

Here are the destinations I plan to support; they'll be checked when in development:

  [x] CloudWatch Logs
  [ ] Kinesis
  [ ] SNS (I think there it might be interesting to create an "error watcher")
  [ ] S3 (as an alternative to an external "logfile mover")



## Usage

### Dependency Versions

To avoid dependency hell, all dependencies are marked as "provided"; you will need
to ensure that your project includes necessary dependencies. The minimum dependency
versions are:

* Log4J: 1.2.16
* AWS SDK: 1.11.0



### CloudWatch Logs

The CloudWatch implementation provides (will provide) the following features:

  [x] User-specified log-group and log-stream names
  [x] Substitution variables to customize log-group and log-stream names
  [ ] Rolling log streams
  [ ] Configurable discard in case of network connectivity issues


Your Log4J configuration should look something like this:

		log4j.rootLogger=ERROR, default
		log4j.logger.com.kdgregory.log4j.cloudwatch.TestCloudwatchAppender=DEBUG
		
		log4j.appender.default=com.kdgregory.log4j.cloudwatch.CloudwatchAppender
		log4j.appender.default.layout=org.apache.log4j.PatternLayout
		log4j.appender.default.layout.ConversionPattern=%d [%t] %-5p %c %x - %m%n
		
		log4j.appender.default.logGroup=TestCloudwatchAppender
		log4j.appender.default.logStream=smoketest
		log4j.appender.default.batchSize=1


The appender provides the following properties (also described in the JavaDoc, where you'll
see default values):

Name            | Description
----------------|----------------------------------------------------------------
`logGroup`      | Name of the Cloudwatch log group where messages are sent. If this group doesn't exist it will be created. May use substitutions.
`logStream`     | Name of the Cloudwatch log stream where messages are sent. If not specified, we will construct a timestamp-based stream name. May use substitutions.
`batchSize`     | Maximum number of messages that will be accumulated before sending a batch.
`batchTimeout`  | Maximum time, in milliseconds, that messages will be accumulated. This ensures that low-volume loggers will actually get logged.


## Substitution Variables

Logging destination names (such as a CloudWatch log group or SNS topic) may use substitution variables
from the table below. To use, these must be brace-delimited (eg: or `MyLog-{date}`, _not_ `MyLog-date`)
and may appear in any configuration variable that allows substitutions.


Variable        | Description
----------------|----------------------------------------------------------------
`date`          | Current UTC date: `YYYYMMDD`
`timestamp`     | Current UTC timestamp: `YYYYMMDDHHMMSS` (note that spaces and colons are not allowed in logstream or loggroup names)


## Design

The primary design constraints are these:

* We may receive logging events on any thread, and don't want to block the thread while communicating
  with the service.
* We generally want to batch individual messages to improve throughput, although the service may have
  constraints on either number of messages or bytes in a batch.
* Services may reject our requests, either temporarily or permanently.

To meet these constraints, we maintain an internal message queue, and each call to `append()` adds to
this queue. At present this queue is a simple `LinkedList`, and access to the queue is synchronized.
While this presents a point of contention, in normal use it should be minimal: adding an item to a linked
list is very fast.

When the messages on the internal queue reach a configured batch size, or a batch timeout occurs, the
currently-queued messages are moved to an immutable list, and this list is passed to the writer thread.

The writer thread maintains its own queue of batches, and attempts to send each batch a configured
number of times (with exponential fallback). If unable to send the batch, or if the backlog of unsent
batches exceeds a configured value, the batch will be dropped.

The writer thread is lazily started on the first call to `append()`. You can disable actual writes by
setting the `dryRun` configuration parameter. All AWS clients use the default constructor, which
retrieves credentials via the [DefaultAWSCredentialsProviderChain](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html).


## Building

There are two child projects in this repository:

* `appender` is the actual appender code.
* `tests` is a set of integration tests. These are in a separate module so that they can be run as
  desired, rather than as part of every build.


## FAQ

Isn't Log4J 1.x at end of life?

> Yep. Have you updated all of your applications yet? If you have, congratulations.
  I haven't, nor have a lot of people that I know. Replacing a stable logging
  framework is pretty low on the priority list, even though we don't expect problems.

If you found other appenders, why are you writing this?

> Reinventing wheels can be a great spur to creativity. It also gives me a deeper
  understanding of the services involved, which is a Good Thing.

What happens when the appender drops messages?

> All misbehaviors get logged using the Log4J internal logger. To see messages from
  this logger, set the system property `log4j.configDebug` to `true` (note that the
  internal logger always writes messages to StdErr).
