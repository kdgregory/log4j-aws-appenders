# log4j-aws-appenders

Appenders for Log4J 1.x that write to various AWS services.

This project started because I couldn't find an appender that would write to CloudWatch.
That's not strictly true: I found several for Log4J 2.x, and of course there's the 
appender that AWS provides for Lambda. And then, after I started this project, I found
an appender for 1.x.

But, this seemed to be an easy weekend project, and I'd be able to get exactly what I
wanted if I was willing to reinvent a wheel. After some thought, I expanded the idea:
why not reinvent several wheels, and be able to write to multiple destinations?

Here are the destinations I plan to support. They'll be checked when in development,
and the link will take you to additional documentation.

* [x] [CloudWatch Logs](Docs/cloudwatch.md)
* [ ] Kinesis
* [ ] SNS (I think there it might be interesting to create an "error watcher")
* [ ] S3 (as an alternative to an external "logfile mover")



## Usage

### Dependency Versions

To avoid dependency hell, all dependencies are marked as "provided"; you will need
to ensure that your project includes necessary dependencies. The minimum dependency
versions are:

* Log4J: 1.2.16
* AWS SDK: 1.11.0


## Substitution Variables

Logging destination names (such as a CloudWatch log group or SNS topic) may use substitution variables
from the table below. To use, these must be brace-delimited (eg: or `MyLog-{date}`, _not_ `MyLog-date`)
and may appear in any configuration variable that allows substitutions.


Variable            | Description
--------------------|----------------------------------------------------------------
`date`              | Current UTC date: `YYYYMMDD`
`timestamp`         | Current UTC timestamp: `YYYYMMDDHHMMSS`
`startTimestamp`    | UTC timestamp of JVM startup as returned by `RuntimeMxBean`: `YYYYMMDDHHMMSS`
`pid`               | Process ID (this is parsed from `RuntimeMxBean.getName()` and may not be available on all platforms
`hostname`          | Unqualified hostname (this is parsed from `RuntimeMxBean.getName()` and may not be available on all platforms
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


## Versions

I follow the standard `MAJOR.MINOR.PATCH` versioning scheme:

* `MAJOR` will track the Log4J major version number (eventually I'll release a version for Log4J 2.x)
* `MINOR` will be incremented for each destination, when that destination is minimally available
* `PATCH` will be incremented as support is extended for a destination, as well as for bugfixes
  (bugfixes will be backported to the version that introduced that destination)

The `master` branch is intended to contain released artifacts only (ie, no snapshot builds). It may,
however, contain commits that aren't strictly releases (eg, documentation updates).

I do not plan to upload all releases to Maven Central; just the "final" ones for each destination
(where "final" may include backports). These releases will be tagged with the name `rel-MAJOR.MINOR.PATCH`.


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
