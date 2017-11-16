# Substitution Variables

Logging destination names (such as a CloudWatch log group or Kinesis stream) may use substitution
variables from the table below. To use, these must be brace-delimited (eg: `MyLog-{date}`, _not_
`MyLog-date`) and may appear in any configuration variable that allows substitutions.


Variable            | Description
--------------------|----------------------------------------------------------------
`date`              | Current UTC date: `YYYYMMDD`
`timestamp`         | Current UTC timestamp: `YYYYMMDDHHMMSS`
`hourlyTimestamp`   | Current UTC timestamp, with minutes and seconds truncated: `YYYYMMDDHH0000`
`startupTimestamp`  | UTC timestamp of JVM startup as returned by `RuntimeMxBean`: `YYYYMMDDHHMMSS`
`sequence`          | A sequence number that's incremented each time a log is rotated (only useful for loggers that rotate logs)
`pid`               | Process ID (this is parsed from `RuntimeMxBean.getName()` and may not be available on all platforms)
`hostname`          | Unqualified hostname (this is parsed from `RuntimeMxBean.getName()` and may not be available on all platforms)
`instanceId`        | _Deprecated_: use `ec2:instanceId`
`aws:accountId`     | AWS account ID. This exists to support SNS topic ARNs, should not be used elsewhere.
`ec2:instanceId`    | EC2 instance ID; see below
`ec2:region`        | Current AWS region; see below
`env:XXX`           | Environment variable `XXX`
`sysprop:XXX`       | System property `XXX`

If unable to replace a substitution variable, the tag will be left in place. This could happen due
to a bogus or unclosed tag, or an unresolvable system property or environment variable.

The `aws` substitutions will connect to AWS to retrieve information. If you do not have network
connectivity or properly configured credentials these will fail.

The `ec2` substitutions retrieve their information from the EC2 metadata service. Using these variables
in any other environment will result in a (long) wait as the SDK attempts to retrieve metadata.

Note that a particular destination may not accept all of the characters produced by a substitution,
and the logger will remove illegal characters. As a general rule you should limit substitution values
to alphanumeric characters, along with hyphens and underscores.
