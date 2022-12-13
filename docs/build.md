# Building It Yourself

There are multiple projects in this repository:

* [library](../library): the actual library, consisting of framework-specific appenders,
  the log-writer implementations and other shared code, and facades to support different
  AWS SDKs.
* [examples](../examples): example programs that demonstrate the appenders in action.
* [integration-tests](../integration-tests): integration tests that execute several
  scenarios against each set of appenders, using actual AWS resources.

All projects are built with [Apache Maven](http://maven.apache.org/). The build commands
differ depending on project:

* library: `mvn clean install` from the project root directory.
* examples: `mvn clean package` from the root directory of a specific example (you can
  also run from the root of the examples directory, to build all examples).
* integration tests: `mvn clean test` from the integration test root directory. Note
  that the full suite of integration tests takes over half an hour to run.


## Source Control

The `trunk` branch is intended for "potentially releasable" versions. Commits on trunk are
functional, but may not be "complete" (for some definition of that word). They may be snapshot
or release builds. Trunk will never be rebased: once a commit is made there it's part of history
for better or worse.

All development takes place on a branch. Branches are either feature branches, in which case
they're named after an issue (eg: `dev-issue-21`), or release-prep branches, in which case
they're named `dev-MAJOR.MINOR.PATCH`. Development branches may be rebased as I see fit:
I often make "checkpoint" commits to save my work and then rebase them into a single commit.
Once a development branch is merged it is deleted.

Features are merged into either a release-prep branch or trunk, using a pull request and
squash merge. If you want to see the individual commits that went into a branch, you can
look at the closed PR.

Each released version is tagged with `release-MAJOR.MINOR.PATCH`.


## Parent versus Driver POMs

Each of the builds listed above -- appenders, integration tests, and examples -- use a "driver"
POM that triggers the sub-project builds. This is not a "parent" POM: it is not referenced by
the sub-projects, and does not contain any project-wide definitions. The primary goal of these
driver POMs is to be able to build projects that require the same set of build commands (eg,
"install" for the libraries, versus "package" for the examples).

There is, however, an actual parent POM in `library/parent`. This provides plugin configuration
and version properties that are used by both the library and integration tests (the examples are
intended to stand alone).


## Dependency Versions

The core library and integration tests should be built using the dependency versions specified
by `library/parent/pom.xml`. These represent the minimum versions supported by the library.

Examples should be built using a "recentish" version of the AWS SDK (recognizing that it's
updated every day), and the most recent versions of all other dependencies.


## Automated Tests

Integration tests are the "gold standard" tests for this library: if they pass, then it's good.
However, there are some situations that can't be easily tested using integration tests, such as
throttling or exceptions in the AWS SDK. For that, I make extensive use of mock objects.

Each "layer" of the library -- facade, logwriter, and appender -- mocks out the layer below it.
The assumption is that each layer has a well-defined interface, and will handle any problems in
a way consistent with that interface.

One recurring problem with the automated tests is that many of them are timing-dependent. This
means that a test that consistently passes on my i7 development machine might fail when run on
a less capable platform (such as an EC2 instance). I have tried to avoid timing-based tests if
possible: for example, the logwriter tests use semaphores to control interaction between the
main (test) thread and the writer thread. And tests that assert on elapsed time generally use
ranges. However, it's possible that tests will fail due to timing; in that case, I rerun and
if there's a consistent failure I try to work-around it. The library doesn't get release unless
all tests (unit and integration) run to completion at least once.

The library builds are configured to use the Jacoco code coverage tool. However, I do not run
it as part of every build: its on-the-fly instrumentation was causing some timing-dependent
tests to fail. Instead, I run manually with the following command:

```
mvn jacoco:prepare-agent test site
```


### Structure of integration test directories

**BEWARE:** the integration tests incur AWS charges. These charges are small (under $1 per run),
but test failures may leave resources that continue to incur charges. _If you choose to run
the integration tests, you accept all charges that result._

Because both the target logging frameworks and the AWS SDKs are almost but not completely like
each other, the integration tests risk becoming a maintenance nightmare. To avoid that, I've
split them into the following directories:

* `helpers`

  Contains utility classes that can be used to prepare the environment and retrieve data to
  confirm test operation. There's one set of classes per AWS SDK.

* `logwriter-v1`, `logwriter-v2`

  Tests that directly exercise the logwriters. There's one set for each AWS SDK, containing
  identical testcases.

  One goal for the logwriter tests is to ensure that there aren't "accidental" dependencies.
  To that end, the "core" logwriter tests are split into one project per service, and only
  reference the AWS library for that service. There's also an "extended" project, which tests
  features that require optional libraries (such as retrieving SSM parameters).

* `appenders`

  Tests that exercise the entire stack. There's one set for each logging framework, using the
  v1 SDK. To minimize duplication, there's also a module of "abstract" tests that contain the
  core logic of the tests; the framework-specific modules subclass these abstract tests and
  perform any framework-specific configuration.


### AWS permissions needed for integration tests

While the individual appender docs list the permissions needed to use those appenders, the
integration tests require additional permissions: they have to create, examine, and delete
resources outside of what the log-writers themselves do. While I normally run using
"Administrator" permissions, I also run a pre-release test on EC2 using an instance role
with the following policy.

**Do not use this policy in production.** It is extremely over-powered, and includes
permissions that are not needed for normal use of an appender.

```
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "AssumedRole",
            "Effect": "Allow",
            "Action": [
                "iam:CreateRole",
                "iam:DeleteRole",
                "iam:ListAttachedRolePolicies",
                "iam:AttachRolePolicy",
                "iam:DetachRolePolicy",
                "iam:ListRoles",
                "sts:AssumeRole"
            ],
            "Resource": "*"
        },
        {
            "Sid": "CloudWatchLogs",
            "Effect": "Allow",
            "Action": [
                "logs:CreateLogGroup",
                "logs:CreateLogStream",
                "logs:DeleteLogGroup",
                "logs:DeleteLogStream",
                "logs:DescribeLogGroups",
                "logs:DescribeLogStreams",
                "logs:GetLogEvents",
                "logs:PutLogEvents",
                "logs:PutRetentionPolicy"
            ],
            "Resource": "*"
        },
        {
            "Sid": "EC2Tags",
            "Effect": "Allow",
            "Action": [
                "ec2:CreateTags",
                "ec2:DescribeTags"
            ],
            "Resource": "*"
        },
        {
            "Sid": "Kinesis",
            "Effect": "Allow",
            "Action": [
                "kinesis:CreateStream",
                "kinesis:DeleteStream",
                "kinesis:DescribeStream",
                "kinesis:DescribeStreamSummary",
                "kinesis:GetRecords",
                "kinesis:GetShardIterator",
                "kinesis:IncreaseStreamRetentionPeriod",
                "kinesis:ListStreams",
                "kinesis:PutRecords"
            ],
            "Resource": "*"
        },
        {
            "Sid": "SNS",
            "Effect": "Allow",
            "Action": [
                "sns:CreateTopic",
                "sns:DeleteTopic",
                "sns:GetTopicAttributes",
                "sns:ListSubscriptions",
                "sns:ListTopics",
                "sns:Publish",
                "sns:Subscribe",
                "sns:Unsubscribe",
                "sqs:AddPermission",
                "sqs:CreateQueue",
                "sqs:DeleteMessage",
                "sqs:DeleteQueue",
                "sqs:GetQueueAttributes",
                "sqs:GetQueueUrl",
                "sqs:ListQueues",
                "sqs:ReceiveMessage",
                "sqs:SendMessage",
                "sqs:SetQueueAttributes"
            ],
            "Resource": "*"
        },
        {
            "Sid": "ParameterStore",
            "Effect": "Allow",
            "Action": [
                "kms:Encrypt",
                "ssm:DeleteParameter",
                "ssm:GetParameter",
                "ssm:PutParameter"
            ],
            "Resource": "*"
        }
    ]
}
```


### Cleaning up after integration tests

The integration tests create live AWS resources. And while each test tries to delete the
resources it creates on success, test failures leave these resources undeleted (this is
intentional, to support post-mortem debugging).

If you run the integration tests, I recommend running the following script afterward to
identify any undeleted resources (note: if you have resources with "IntegrationTest" in
their names it will show them as well, so beware false positives!):


```
#!/bin/bash

echo "CloudWatch Logs"
for r in 'us-east-1' 'us-east-2' 'us-west-1' 'us-west-2' ; \
    do for g in $(aws logs describe-log-groups --region $r --query 'logGroups[].logGroupName' | grep IntegrationTest | sed -e 's/[ ",]*//g') ; \
        do echo $r " - " $g ; \
    done ; \
done

echo "Kinesis"
for r in 'us-east-1' 'us-east-2' 'us-west-1' 'us-west-2' ; \
    do for s in $(aws kinesis list-streams --region $r --query 'StreamNames[]' | grep IntegrationTest | sed -e 's/[ ",]*//g') ; \
        do echo $r " - " $s ; \
    done ; \
done

echo "SNS Subscriptions"
for r in 'us-east-1' 'us-east-2' 'us-west-1' 'us-west-2' ; \
    do for s in $(aws sns list-subscriptions --region $r --query 'Subscriptions[].SubscriptionArn' | grep IntegrationTest | sed -e 's/[ ",]*//g') ; \
        do echo $r " - " $s ; \
    done ; \
done

echo "SNS Topics"
for r in 'us-east-1' 'us-east-2' 'us-west-1' 'us-west-2' ; \
    do for t in $(aws sns list-topics --region $r --query 'Topics[].TopicArn' | grep IntegrationTest | sed -e 's/[ ",]*//g') ; \
        do echo $r " - " $t ; \
    done ; \
done

echo "SQS Queues"
for r in 'us-east-1' 'us-east-2' 'us-west-1' 'us-west-2' ; \
    do for q in $(aws sqs list-queues --region $r --query 'QueueUrls[]' | grep IntegrationTest | sed -e 's/[ ",]*//g') ; \
        do echo $r " - " $q ; \
    done ; \
done
```

You can delete these resources with the following CLI commands (replacing the capitalized names with
the output from the script above):

* CloudWatch Logs

  `aws logs delete-log-group --region REGION --log-group-name LOG_GROUP_NAME`

* Kinesis

  `aws kinesis delete-stream --region REGION --stream-name STREAM_NAME`

* SNS Subscriptions

  `aws sns unsubscribe --region REGION --subscription-arn SUBSCRIPTION_ARN`

* SNS Topics

  `aws sns delete-topic --region REGION --topic-arn TOPIC_ARN`

* SQS Queues

  `aws sqs delete-queue --region REGION --queue-url QUEUE_URL`


## Known Problems

### Building on Java 14+

Java introduced the `java.lang.Record` class in Java 14. This conflicts with a wildcard
import of the Kinesis model classes, which also defines a `Record` class, and causes the
following compilation error:

> reference to Record is ambiguous
> both class com.amazonaws.services.kinesis.model.Record in com.amazonaws.services.kinesis.model and class java.lang.Record in java.lang match

Since the library supports Java 8 as a minimum version, I use OpenJDK 8 to build the
library to ensure that I don't accidentally add a dependency on a later version. As a
result, I don't see this error and have no plans to fix it. It's a compile-time error,
so does not affect the operation of the library.

If you want to build your own version on a later JDK, you will need to replace the
wildcard imports with explicit imports.
