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

Here are the supported destinations:

* [CloudWatch Logs](docs/cloudwatch.md): basic logging that allows keyword search and time ranges
* [Kinesis Streams](docs/kinesis.md): can be used as a source for Kinesis Firehose, and thence ElasticSearch or S3 storage
* [SNS](docs/sns.md): useful for real-time error notifications

In addition to the appenders, I've added a [JSON layout](docs/jsonlayout.md) to make
it easier to send data to an ElasticSearch/Kibana cluster.


## Usage

To use these appenders, include the `aws-appenders` JAR in your project along with the
relevant AWS JAR(s), and configure the desired appender in your Log4J properties. Each
appender's documentation gives an example configuration, and I have created an [example
project](example) that writes to all of the supported destinations (along with
CloudFormation templates to set up those destinations).


### Dependency Versions

To avoid dependency hell, all dependencies are marked as "provided": you will need
to ensure that your project includes necessary dependencies, as follows:

* `aws-java-sdk-logs` to use `CloudWatchAppender`
* `aws-java-sdk-kinesis` to use `KinesisAppender`
* `aws-java-sdk-sns` to use `SNSAppender`
* `aws-java-sdk-sts` to use the `aws:accountId` substitution variable (typically used with `SNSAppender`)

The minimum supported depedency versions are as follows:

* JDK: 1.6  
  The build script generates 1.6 compatible classfiles, and the appender code
  does not rely on standard libary classes/methods introduced after 1.6. Before
  each release, I have compiled and run the integration tests using OpenJDK 1.6.
  However, Amazon releases the SDK on a daily basis, and a newer version may not
  support 1.6.
* Log4J: 1.2.16  
  This is the first version that implements `LoggingEvent.getTimeStamp()`, which
  is needed to order messages when sending to AWS. It's been around since 2010,
  so if you haven't upgraded already you should.
* AWS SDK: 1.11.0  
  The appenders make use of client constructors in order to support all versions
  in the 1.11.x release sequence. The client builders were introduced at 1.11.16,
  and will be invoked via reflection if available. Earlier appender releases
  could be built using 1.10.x, but this is no longer the case.

I have made an intentional effort to limit dependencies to the bare minimum. This
has in some cases meant that I write internal implementations for functions that
are found in common libraries (including my own).


## Building

There are three projects in this repository:

* `appender` is the actual appender code.
* `tests` is a set of integration tests. These are in a separate module so that they
  can be run as desired, rather than as part of every build. *Beware: these tests
  create resources that incur AWS charges, and does not delete them automatically.*
* `example` is a simple example that writes log message to all supported destinations.
  It includes CloudFormation templates to create those destinations. *Note: you will
  incur AWS charges to run this example.*

Classes in the top-level `com.kdgregory.log4j.aws` package are expected to remain backwards
compatible. Any other classes, particularly those under packages named `internal`, may
change arbitrarily and should not be relied-upon by user code. This caveat also applies
to all test classes and packages.


### Versions

I follow the standard `MAJOR.MINOR.PATCH` versioning scheme:

* `MAJOR` is currently 1, and was intended to track the Log4J version number. In an upcoming
  release it will switch to 11, tracking the AWS SDK minor version number.
* `MINOR` is incremented for each destination (CloudWatch is 0, Kinesis is 1, SNS is 2).
* `PATCH` is incremented for reflect bugfixes or additional features that don't change the
  supported destinations.
  
Not all versions will be released to Maven Central. I may choose to make release (non-snapshot)
versions for development testing, or as interim steps of a bigger piece of functionality. However,
all release versions are tagged in source control, whether or not available on Maven Central.

The source tree also contains commits with major version of 0. These are "pre-release" versions,
and may not reflect the ultimate API. Please do not use them.


### Source Control

The `master` branch is intended for "potentially releasable" versions. Commits on master
are functional, but may not be "complete" (for some definition of that word). They may be
snapshot or release builds. Master will never be rebased; once a commit is made there it's
part of history for better or worse.

Development takes place on a `dev-MAJOR.MINOR.PATCH` branch; these branches are deleted
once their content has been merged into `master`. *BEWARE*: these branches are rebased
as I see fit.

Each "release" version is tagged with `release-MAJOR.MINOR.PATCH`, whether or not it was
uploaded to Maven Central.

Merges into `master` are handled via pull requests, with a squash merge. If you want to see
the individual commits that went into a branch, you can look at the closed PR.


## FAQ

Isn't Log4J 1.x at end of life?

> Yes. Have you updated all of your applications yet? If you have, congratulations.
  I haven't, nor have a lot of people that I know. Replacing a stable logging
  framework is pretty low on the priority list, so I expect 1.x to be around for
  many more years.

If you found other appenders, why are you writing this?

> Reinventing wheels can be a great spur to creativity. It also gives me a deeper
  understanding of the services involved, which is a Good Thing. And of course I've
  added features that I didn't find elsewhere.

What happens when the appender drops messages?

> All misbehaviors get logged using the Log4J internal logger. To see messages from
  this logger, set the system property `log4j.configDebug` to `true` (note that the
  internal logger always writes messages to StdErr).

What are all these messages from `com.amazonaws` and `org.apache.http`?

> You attached the AWS appender to your root logger. There are two solutions: the first
  is to attach the appender only to your program's classes (here I turn off additivity,
  so the messages _won't_ go to the root logger; you might prefer sending messages to
  both destinations):

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
