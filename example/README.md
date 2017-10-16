# Logging Example

This directory contains an example program that writes log messages to both CloudWatch and Kinesis.
It also contains a CloudFormation template that will create the destinations for these logs, along
with an ElasticSearch cluster and a Kinesis Firehose delivery stream that will write events to that
cluster.

Use the [AWS Console](https://console.aws.amazon.com/cloudformation/home) to create the example
stack from the [provided template](cloudformation.json).

*Some important caveats*

* It will take roughly 20 minutes to create the stack.
* *You will be charged* for the Kinesis stream, Kinesis Firehose delivery stream, and ElasticSearch
  cluster. You should delete the stack when you are finished exploring the example.
* The example ElasticSearch cluster provides open access to the world. This makes it easy to use,
  as you don't need to manage security groups or IAM permissions, but it is inappropriate for
  production use.

To build the example program, use Maven:

    mvn clean package

This will produce an executable JAR, which you can run from the command-line:

    java -jar target/aws-appenders-example-1.0.0.jar

This program will spawn two threads, each of which writes a log message at one-second intervals. To spawn
more threads, give the number of desired threads as a command-line argument. Kill the program to stop writing.
