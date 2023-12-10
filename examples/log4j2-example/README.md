# Logging Example

This directory contains a simple program that writes log messages at various levels, with
commented-out logging configuration for all destinations.


## Preparation

Start by editing `src/main/resources/log4j2.xml`, uncommenting your desired appenders.

If you enable Kinesis or SNS, you will also need to create the destinations; for CloudWatch
Logs the destination is auto-created. You can create them using the CloudFormation templates
[here](../cloudformation), manually, or by enabling the auto-create feature on the appenders.

> *BEWARE!* You will be charged for all AWS services used by this example. I have tried to keep
  the costs minimal, and some services (such as CloudWatch) may be covered under a free tier.
  However, *you are responsible for all charges*.


## Building and running the example

To build the example program, use Maven:

    mvn clean package

This will produce an executable JAR, which you can run from the command-line (the wildcard
works with Linux; if you're running elsewhere you might need to specify the exact name):

    java -jar target/log4j2-aws-appenders-example-*.jar

This program will spawn two threads, each of which writes a log message at one-second intervals.
Log levels are randomly assigned: 65% DEBUG, 20% INFO, 10% WARN, and 5% ERROR.

To spawn more threads, give the number of desired threads as a command-line argument. Kill the
program to stop logging (note: due to the shutdown timeout, it will keep running for a couple
of seconds after Ctrl-C; use `kill -9` if you want it to stop immediately).

The Kinesis output demonstrates configuration of `JsonLayout` tags, including a system property
with default value. To change from the "dev" environment, define the system property "environment":

    java -Denvironment=prod -jar target/log4j2-aws-appenders-example-*.jar


## Log4j2Plugins.dat

Log4J2 uses simple names to refer to appenders and other plugins. As a result, it needs some way
to identify the actual classes for these plugins. Historically, it used classpath scanning, using
the `packages` element of the configuration file. Now, it wants a `Log4j2Plugins.dat` file.

If your deployment bundle consists of independent JARs, then you're in luck: Log4J asks the
classloader to find all copies of the file, and merges them.

However, if (like this example) you're building an "uberjar", which unpacks all of the dependencies
and repacks them into a single JAR, there's a problem: there can only be one copy of the file in the
final JAR, and the copy that wins will depend on the order the dependencies are processed. If the
version from this library wins, then Log4J won't work at all because it can't find its internal
classes. If the version from `log4j-core` wins, then the appenders from this library won't be found.
See the [troubleshooting doc](../../docs/troubleshooting.md#log4j2-configuration-warningserrors)
for examples of how these problems are reported.

If you're using the Maven Shade plugin to produce the uberjar, then there's an [official
transformer](https://logging.apache.org/log4j/transform/latest/#maven-shade-plugin-extensions)
that will merge those files; the [POM](pom.xml#L129) in this example uses it. Be aware that
this transformer requires at least version 3.3.0 of the plugin; earlier versions fail with the
warning "Cannot load implementation hint".

If you're using Gradle, there are [two transformers](https://plugins.gradle.org/search?term=log4j2)
that come up from a plugin search. I have not used either, so can't give a recommendation.

If you're using some other build tool and there isn't a transformer plugin available, you
can manually create the merged file and check it into your project resources. See
[this Gist](https://gist.github.com/kdgregory/202222cf23a9df085446f0f130da80de) for an 
example. Note that, if you do this, you'll need to ensure that the variants from the
source JARs are not copied into the uberjar.
