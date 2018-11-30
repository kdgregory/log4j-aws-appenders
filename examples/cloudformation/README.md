This directory contains CloudFormation templates to create the logging destinations,
along with their permissions. All of the examples write to the same destinations.

> *WARNING*: these templates create AWS resources for which you will be changed. Be
  sure to delete the stacks when you are done.


## CloudWatch Logs

[This template](cloudwatch.json) creates the following resources:

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


## Kinesis

[This template](kinesis.json) creates the following resources:

* A Kinesis Stream named "AppenderExample", with one shard. For production use with multiple
  applications you will probably need to create multiple shards.
* An IAM managed policy named "AppenderExampleKinesisWriter" that grants access to create
  and write to the named Kinesis stream. You would attach this policy to any application that
  uses the Kinesis appender.
* An IAM managed policy named "AppenderExampleKinesisReader" that grants access to describe
  the stream and read records from it. This is used by Kinesis Firehose, but may be applied
  to any application that processes log records.

Things to know:

* Kinesis Streams have a per-hour charge. To avoid unnecessary charges, delete the stack after
  running the example and verify that all components have been deleted.


## Kinesis Firehose

[This template](kinesis-firehose.json) creates the following resources:

* An ElasticSearch domain named "logging-example" that is the ultimate destination for log
  output. This is a `t2.small.elasticsearch` instance with 16 GB of storage, which is enough
  for many days worth of logging from the example application, but is undersized for any real
  deployment.
* A Kinesis Firehose Delivery Stream named "LoggingFirehose", which reads records from the
  Kinesis logging stream and writes them to the Elastic Search domain.
* A CloudWatch Logs log group named "FirehoseErrors", which is used to record any errors in
  the execution of the Firehose Delivery stream.
* An IAM Role named "Logging_Example_Firehose_Role", that allows the Firehose to read the
  stream, write to ElasticSearch, and store any failed records in S3.


Things to know:

* You must create the Kinesis stack first, as this stack relies on the resources it creates.
* It will take roughly 20 minutes to create the stack.
* To run this stack you will need an existing S3 bucket to hold failed records from the Firehose
  delivery steam. You are prompted to enter the name of this bucket, as well as a prefix for the
  failed record key, at the time of stack creation. To avoid unexpected storage charges, examine
  this bucket and delete any failed records after running the example (there shouldn't be any,
  but verify this).
* Kinesis Streams and ElasticSearch have a per-hour charge. To avoid unnecessary charges, delete
  the stack after running the example and verify that all components have been deleted.
* The example ElasticSearch cluster provides open access to the world. This makes it easy to use,
  as you don't need to manage security groups or IAM permissions, but it is inappropriate for
  production use.


## SNS

[This template](sns.json) creates the following resources:

* An SNS topic named "AppenderExample" that is the destination for log output.
* An IAM managed policy named "AppenderExampleSNSWriter" that grants access to create and
  write to the SNS topic. Enable this policy for any application that uses the SNS appender.
* An IAM managed policy named "AppenderExampleSNSSubscriber" that grants access to subscribe
  to the SNS topic. This is not used by the example but is here as a base for your own SNS
  policies.

Things to know:

* The SNS topic is created with an email subscription. You will be able to change the email
  address during stack creation; by default it's [logging-example@mailinator.com](https://www.mailinator.com/v3/index.jsp?zone=public&query=logging-example#/#inboxpane).
  Messages sent to this address are deleted within a few hours, but are publicly available
  to anyone with the link.
* You must explicit confirm this subscription to receive messages. SNS sends a confirmation
  email immediately after the topic is created, and there's a link that you must click to
  confirm the subscription.
