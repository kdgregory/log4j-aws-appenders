# Building It Yourself

There are multiple projects in this repository:

* [aws-shared](../aws-shared): the AWS log-writers and supporting code that would be
  used by any appender implementation.
* [log4j1-appenders](../log4j1-appenders): appender implementations for Log4J 1.x.
* [logback-appenders](../logback-appenders): appender implementations for Logback.
* [examples](../examples): example programs that demonstrate using the appenders.
* [integration-tests](../integration-tests): integration tests that execute several
  scenarios against each set of appenders.

All sub-projects are built using [Apache Maven](http://maven.apache.org/). The build commands
differ depending on project:

* appenders (including aws-shared): `mvn clean install` run from the project root
* examples: `mvn clean package` (see individual documentation for running)
* integration tests: `mvn clean test`

**Beware:** the integration tests and examples create resources and do not delete them. You
will be charged for those resources, including a per-hour charge for the Kinesis streams.
To avoid charges, be sure to delete all resources when they're no longer needed.


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


## Interface Stability

Classes in top-level mainline packages (eg, `com.kdgregory.log4j.aws`) are expected to remain
backwards compatible.

Any other classes, particularly those under packages named `internal`, may change arbitrarily
and should not be relied-upon by user code. This caveat also applies to all test classes and
packages.
