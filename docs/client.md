# Client Connections

This library provides several ways to create and configure an AWS service client:

1. An application-provided static factory method, specified with the `clientFactory`
   configuration parameter. This allows you to perform any customization that you want.
2. Using the client-specific "builder" object, optionally assuming a role. This is the
   mechanism used for all "modern" SDK versions.
3. For early releases of the AWS SDK, which did not have builder objects, calling the
   relevant client constructor. This supports the credentials provider chain, but does
   not support the region provider chain (instead defaulting to `us-east-1`). You can,
   however, explicitly specify a region.

Each of these mechanisms is tried in order. If the appender is not _able_ to use the
method (_ie,_ no static factory method configured, SDK does not support builders), it
will try the next mechanism.

However, _if a mechanism fails due to configuration_ (_eg,_ an invalid region, or factory
method that doesn't exist), the appender will not start. This prevents accidental
misconfiguration from turning into a hidden security breach (_eg,_ if you assume a role
to write sensitive log messages to a different account, you don't want an invalid role
name to result in writing those messages in the current account).


## Configuration Properties

All appenders provide the following connection properties, which are described in detail
below.  These configuration properties cannot be changed once the appender has started.

Name                | Description
--------------------|----------------------------------------------------------------
`assumedRole`       | The ARN or name of a (possibly cross-acount) role that the logger can assume.
`clientFactory`     | The fully-qualified name of a static method that will be invoked to create the AWS service client.
`clientRegion`      | Specifies a non-default region for the client.
`clientEndpoint`    | Specifies a non-default endpoint; only supported for clients created via constructors.

**Note:** One thing that this library does not &mdash; _and will never_ &mdash; allow is 
providing access keys via configuration properties. Please follow the [AWS best
practices](https://docs.aws.amazon.com/general/latest/gr/aws-access-keys-best-practices.html)
for managing your credentials.



### `assumedRole`

If specified, the appender will attempt to assume a role before creating the client
connection. This is intended for writing logs to a destination owned by a different
account.

> Note: only applies when using a version of the SDK that provides "builder" objects
  to create the client. Not supported for either constructor or application-provided
  factory methods (although you can implement your own mechanism in the latter).

May be specified as either a simple role name or an ARN. If specified as a name, a
role with that name must exist in the current account.

Permissions required:

* `iam:ListRoles`
* `sts:AssumeRole`

If unable to assume the role, the appender will report the error using the logging
framework's internal status logger. It will not attempt to operate with the default
credentials.

Example:

```
log4j.appender.cloudwatch.assumedRole=CloudWatchAppenderIntegrationTest-Log4J1
```


### `clientFactory`

If specified, identifies the fully qualified name of a static method provided by the
application code. This method must return an appropriate SDK client, which will then
be used to write to the destination. This is intended to support client configuration
that isn't otherwise covered by the appender (for example, using an HTTP proxy).

There are two supported variants for this method. The first takes no parameters; it
must retrieve any configuration from an external source. The second is passed three
parameters from the appender configuration.

If unable to invoke the client method, the appender will report the error using the
logging framework's internal status logger. It will not fallback to another client
construction mechanism.

Example method signatures:

* `public static AWSLogs createCloudWatchClient()`
* `public static AWSLogs createCloudWatchClient(String assumedRole, String region, String endpoint)`

Example configuration:

```
log4j.appender.cloudwatch.clientFactory=com.kdgregory.logging.test.AbstractCloudWatchAppenderIntegrationTest.createClient
```

Example implementation (note that it just returns the default client, so is rather pointless):

```
public static AWSLogs createClient()
{
    return AWSLogsClientBuilder.defaultClient();
}
```


### `clientRegion`

If specified, identifies the region where the logging destination is found. This
is primarily intended to support early SDK versions, which only provide client
constructors, and default the region to `us-east-1`. It may also be used for
newer SDKs, to support cross-region logging.

**Beware:** the SDK will validate this against an internal list of regions. You
will need to upgrade your SDK version to configure a region that was released
after it.

If unable to set the region, the appender will report the error using the logging
framework's internal status logger. It will not create a client in the default
region.

Example:

```
log4j.appender.cloudwatch.clientRegion=us-east-2
```


### `clientEndpoint`

If specified, identifies a hostname that should be used for SDK connections. _This
is only supported for pre-builder SDK versions,_ and is intended to overcome the
problem of specifying a newer region. The regional endpoints for CloudWatch Logs
are listed [here](https://docs.aws.amazon.com/general/latest/gr/rande.html#cwl_region),
Kinesis [here](https://docs.aws.amazon.com/general/latest/gr/rande.html#ak_region),
and SNS [here](https://docs.aws.amazon.com/general/latest/gr/rande.html#sns_region).

Example:

```
log4j.appender.cloudwatch.clientEndpoint=logs.us-west-2.amazonaws.com
```


# Client Shutdown

At the present time, this library does not explicitly shut down its AWS clients. A
client is created when the log-writer starts, and will be garbage collected when
the log-writer goes out of scope (which only happens when the logging framework is
shut down prior to program exit).

I have considered adding a `shutdown()` method to `ClientFactory`, but this leads
to some undesirable complexity: how should I handle clients that have been created
using an application-specific factory method? Should I require applications to
provide a `shutdown()` method as well? For that matter, does anybody actually use
that functionality?

After spending time looking at the `AmazonWebServiceClient` implementation, I decided
this complexity would not be worthwhile. While the SDK client does use a connection
for multiple requests, those connections (1) are closed when a timeout expires, and
(2) use a finalizer for the case where a client goes out of scope.
