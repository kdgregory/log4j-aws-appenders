# log4j-aws-appenders

Appenders for Log4J 1.x that write to various AWS services.

This project started because I couldn't find an appender that would write to CloudWatch.
That's not strictly true: I found several for Log4J 2.x, and of course there's the
appender that AWS provides for Lambda. And then, after I started this project, I found
an appender for 1.x.

But, this seemed to be an easy weekend project, and I'd be able to get exactly what I
wanted if I was willing to reinvent a wheel. After some thought, I expanded the idea:
why not reinvent several wheels, and be able to write to multiple destinations? It's
been more than a dozen weekends since I started the project; I keep getting new ideas.

Here are the destinations I plan to support. No idea how many weekends they'll take.

* [x] [CloudWatch Logs](Docs/cloudwatch.md)
* [ ] Kinesis Firehose (to support Elastic Search)
* [ ] SNS (I think there it might be interesting to create an "error watcher")
* [ ] S3 (as an alternative to an external "logfile mover")



## Usage

To use these appenders, include the `aws-appenders` JAR in your project, and configure
the desired appender in your Log4J properties. Each appender's documentation gives an
example configuration.

### Dependency Versions

To avoid dependency hell, all dependencies are marked as "provided": you will need
to ensure that your project includes necessary dependencies. Minimum dependency
versions will depend on which AWS service you use; Amazon introduces new services
and APIs all the time, and does not pay attention to backwards compatibility.

* JDK: 1.6  
  The appender code does not rely on standard libary classes/methods introduced
  after 1.6. The AWS code, however, might.
* Log4J: 1.2.16  
  This is the first version that implements `LoggingEvent.getTimeStamp()`, which
  is needed to order messages when sending to AWS. It's been around since 2010,
  so if you haven't upgraded already you should.
* CloudWatch SDK: 1.11.0  
  This is the first version where `createLogGroup()` and `createLogStream()` return
  a result object. In the 1.10.x branch, these functions returned `void`; you can
  compile the appender for those releases, but it won't run on newer releases.


### Substitution Variables

Logging destination names (such as a CloudWatch log group or SNS topic) may use substitution variables
from the table below. To use, these must be brace-delimited (eg: `MyLog-{date}`, _not_ `MyLog-date`)
and may appear in any configuration variable that allows substitutions.


Variable            | Description
--------------------|----------------------------------------------------------------
`date`              | Current UTC date: `YYYYMMDD`
`timestamp`         | Current UTC timestamp: `YYYYMMDDHHMMSS`
`hourlyTimestamp`   | Current UTC timestamp, with minutes and seconds truncated: `YYYYMMDDHH0000`
`startTimestamp`    | UTC timestamp of JVM startup as returned by `RuntimeMxBean`: `YYYYMMDDHHMMSS`
`sequence`          | A sequence number that's incremented each time a log is rotated (only useful for loggers that rotate logs)
`pid`               | Process ID (this is parsed from `RuntimeMxBean.getName()` and may not be available on all platforms)
`hostname`          | Unqualified hostname (this is parsed from `RuntimeMxBean.getName()` and may not be available on all platforms)
`instanceId`        | EC2 instance ID. Beware that using this outside of EC2 will introduce a several-minute delay, as the appender tries to retrieve the information
`env:XXX`           | Environment variable `XXX`
`sysprop:XXX`       | System property `XXX`

If unable to replace a substitution variable, the tag will be left in place. This could happen due
to a bogus or unclosed tag, or an unresolvable system property or environment variable.

Note that a particular destination may not accept all of the characters produced by a substitution,
and the logger will remove illegal characters. You should try to limit substitution values to
alphanumeric characters, along with hyphens and underscores.


## Design

The primary design constraints are these:

* We may receive logging events on any thread, and don't want to block the thread while communicating
  with the service.
* We generally want to batch individual messages to improve throughput, although the service may have
  constraints on either number of messages or bytes in a batch.
* Services may reject our requests, either temporarily or permanently.

To meet these constraints, the appender spins up a separate thread for communication with the service,
with a concurrent queue between appender and writer. When Log4J calls `append()`, the appender converts
the passed `LoggingEvent` into a textual representation, verifies that it conforms to limitations imposed
by the service, and adds it to the queue.

The writer consumes that queue, attempting to batch together messages into a single request. Once it
has a batch (either based on size or a configurable timeout) it attempts to write those messages to
the service. In addition to retries embedded within the AWS SDK, the writer makes three attempts to
write the batch; if unable to do so it drops the batch and logs a message on Log4J's internal logger.

The writer thread is lazily started on the first call to `append()`. There's a factory for writer
objects and writer threads, to support testing (_not_ to be enterprisey!). If unable to start the
writer thread, messages are dropped and the situation is reported to the internal logger.

Unexpected exceptions within the writer thread are reported using an uncaught exception handler in
the appender. This will trigger the appender to discard the writer and create a new one (and report
the failure to the internal logger).

The writer uses the default constructor for each service, which in turn uses the default credential
provider chain. This allows you to specify explicit credentials using several mechanisms, or to use
instance roles for applications running on EC2 or Lambda.


## Building

There are two projects in this repository:

* `appender` is the actual appender code.
* `tests` is a set of integration tests. These are in a separate module so that they can be run as
  desired, rather than as part of every build.

Classes in the `com.kdgregory.log4j.aws` package are expected to remain backwards compatible. Any
other classes, particularly those in the `com.kdgregory.log4j.aws.internal` package, may change
arbitrarily and should not be relied-upon by user code. This caveat also applies to all test
classes and packages.


## Versions

I follow the standard `MAJOR.MINOR.PATCH` versioning scheme:

* `MAJOR` will track the Log4J major version number (yes, eventually I'll release a version for Log4J 2.x)
* `MINOR` will be incremented for each destination (CloudWatch, Kinesis, &c)
* `PATCH` will be incremented to reflect bugfixes or additional features; significant bugfixes will be backported

Not all versions will be released to Maven Central. I may choose to make release (non-snapshot) versions for
development testing, or as interim steps of a bigger piece of functionality. Versions that _are_ released to
Maven Central will be tagged in source control.

The source tree also contains commits with major version of 0. These are "pre-release" versions, and may change
in arbitrary ways. Please do not use them.


## Source Control

The `master` branch holds the current branch of development. Commits on master are functional, but may
not be "complete" (whatever that means). They may be "snapshot" or release builds.

Development takes place on a `dev-MAJOR.MINOR.PATCH` branch; these branches are deleted once their
content has been merged into `master`.

Each minor release has a `support-MAJOR.MINOR` branch for backports and patches. These branches are
expected to live forever.

Each version released to Maven Central is tagged with `release-MAJOR.MINOR.PATCH`.

Merges into `master` are typically handled via pull requests, and each is squashed into a single commit. If
you really want to see my development process you can look at closed PRs.


## FAQ

Isn't Log4J 1.x at end of life?

> Yep. Have you updated all of your applications yet? If you have, congratulations.
  I haven't, nor have a lot of people that I know. Replacing a stable logging
  framework is pretty low on the priority list.

If you found other appenders, why are you writing this?

> Reinventing wheels can be a great spur to creativity. It also gives me a deeper
  understanding of the services involved, which is a Good Thing. And of course I've
  added features that I didn't find elsewhere.

What happens when the appender drops messages?

> All misbehaviors get logged using the Log4J internal logger. To see messages from
  this logger, set the system property `log4j.configDebug` to `true` (note that the
  internal logger always writes messages to StdErr).
