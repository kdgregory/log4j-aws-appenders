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
