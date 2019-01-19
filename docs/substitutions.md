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
`pid`               | Process ID (this is parsed from `RuntimeMxBean.getName()` and may not be available on all platforms)
`hostname`          | Hostname (this is parsed from `RuntimeMxBean.getName()` and may not be available on all platforms; if running in a Docker container, it will be the container ID)
`instanceId`        | _Deprecated_: use `ec2:instanceId`
`aws:accountId`     | AWS account ID. This exists to support SNS topic ARNs, probably not useful elsewhere.
`ec2:instanceId`    | EC2 instance ID; see below.
`ec2:region`        | Region where the current instance is running; see below.
`env:XXX`           | Environment variable `XXX`; see below for complete syntax.
`sysprop:XXX`       | System property `XXX`; see below for complete syntax.

If unable to replace a substitution variable, the tag will be left in place. This could happen due
to a bogus or unclosed tag, or an unresolvable system property or environment variable.

The `aws` substitutions will connect to AWS to retrieve information. If you do not have network
connectivity or properly configured credentials these will fail.

The `ec2` substitutions retrieve their information from the EC2 metadata service. Using these variables
in any other environment will result in a (long) wait as the SDK tries to make an HTTP request to the
non-existent endpoint.

The `env` and `sysprop` substitutions have two forms: `env:VARNAME` (or `sysprop:VARNAME`) and
`env:VARNAME:DEFAULT` (ditto for sysprops). For example, if the environment variable `FOO` is
undefined, then the first form (`env:FOO`) will result in `{env:FOO}` in the output. Using the
form `env:FOO:bar` will succeed, resulting in `bar` in the output.

Note that a particular destination may not accept all of the characters produced by a substitution,
and the appenders will not initialize that happens. As a general rule you should limit substituted
values (from environment variable or system properties) to alphanumeric characters, hyphens, and
underscores.
