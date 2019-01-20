# Logging Example

This directory contains a simple program that writes log messages at various levels. It's
configured to use all appenders, sending all of the messages to CloudWatch Logs and Kinesis,
and error messages to SNS.


## Preparation

You will need to create the Kinesis and SNS destinations; CloudWatch is created automatically.
You can create them using the CloudFormation templates [here](../cloudformation), manually, or
by enabling the auto-create feature on the appenders.

> *BEWARE!* You will be charged for all AWS services used by this example. I have tried to keep
  the costs minimal, and some services (such as CloudWatch) may be covered under a free tier.
  However, *you are responsible for all charges*.


## Building and running the example

To build the example program, use Maven:

    mvn clean package

This will produce an executable JAR, which you can run from the command-line (the wildcard
works with Linux; if you're running elsewhere you might need to specify the exact name):

    java -jar target/logback-aws-appenders-example-*.jar

This program will spawn two threads, each of which writes a log message at one-second intervals.
Log levels are randomly assigned: 65% DEBUG, 20% INFO, 10% WARN, and 5% ERROR.

To spawn more threads, give the number of desired threads as a command-line argument. Kill the
program to stop logging.

The Kinesis output demonstrates configuration of `JsonLayout` tags, including a system property
with default value. To change from the "dev" environment, define the system property "environment":

    java -Denvironment=prod -jar target/logback-aws-appenders-example-*.jar
