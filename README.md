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
`hourlyTimestamp`   | Current UTC timestamp, with minutes and seconds truncated: `YYYYMMDDHH0000`
`startTimestamp`    | UTC timestamp of JVM startup as returned by `RuntimeMxBean`: `YYYYMMDDHHMMSS`
`sequence`          | A sequence number that's incremented each time a log is rotated (only useful for loggers that rotate logs); defaults to 0
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

To meet these constraints, we maintain an internal message queue, and each call to `append()` adds to
this queue. At present this queue is a simple `LinkedList`, and access to the queue is synchronized.
While this presents a point of contention, in normal use it should be minimal: adding an item to a linked
list is very fast.

When the messages on the internal queue reach a configured batch size, or a batch timeout occurs, the
currently-queued messages are passed to a writer thread. This is handled by passing the entire list
and creating a new one in the appender; writers may then use this list however they wish.

The writer thread combines messages into batches, with the batch size dependent on the service. It
attempts to send each batch multiple times, with exponential fallback (note that the service client
has its own retry mechanisms). If unable to send the batch after multiple tries, it is blocked and
the failure is logged using Log4J's internal logger.

The writer thread is lazily started on the first call to `append()`. You can disable actual writes by
setting the `dryRun` configuration parameter. All AWS clients use the default constructor, which
retrieves credentials via the [DefaultAWSCredentialsProviderChain](http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/auth/DefaultAWSCredentialsProviderChain.html).


## Building

There are two projects in this repository:

* `appender` is the actual appender code.
* `tests` is a set of integration tests. These are in a separate module so that they can be run as
  desired, rather than as part of every build.

Classes in the package `com.kdgregory.log4j.aws` are expected to remain backwards compatible. Any
other classes, particularly those in `com.kdgregory.log4j.aws.internal` may change arbitrarily and
should not be relied-upon by user code. This caveat also applies to all test classes/packages.


## Versions

I follow the standard `MAJOR.MINOR.PATCH` versioning scheme:

* `MAJOR` will track the Log4J major version number (yes, eventually I'll release a version for Log4J 2.x)
* `MINOR` will be incremented for each destination; version x.y.0 will be minimally functional
* `PATCH` will be incremented to reflect bugfixes or additional features; significant bugfixes will be backported

I do not plan to upload all releases to Maven Central; just the "final" ones for each destination
(where "final" may include backports). These releases will be tagged with the name `rel-MAJOR.MINOR.PATCH`.

The source tree also contains commits with major version of 0. These are "pre-release" versions, and
may change in arbitrary ways. Please do not use them.


## Source Control

The `master` branch is intended to contain released artifacts only (ie, no snapshot builds). It may,
however, contain commits that aren't strictly releases (eg, documentation updates).

Development takes place on a `dev-MAJOR.MINOR.PATCH` branch; these branches are deleted once their
content has been merged into `master`.

Each minor release has a `support-MAJOR.MINOR` branch for backports and patches. These branches are
expected to live forever.

Each release version is tagged with `release-MAJOR.MINOR.PATCH`.

Merges into `master` are handled via pull requests, and each is squashed into a single commit. If
you really want to see my experiments you can look at closed PRs.


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
