This directory contains CloudFormation templates to create the example logging destinations
and policies to allow their use, along with components for the logging pipeline described
[here](https://www.kdgregory.com/index.php?page=aws.loggingPipeline).

> *WARNING*: these templates create AWS resources for which you will be changed. Be
  sure to delete the stacks when you are done.

All stacks take parameters that let you name the resources that they create. By default,
the logging destinations are named "AppenderExample" (for consistency with the example
programs). Where this name appears below, substitute whatever name you've chosen.


## CloudWatch Logs

[This template](cloudwatch.yml) creates the following resources:

* A CloudWatch log group.
* An IAM managed policy named "CloudWatchLogWriter-AppenderExample", that grants access
  to create that log group, create streams within it, and write log messages. You would
  attach this policy to any application that uses the CloudWatch appender.
* An IAM managed policy named "CloudWatchLogReader-AppenderExample", that grants access
  to read log events from the log group created by this stack (and _only_ that group).
  You would assign this policy to any user that needs to read the logs (in the real world,
  where you would use a different log group for each application, you would probably grant
  access to all log groups).

Things to know:

* CloudWatch Logs charges for the amount of data ingested, and also for long-term storage of
  data. Delete the log group after running the example to avoid unexpected storage charges.


## Kinesis

[This template](kinesis.yml) creates the following resources:

* A Kinesis Stream with one shard. For production use with multiple applications you will
  probably need to create multiple shards.
* An IAM managed policy named "KinesisWriter-AppenderExample", that grants access to create
  and write to the named Kinesis stream. You would attach this policy to any application that
  uses the Kinesis appender.
* An IAM managed policy named "KinesisReader-AppenderExample", that grants access to describe
  the stream and read records from it. You would attach this policy to any application that
  that processes log records.

Things to know:

* Kinesis Streams have a per-hour charge. To avoid unnecessary charges, delete the stack after
  running the example and verify that all components have been deleted.


## SNS

[This template](sns.yml) creates the following resources:

* An SNS topic.
* An IAM managed policy named "SNSWriter-AppenderExample", that grants access to list SNS
  topics and publish to the "AppenderExample" topic. Attach this policy to any application
  that uses the SNS appender.
* An IAM managed policy named "SNSReader-AppenderExample" that grants access to list SNS
  topics. This is not used by the example but is here as a base for your own SNS policies.

Things to know:

* The stack provides a parameter to specify an email address for logging messages. The default
  is [logging-example@mailinator.com](https://www.mailinator.com/v3/index.jsp?zone=public&query=logging-example#/#inboxpane),
  which is a "poste restante" email service: messages sent to this address are deleted within
  a few hours, but are publicly available to anyone with the link.
* You must explicit confirm this subscription to receive messages. SNS sends a confirmation
  email immediately after the topic is created, and there's a link that you must click to
  confirm the subscription.


## Application Role

[This template](application_role.yml) creates an EC2 role and instance profile that reference
the "writer" policies for all of the preceding templates. This can be used as-is to run the
example programs, or as a base for your own application roles.

Things to know:

* You specify the names for the logging destination via parameters, and the template references
  roles using the naming convention established for the destination-creation stacks.
* The template specifies default values for all destinations, which means that the stack will
  fail to create if the corresponding role does not exist.
* Deleting the parameter value will omit that destination from the application role.


## Elasticsearch Cluster

[This template](elasticsearch.yml) creates an Elasticsearch domain to be the endpoint of the
logging pipeline. This cluster allows access from the same AWS account (ie, Kinesis firehose)
as well as from any IP addresses that you specify.

Things to know:

* If you don't specify any IP addresses you will be unable to access Kibana. If you specify
  `0.0.0.0/0` you will grant access to anyone on the Internet; _do not log sensitive data_,
  and recognize that anyone with the cluster endpoint can modify data or delete indexes.
* It will take roughly 15 minutes for the cluster to be ready to accept records.
* The instance class is a `t2.small.elasticsearch`, and it has 16 GB of storage. This is enough
  for many days worth of logging from the example application, but is undersized for any real
  deployment.
* This instance class is covered by the 12-month "new account" free tier (although 6GB of the
  storage isn't). If you're not in the free tier, it will cost $0.86 per day.


## Kinesis Firehose

[This template](firehose.yml) creates the following resources:

* A Kinesis Firehose delivery stream that reads messages from a Kinesis stream and writes
  them to both Elasticsearch and S3.
* A CloudWatch Logs log group named "AppenderExample-FirehoseErrors", along with log
  streams, that is used by the Firehose to report any errors in its operation.
* A role named "AppenderExample-DeliveryRole" that allows the Firehose to operate.

Things to know:

* To create this stack, you must already have the Kinesis Stream, Elasticsearch cluster,
  and S3 bucket.
* You will be prompted to enter the name of the bucket, and also a prefix for storing the
  archived records.
* To avoid unexpected storage charges, delete all files with this prefix after shutting
  down the firehose.
