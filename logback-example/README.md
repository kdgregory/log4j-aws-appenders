# Logging Example

This directory contains an example program that writes log messages to all supported loggers. It
also contains CloudFormation templates that will create the destinations for these loggers.

> *BEWARE!* You will be charged for all AWS services used by this example. I have tried to keep
  the costs minimal, and some services (such as CloudWatch) may be covered under a free tier.
  However, *you are responsible for all charges*.

## Creating the AWS resources

Use the [AWS Console](https://console.aws.amazon.com/cloudformation/home) to create stacks from
the templates linked below.

### CloudWatch

[This template](cloudformation/cloudwatch.json) creates the following resources:

* A CloudWatch log group named "AppenderExample"
* An IAM managed policy named "AppenderExampleCloudWatchWriter" that grants access to create
  and write to CloudWatch logs. You would attach this policy to any application that uses the
  CloudWatch appender.
* An IAM managed policy named "AppenderExampleCloudWatchReader" that grants the ability to
  read _only_ the log group created by this stack. You would assign this policy to any user
  that needs to read the logs (in the real world, where you use a different log group for
  each application, you would probably grant access to all log groups).

Things to know:

* CloudWatch Logs charges for the amount of data ingested, and also for long-term storage of
  data. Delete the log group after running the example to avoid unexpected storage charges.

### Kinesis

[This template](cloudformation/kinesis.json) creates the following resources:

* A Kinesis Stream named "AppenderExample", with one shard. For production use with multiple
  applications you will probably need to create multiple shards.
* An ElasticSearch domain named "logging-example" that is the ultimate destination for log
  output. This is a `t2.small.elasticsearch` instance with 16 GB of storage, which is enough
  for many days worth of logging from the example application, but is undersized for any real
  deployment.
* A Kinesis Firehose Delivery Stream named "LoggingFirehose", which reads records from the
  Kinesis logging stream and writes them to the Elastic Search domain.
* A CloudWatch Logs log group named "FirehoseErrors", which is used to record any errors in
  the execution of the Firehose Delivery stream.
* An IAM managed policy named "AppenderExampleKinesisWriter" that grants access to create
  and write to the named Kinesis stream. You would attach this policy to any application that
  uses the Kinesis appender.
* An IAM managed policy named "AppenderExampleKinesisReader" that grants access to describe
  the stream and read records from it. This is used by Kinesis Firehose, but may be applied
  to any application that processes log records.
* An IAM Role named "Logging_Example_Firehose_Role", that allows the Firehose to read the
  stream, write to ElasticSearch, and store any failed records in S3.

Things to know:

* It will take roughly 20 minutes to create the stack.
* To run this stack you will need an existing S3 bucket to hold failed records from the Firehose
  delivery steam. You are prompted to enter the name of this bucket, as well as a prefix for the
  failed record key, at the time of stack creation. To avoid unexpected storage charges, examine
  this bucket and delete any failed records after running the example (there shouldn't be any,
  but verify this).
* Kinesis Streams and ElasticSearch have a per-hour charge. Delete the stack after running
  the example, and verify that all components have been deleted.
* The example ElasticSearch cluster provides open access to the world. This makes it easy to use,
  as you don't need to manage security groups or IAM permissions, but it is inappropriate for
  production use.

### SNS

[This template](cloudformation/sns.json) creates the following resources:

* An SNS topic named "AppenderExample" that is the destination for log output.
* An IAM managed policy named "AppenderExampleSNSWriter" that grants access to create and
  write to the SNS topic. Enable this policy for any application that uses the SNS appender.
* An IAM managed policy named "AppenderExampleSNSSubscriber" that grants access to subscribe
  to the SNS topic. This is not used by the example but is here as a base for your own SNS
  policies.

Things to know:

* The SNS topic is created with an email subscription. You will be able to change the email
  address during stack creation; by default it's [logging-example@mailinator.com](https://www.mailinator.com/v2/inbox.jsp?zone=public&query=logging-example).
  Messages sent to this address are deleted within a few hours, but are publicly available
  to anyone with the link.
* You must explicit confirm this subscription to receive messages. SNS sends a confirmation
  email immediately after the topic is created, and there's a link that you must click to
  confirm the subscription.


## Building and running the example

To build the example program, use Maven:

    mvn clean package

This will produce an executable JAR, which you can run from the command-line (the wildcard
works with Linux; if you're running elsewhere you might need to specify the exact name):

    java -jar target/aws-appenders-example-*.jar

This program will spawn two threads, each of which writes a log message at one-second intervals.
Log levels are randomly assigned: 65% DEBUG, 20% INFO, 10% WARN, and 5% ERROR.

To spawn more threads, give the number of desired threads as a command-line argument. Kill the
program to stop logging.
