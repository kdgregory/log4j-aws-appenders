# Client Connections

This library provides several ways to create and configure an AWS service client;
the following mechanisms are tried in order:

1. An application-provided static factory method, specified with the `clientFactory`
   configuration parameter. This allows you to perform any customization that you want.
2. Using the client-specific "builder" object, optionally assuming a role. This is the
   mechanism used for all "modern" SDK versions.
3. For early releases of the AWS SDK, which did not have client factories, calling the
   relevant client constructor. This supports the credentials provider chain, but does
   not support the region provider chain (instead defaulting to `us-east-1`). You can,
   however, explicitly specify a region.

**Note:** one thing that this library does not &mdash; _and will never_ &mdash;
allow is storing access keys in the logger configuration file. Please follow the
[AWS best practices](https://docs.aws.amazon.com/general/latest/gr/aws-access-keys-best-practices.html)
for managing your credentials.


## Configuration Properties

All appenders provide the following connection properties, which are described in detail below.

**Note:** these configuration properties cannot be changed once the appender has started.

Name                | Description
--------------------|----------------------------------------------------------------
`assumedRole`       | The ARN or name of a (possibly cross-acount) role that the logger can assume.
`clientFactory`     | The fully-qualified name of a static method that will be invoked to create the AWS service client.
`clientRegion`      | Specifies a non-default region for the client.
`clientEndpoint`    | Specifies a non-default endpoint; only supported for clients created via constructors.


### `assumedRole`

If specified, the appender will attempt to assume a role before creating the client
connection. This is intended for writing logs to a destination owned by a different
account.

> Note: only applies when using a "builder" object to create the client, not when
  using either a constructor or application-provided factory method.

> Note: in the current implementation, failure to assume a role will result in
  falling back to the client constructor.

May be specified as either a simple role name or an ARN. If specified as a name, a
role with that name must exist in the current account.

Permissions required:

* `sts:AssumeRole`
* `iam:ListRoles` to provide a role name rather than an ARN.

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

> Note: if you provide a client factory method, the appender will not try any other
  mechanisms.

If unable to invoke the client method, the appender will report the error using the
logging framework's internal status logger. It will not fallback to another client
construction mechanism.

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
that this complexity would not be worthwhile. While the SDK client holds a connection
for multiple requests, those connections (1) will be closed on their own, and (2) use
a finalizer for the case where a client goes out of scope.
