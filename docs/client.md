# Client Configuration

This library provides two ways to create an AWS service client:

* Using an application-provided static factory method. This allows the application
  to configure the client in arbitrary ways, including retrieving credentials from
  an external source.

* Using the "client builder" objects provided by the SDK. This uses the default
  credentials provider, and allows limited configuration.

The `clientFactory` configuration parameter controls which approach is used.


## Application-Provided Factory Method

If the `clientFactory` parameter is set, it must contain a fully-qualified method name
(`package.class.method`). The appender library then uses reflection to identify and
invoke this method. If unable to invoke (most likely, because it doesn't exist), then
the appender aborts; it does not fall back to the SDK-provided client builder.

There are two supported variants for this method. The first takes no parameters; it
must retrieve any configuration from an external source. The second is passed the
`assumedRole`, `clientRegion`, and `clientEndpoint` configuration parameters. It may
choose to use or ignore them.

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


## SDK-Provided Client Builder

If the `clientFactory` is not set, then the appender will use the SDK-provided client builder.
This client builder can be configured as below to use an assumed role, or a non-default region
or endpoint.

> The client builder uses the [default credential provider chain](https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html).
You can not provide credentials as part of the appender configuration. This is an intentional
decision, and this library will never support credentials in the logging config. Please don't
ask.


### `assumedRole`

If specified, the appender attempts to assume a role before creating the client.
This supports writing logs to a destination owned by a different account, but may
also be used to perform "least privilege" application configuration (ie, your app
has the permission to assume a logging role, which in turn controls access to the
destination).

This parameter may be specified as either a simple role name or an ARN. In the former
case, the role name must identify a role within the current account.

If unable to assume the role, the appender will report the error using the logging
framework's internal status logger. It will not attempt to operate with the default
credentials.

Permissions required to use this feature:

* `iam:ListRoles`
* `sts:AssumeRole`

Example:

```
log4j.appender.cloudwatch.assumedRole=CloudWatchAppenderIntegrationTest-Log4J1
```


### `clientEndpoint`

If specified, identifies the endpoint for API calls, overriding the region-specific
default endpoint. This is used when running inside a VPC without access to the Internet,
or with a simulated environment such as [localstack](https://github.com/localstack/localstack).

If unable to configure a client with the desired endpoint, the appender will report the
error using the logging framework's internal status logger. It will not create a client
using a default endpoint.

> Note: the version 1 AWS SDK allows you to specify a client endpoint as either a hostname
  or a URL. Version 2 requires a URL.

Example:

```
log4j.appender.cloudwatch.clientEndpoint=https://myservice.example.com
```


### `clientRegion`

If specified, identifies the region where the logging destination is found. This allows
you to centralize logging while running your application in multiple regions.

If unable to set the region, the appender will report the error using the logging
framework's internal status logger. It will not create a client in the default
region.

> **Beware:** the SDK validates this against an internal list of regions. Older SDKs
  do not support all regions.

Example:

```
log4j.appender.cloudwatch.clientRegion=us-east-2
```
