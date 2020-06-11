# Building It Yourself

There are multiple projects in this repository:

* [aws-shared](../aws-shared): the AWS log-writers and supporting code that would be
  used by any appender implementation.
* [log4j1-appenders](../log4j1-appenders): appender implementations for Log4J 1.x.
* [log4j2-appenders](../log4j2-appenders): appender implementations for Log4J 2.x.
* [logback-appenders](../logback-appenders): appender implementations for Logback.
* [examples](../examples): example programs that demonstrate the appenders in action.
* [integration-tests](../integration-tests): integration tests that execute several
  scenarios against each set of appenders, using actual AWS resources.

All projects are built [Apache Maven](http://maven.apache.org/). The build commands
differ depending on project:

* appenders (including aws-shared): `mvn clean install` run from the project root.
* examples: `mvn clean package` (see individual documentation for running the examples).
* integration tests: `mvn clean test`.


## Interface Stability

Classes in top-level mainline packages (eg, `com.kdgregory.log4j.aws`) are expected to remain
backwards compatible.

Any other classes, particularly those under packages named `internal`, may change arbitrarily
and should not be relied-upon by user code. This caveat also applies to all test classes and
packages.


## Source Control

The `master` branch is intended for "potentially releasable" versions. Commits on master
are functional, but may not be "complete" (for some definition of that word). They may be
snapshot or release builds. Master will never be rebased: once a commit is made there it's
part of history for better or worse.

All development takes place on a branch. Branches are either feature branches, in which
case they're named after an issue (eg: `dev-issue-21`), or release-prep branches, in which
case they're named `dev-MAJOR.MINOR.PATCH`. Development branches may be rebased as I see fit:
I often make "checkpoint" commits to save my work and then rebase them into a single commit.
Once a development branch is merged it is deleted.

Features are merged into either a release-prep branch or master, using a pull request and
squash merge. If multiple independent features are combined into a release, each feature
commit is preserved in master (note that major functionality, such as a new logging framework,
is considered a single feature). If you want to see the individual commits that went into a
branch, you can look at the closed PR.

Each "release" version is tagged with `release-MAJOR.MINOR.PATCH`.


## Parent versus Driver POMs

Each of the builds listed above -- appenders, integration tests, and examples -- uses a "driver"
POM that triggers the sub-project builds. This is not a "parent" POM: it is not referenced by
the sub-projects, and does not contain any project-wide definitions. The primary goal of these
driver POMs is to be able to build projects that require the same set of build commands (eg,
"install" for the libraries, versus "package" for the examples).

There is a parent POM, which provides plugin configuration and version properties that are used
throughout the project (other than the examples, which I want to be stand-alone).


## Automated Tests

The `aws-shared` and appenders library are heavily tested using mock objects. Since these mocks
are only as good as my understanding of how the actual SDK works, there's also a full suite of
integration tests that exercise the appenders using actual AWS resources.

**BEWARE:** the integration tests incur AWS charges. These charges are small (well under $1),
but test failures may leave resources that continue to incur charges. _If you choose to run
the integration tests, you accept all charges that result._

The library builds are configured to use the Cobertura code coverage tool. It appears to work
successfully for the `aws-shared` directory, but generates a report indicating 0% coverage for
the appenders libraries. It's unclear to me why this is happening: there aren't any errors in
the build log, it shows Cobertura instrumenting classes before running tests, and then running
again to generate the report. As a result, I don't pay attention to the coverage report.


### AWS permissions needed for integration tests

While the individual appender docs list the permissions needed to use those appenders, the
integration tests require many more permissions: they have to create, examine, and delete
resources. While I normally run using "Administrator" permissions, I also run a pre-release
test on EC2 using an instance role with the following policy.

**Do not use this policy in production.** It is extremely over-powered, and includes
permissions that are not needed for normal use of an appender.

```
{
    "Version": "2012-10-17",
    "Statement": [
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
            "Sid": "Kinesis",
            "Effect": "Allow",
            "Action": [
                "kinesis:CreateStream",
                "kinesis:DeleteStream",
                "kinesis:DescribeStream",
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
        },
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
        }
    ]
}
```


### Cleaning up after integration tests

The integration tests create live AWS resources. And while each test tries to delete the
resources it creates on success, test failures leave these resources undeleted (this is
intentional, to support post-mortem debugging).

If you run the integration tests, I recommend running the following script afterward to
identify any undeleted resources (note: if you have resources named "IntegrationTest" it
will show them as well, so beware false positives!):


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
