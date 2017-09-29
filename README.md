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

* [x] [CloudWatch Logs](Docs/cloudwatch.md): basic logging that allows keyword search and time ranges
* [x] [Kinesis Streams](Docs/kinesis.md): can be used as a source for Kinesis Firehose, and thence ElasticSearch or S3 storage
* [ ] SNS: I think it might be useful to create an "error notifier"


## Usage

To use these appenders, include the `aws-appenders` JAR in your project, and configure
the desired appender in your Log4J properties. Each appender's documentation gives an
example configuration.

### Dependency Versions

To avoid dependency hell, all dependencies are marked as "provided": you will need
to ensure that your project includes necessary dependencies. The minimum supported
depedencies are as follows:

* JDK: 1.6  
  The appender code does not rely on standard libary classes/methods introduced
  after 1.6. The AWS SDK, however, might.
* Log4J: 1.2.16  
  This is the first version that implements `LoggingEvent.getTimeStamp()`, which
  is needed to order messages when sending to AWS. It's been around since 2010,
  so if you haven't upgraded already you should.
* AWS SDK: 1.11.0  
  Amazon changed the return type of several functions between 1.10.x and 1.11.x.
  If your project is still using 1.10.x, you can recompile the appenders locally
  with that version; I have built and tested with 1.10.1. Note, however, that the
  integration tests use client-builder classes that weren't introduced until midway
  in the 1.11.x release sequence.

I have made an intentional effort to limit dependencies to the bare minimum. This
has in some cases meant that I write internal implementations for functions that
are found in common libraries (including my own).

Note that tests may introduce their own dependencies. These will all be found on
Maven Central, and will be marked as `test` scope in the POM.

### Substitution Variables

Logging destination names (such as a CloudWatch log group or Kinesis stream) may use substitution
variables from the table below. To use, these must be brace-delimited (eg: `MyLog-{date}`, _not_
`MyLog-date`) and may appear in any configuration variable that allows substitutions.


Variable            | Description
--------------------|----------------------------------------------------------------
`date`              | Current UTC date: `YYYYMMDD`
`timestamp`         | Current UTC timestamp: `YYYYMMDDHHMMSS`
`hourlyTimestamp`   | Current UTC timestamp, with minutes and seconds truncated: `YYYYMMDDHH0000`
`startupTimestamp`  | UTC timestamp of JVM startup as returned by `RuntimeMxBean`: `YYYYMMDDHHMMSS`
`sequence`          | A sequence number that's incremented each time a log is rotated (only useful for loggers that rotate logs)
`pid`               | Process ID (this is parsed from `RuntimeMxBean.getName()` and may not be available on all platforms)
`hostname`          | Unqualified hostname (this is parsed from `RuntimeMxBean.getName()` and may not be available on all platforms)
`instanceId`        | EC2 instance ID. Beware that using this outside of EC2 will introduce a several-minute delay, as the appender tries to retrieve the information
`env:XXX`           | Environment variable `XXX`
`sysprop:XXX`       | System property `XXX`

If unable to replace a substitution variable, the tag will be left in place. This could happen due
to a bogus or unclosed tag, or an unresolvable system property or environment variable.

Note that a particular destination may not accept all of the characters produced by a substitution,
and the logger will remove illegal characters. As a general rule you should limit substitution values
to alphanumeric characters, along with hyphens and underscores.

### Common Configuration

The following configuration parameters are available for all appenders:

Name                | Description
--------------------|----------------------------------------------------------------
`batchDelay`        | The time, in milliseconds, that the writer will wait to accumulate messages for a batch. See below for more information.


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

### Message Batches

Most AWS services allow batching of messages for efficiency. While sending maxmimum-sized requests is
more efficient when there's a high volume of logging, it could excessively delay writing when there's
a low volume (and potentially leave more messages unwritten if the program crashes).

The `batchDelay` timer starts when the first message in a batch is pulled off the internal queue. The
log writer will read additional messages until it either fills the batch or the timer is at zero, at
which point it sends the batch and starts a new one.

This timeout is also used as a "cooldown" timer when the writer is closed (as when the appender rotates
its log stream): the writer will continue to look for messages for this amount of time. Note that the writer
might actually take longer to shut down, if there is a large backlog of messages or communication errors
that prevent the batch being written.

The default value, 2000, is intended as a tradeoff between keeping the log up to date and minimizing the amount
of network traffic generated by the logger.


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

* `MAJOR` will track the Log4J major version number (yes, eventually I'll release a version for Log4J 2.x).
* `MINOR` will be incremented for each destination (CloudWatch, Kinesis, &c).
* `PATCH` will be incremented to reflect bugfixes or additional features; significant bugfixes will be
  backported so that you can continue using the same minor release.
  
Not all versions will be released to Maven Central. I may choose to make release (non-snapshot) versions for
development testing, or as interim steps of a bigger piece of functionality. However, all release versions
are tagged in source control, whether or not available on Maven Central.

The source tree also contains commits with major version of 0. These are "pre-release" versions, and may change
in arbitrary ways. Please do not use them.

## Source Control

The `master` branch is intended for "potentially releasable" versions. Commits on master are functional, but may
not be "complete" (for some definition of that word). They may be "snapshot" or release builds. Master will never
be rebased; once a commit is made there it's part of history for better or worse.

Development takes place on a `dev-MAJOR.MINOR.PATCH` branch; these branches are deleted once their
content has been merged into `master`. *BEWARE*: these branches may be rebased as I see fit.

Each "release" version is tagged with `release-MAJOR.MINOR.PATCH`, whether or not it was uploaded to Maven Central.

Merges into `master` are typically handled via pull requests, and each is squashed into a single commit. If
you want to see the individual commits that went into a branch, you can look at the closed PR.


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

What are all these messages from `com.amazonaws` and `org.apache.http`?

> You attached the `CloudWatchAppender` to your root logger. There are two solutions;
  the first is to attach the appender only to your program's classes (here I turn off
  additivity, so the messages _won't_ go to the root logger; you might prefer sending
  messages to both destinations).

    log4j.logger.com.myprogram=DEBUG, cloudwatch
    log4j.additivity.com.myprogram=false

> Or alternatively, shut off logging for those packages that you don't care about.

    log4j.logger.org.apache.http=ERROR
    log4j.logger.com.amazonaws=ERROR

> My preference is to attach the CloudWatch appender to my application classes, and use
  the built-in `ConsoleAppender` as the root logger. This ensures that you have some
  way to track "meta" issues with the logging configuration.

> Note that seeing unwanted messages is a problem with whatever appender you might use.
  It's more apparent here because the logger invokes code that itself writes log messages.


## Major TODOs / Caveats / Bugs

If you're unable to connect to AWS, or the connection is interrupted, the appenders
will keep writing log messages to the internal queue. Eventually, this will use up
all of your memory. This will be fixed by the 1.1.3 release: you'll be able to.
configure a "max outstanding messages" parameter, and a discard policy.
