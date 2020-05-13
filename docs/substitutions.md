# Substitution Variables

Some configuration parameters (such as CloudWatch log group or Kinesis stream) may use substitution
variables from the table below. To use, enclose the variable in braces (eg: `MyLog-{date}`).


Variable            | Description
--------------------|----------------------------------------------------------------
`date`              | Current UTC date: `YYYYMMDD`
`timestamp`         | Current UTC timestamp: `YYYYMMDDHHMMSS`
`hourlyTimestamp`   | Current UTC timestamp, with minutes and seconds truncated: `YYYYMMDDHH0000`
`startupTimestamp`  | UTC timestamp of JVM startup as returned by `RuntimeMxBean`: `YYYYMMDDHHMMSS`
`sequence`          | A sequence number that's incremented each time a log is rotated (only useful with appenders that rotate logs)
`pid`               | Process ID (see below)
`hostname`          | Hostname (see below)
`env:XXX`           | Environment variable `XXX`; see below for complete syntax.
`sysprop:XXX`       | System property `XXX`; see below for complete syntax.
`instanceId`        | _Deprecated_: use `ec2:instanceId`
`aws:accountId`     | AWS account ID. Useful for cross-account logging (eg, as part of a CloudWatch log stream name)
`ec2:instanceId`    | EC2 instance ID; see below.
`ec2:region`        | Region where the current instance is running; see below.
`ssm:XXX`           | Parameter Store value `XXX`; see below.

If unable to replace a substitution variable, the tag will be left in place. This could happen due
to a incorrect or unclosed tag, or an unresolvable system property or environment variable.

The `pid` and `hostname` values are parsed from `RuntimeMxBean.getName()` and may not be available
on all JVMs (in particular non-OpenJDK JVMs may use a different format). When running in a Docker
container, the container ID is reported as the hostname.

The `env` and `sysprop` substitutions have two forms: `env:VARNAME` (or `sysprop:VARNAME`) and
`env:VARNAME:DEFAULT` (ditto for sysprops). For example, if the environment variable `FOO` is
undefined, then the using first form (`env:FOO`) results in `{env:FOO}`, while the second form
(`env:FOO:bar`) results in `bar`.

The `aws` substitutions connect to AWS to retrieve information. If you do not have network
connectivity or properly configured credentials these will fail. You must also have the relevant
AWS SDK library in your classpath.

The `ec2` substitutions retrieve their information from the EC2 metadata service. Using these
variables in any other environment will result in a (long) wait as the SDK tries to make an HTTP
request to the (non-existent) metadata endpoint.

The `ssm` substitutions retrieve their values from the [Systems Manager Parameter
Store](https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-parameter-store.html).
Like the `env` and `sysprop` substitutions, these have two forms: `ssm:XXX` will fail
if the parameter named `XXX` doesn't exist, returning the un-substituted key, while
`ssm:XXX:YYY` returns `YYY` if the parameter doesn't exist.

> Caveats: to use SSM substitutions, you must be using at least version 1.11.63 of the SDK (earlier
  versions did not support parameter store). All parameters must be defined in the "current"
  region (even if you've configured your appender to write to a different region). And you
  can't retrieve the values of secure string parameters.


## Log4J2 Support

Log4J 2.x provides its own [substitution](https://logging.apache.org/log4j/2.x/manual/configuration.html#PropertySubstitution)
mechanism, which works together with the substitutions described here. The type of substitution
and order of application depends on the syntax:

1. `${key}` Log4J2 substitution that's resolved when the configuration is read.
2. `$${key}` Log4J2 substitution that's resolved at runtime, when the log-writer
   is created (in general, this is when the appender is created, but appenders
   that rotate their log-writers will re-resolve for each new log-writer).
3. `{key}` in-library substitution, that's resolved at runtime, after all Log4J2
   substitutions have been resolved.

This library also exposes all of the substitutions described above to the Log4J2
lookup mechanism, using the prefix `awslogs`. Here are some examples:

Substitution                | Description
----------------------------|----------------------------------------------------------------
`${date:yyyyMMddHHmmss}`    | Log4J2 substitution, resolved when configuration is read.
`$${date:yyyyMMddHHmmss}`   | Log4J2 substitution, resolved when log-writer is created.
`{timestamp}`               | Library-defined substitution, resolved when log-writer is created.
`${awslogs:timestamp}`      | Log4J2 syntax for invoking library substitution, resolved when configuration is read.
`$${awslogs:timestamp}`     | Log4J2 syntax for invoking library substitution, resolved when log-writer is created.
`{env:FOO}`                 | Library-defined substitution, reading environment variable `FOO` with no default.
`{env:FOO:bar}`             | Library-defined substitution, reading environment variable `FOO` with default value of "bar".
`${env:FOO}`                | Log4J2 config-time substitution, reading environment variable `FOO` with no default.
`${env:ENV_NAME:-bar}`      | Log4J2 config-time substitution, reading environment variable `FOO` with default value of "bar".
`${awslogs:env:FOO:bar}`    | Log4J2 config-time substitution, using library substitution to read environment variable `FOO` with default value of "bar" (yes, this is a silly example, but it shows the general behavior).

Note: in the 2.3.0 release, there were some substitution variables that used different names
(eg: `awslogs:awsAccountId` rather than `awslogs:aws:accountId`). These are still supported,
but are deprecated and have been removed from the documentation.


## Caveats and additional information

A particular destination may not accept all of the characters produced by a substitution, and the
appenders will not initialize if that happens. As a general rule you should limit substituted
values (from environment variable or system properties) to alphanumeric characters, hyphens, and
underscores.
