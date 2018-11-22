# Logging Example

This directory contains an example program that writes log messages to all supported loggers. It
also contains CloudFormation templates that will create the destinations for these loggers.

> *BEWARE!* You will be charged for all AWS services used by this example. I have tried to keep
  the costs minimal, and some services (such as CloudWatch) may be covered under a free tier.
  However, *you are responsible for all charges*.


## Building and running the example

To build the example program, use Maven:

    mvn clean package

This will produce an executable JAR, which you can run from the command-line (the wildcard
works with Linux; if you're running elsewhere you might need to specify the exact name):

    java -jar target/log4j1-aws-appenders-example-*.jar

This program will spawn two threads, each of which writes a log message at one-second intervals.
Log levels are randomly assigned: 65% DEBUG, 20% INFO, 10% WARN, and 5% ERROR.

To spawn more threads, give the number of desired threads as a command-line argument. Kill the
program to stop logging.
