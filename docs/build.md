There are three projects in this repository:

* [aws-core](../aws-core): the AWS log-writers and supporting code that would be used
  by any appender implementation.
* [log4j1-appenders](../log4j1-appenders): the Log4J 1.x compatible appenders.
* [log4j1-integration-tests](../log4j1-integration-tests): a set of integration tests
  for the Log4J 1.x appenders. These are in a separate project so that they can be
  can be run as desired, rather than as part of every build.
* [log4j1-example](../log4j1-example): an example program using all of the Log4J 1.x
  appenders. It includes CloudFormation templates to create destinations.


## Building

All sub-projects are built using [Apache Maven](http://maven.apache.org/). The build commands
differ depending on project:

* appenders: `mvn clean install`
* tests: `mvn clean test`
* example: `mvn clean package`

**Beware:** the `tests` project creates AWS resources and does not delete them. You will
be charged for those resources, including a per-hour charge for the Kinesis streams. The
`example` project also incurs AWS charges while running, and for any resources that you
do not delete.


## Source Control

The `master` branch is intended for "potentially releasable" versions. Commits on master
are functional, but may not be "complete" (for some definition of that word). They may be
snapshot or release builds. Master will never be rebased; once a commit is made there it's
part of history for better or worse.

All development takes place on a branch. Branches are either feature branches, in which
case they're named after an issue (eg: `dev-21`), or release-prep branches, in which case
they're named `dev-MAJOR.MINOR.PATCH`. Development branches may be rebased as I see fit:
I often make "checkpoint" commits to save my work and then rebase them into a single commit.
Once a development branch is merged it is deleted.

Features are merged into either a release-prep branch or master, using a pull request and
squash merge. If you want to see the individual commits that went into a branch, you can
look at the closed PR.

Each "release" version is tagged with `release-MAJOR.MINOR.PATCH`.


## Interface Stability

Classes in the top-level `com.kdgregory.log4j.aws` package are expected to remain backwards
compatible.

Any other classes, particularly those under packages named `internal`, may change arbitrarily
and should not be relied-upon by user code. This caveat also applies to all test classes and
packages.
