# Building It Yourself

There are multiple projects in this repository:

* [aws-shared](../aws-shared): the AWS log-writers and supporting code that would be
  used by any appender implementation.
* [log4j1-appenders](../log4j1-appenders): appender implementations for Log4J 1.x.
* [log4j2-appenders](../log4j2-appenders): appender implementations for Log4J 2.x.
* [logback-appenders](../logback-appenders): appender implementations for Logback.
* [examples](../examples): example programs that demonstrate using the appenders.
* [integration-tests](../integration-tests): integration tests that execute several
  scenarios against each set of appenders, using actual AWS resources.

All sub-projects are built using [Apache Maven](http://maven.apache.org/). The build commands
differ depending on project:

* appenders (including aws-shared): `mvn clean install` run from the project root.
* examples: `mvn clean package` (see individual documentation for running the examples).
* integration tests: `mvn clean test`.

**Beware:** the integration tests and examples create AWS resources and do not delete them.
You will be charged for those resources, including a per-hour charge for the Kinesis streams.
To avoid charges, be sure to delete all resources when they're no longer needed.


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
case they're named after an issue (eg: `dev-21`), or release-prep branches, in which case
they're named `dev-MAJOR.MINOR.PATCH`. Development branches may be rebased as I see fit:
I often make "checkpoint" commits to save my work and then rebase them into a single commit.
Once a development branch is merged it is deleted.

Features are merged into either a release-prep branch or master, using a pull request and
squash merge. If multiple independent features are combined into a release, each feature
commit is preserved in master (note that major functionality, such as a new logging framework,
may be considered a single feature). If you want to see the individual commits that went into
a branch, you can look at the closed PR.

Each "release" version is tagged with `release-MAJOR.MINOR.PATCH`.


## Parent versus Driver POMs

Each of the builds listed above -- appenders, integration tests, and examples -- uses a "driver"
POM that triggers the sub-project builds. This is not a "parent" POM: it is not referenced by
the sub-projects, and does not contain any project-wide definitions. The primary goal of these
driver POMs is to be able to build projects that require the same set of build commands (eg,
"install" for the libraries, versus "package" for the examples).

There is a parent POM, which provides plugin configuration and version properties that are used
throughout the project (other than the examples, which I want to be stand-alone).


## Code coverage (or lack thereof)

The library builds are configured to use the Cobertura code coverage tool. It appears to work
successfully for the `aws-shared` directory, but generates a report indicating 0% coverage for
the Log4J and Logback appenders library. It's unclear to me why this is happening: there aren't
any errors in the build log, it shows Cobertura instrumenting classes before running tests, and
then running again to generate the report.

I will dig into this for a later release; for now, I consider all coverage numbers suspect.


## AWS permissions needed for integration tests

While the individual appender docs list the permissions needed to use those appenders, the
integration tests require many more permissions: they have to create, examine, and delete
resources. While I normally run using my personal "Administrator" permissions, I also run
a pre-release test on EC2 using an instance role with the following policy.

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


## Cleaning up after integration tests

The integration tests create AWS resources but do not delete them. This is intentional, to
support post-mortem debugging. However, some of those resources incur a per-hour charge,
and in general you don't want to have a lot of unused/untagged resources in your account.

Assuming that you're using Bash, you can clean up these reources with this script. I save
it in my personal `bin` directory with the name `cleanup.sh`. It assumes that you're running
in the `us-east-1` region; if you're running elsewhere, add your region to each of the `for`
statements.

**Beware:** this script deletes all resources with "IntegrationTest" in their name.  If you
have resources that match, they will be deleted as well.

```
#!/bin/bash

echo "CloudWatch Logs"
for r in 'us-east-1' 'us-east-2' 'us-west-1' 'us-west-2' ; \
    do for g in $(aws logs describe-log-groups --region $r --query 'logGroups[].logGroupName' | grep IntegrationTest | sed -e 's/[ ",]*//g') ; \
        do echo $r " - " $g ; \
        aws logs delete-log-group --region $r --log-group-name $g ; \
    done ; \
done

echo "Kinesis"
for r in 'us-east-1' 'us-east-2' 'us-west-1' 'us-west-2' ; \
    do for s in $(aws kinesis list-streams --region $r --output text | grep IntegrationTest | awk '{print $2}') ; \
        do echo $r " - " $s ; \
        aws kinesis delete-stream --region $r --stream-name $s ; \
    done ; \
done

echo "SNS"
for r in 'us-east-1' 'us-east-2' 'us-west-1' 'us-west-2' ; \
    do for t in $(aws sns list-topics --region $r --output text | grep IntegrationTest | awk '{print $2}') ; \
        do echo $r " - " $t ; \
        aws sns delete-topic --region $r --topic-arn $t ; \
    done ; \
done

for r in 'us-east-1' 'us-east-2' 'us-west-1' 'us-west-2' ; \
    do for s in $(aws sns list-subscriptions --region $r --query 'Subscriptions[].SubscriptionArn' | grep IntegrationTest | sed -e 's/[ ",]*//g') ; \
        do echo $r " - " $s ; \
        aws sns unsubscribe --region $r --subscription-arn $s ; \
    done ; \
done

for r in 'us-east-1' 'us-east-2' 'us-west-1' 'us-west-2' ; \
    do for q in $(aws sqs list-queues --region $r | grep IntegrationTest | sed -e 's/[ ",]*//g') ; \
        do echo $r " - " $q ; \
        aws sqs delete-queue --region $r --queue-url $q ; \
    done ; \
done
```
