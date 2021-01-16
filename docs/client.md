# Client Configuration

This library provides two ways to create an AWS service client:

* Using an application-provided static factory method. This allows the application
  to configure the client in arbitrary ways, including retrieving credentials from
  an external source.

* Using the "client builder" objects provided by the SDK. This uses the default
  credentials provider, and allows limited configuration.

Which is used depends on the setting of the `clientFactory` configuration parameter.


## Application-Provided Factory Method

If the `clientFactory` parameter is set, it must contain a fully-qualified method name
(`package.class.method`). The appender library will use reflection to to invoke this
method. If unable to invoke this emthod, then the appender wil abort; it does not fall
back to the SDK-provided client builder.

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
You can not provide credentials as part of the appender configuration.


### `assumedRole`

If specified, the appender will attempt to assume a role before creating the client
connection. This is intended for writing logs to a destination owned by a different
account.

This parameter may be specified as either a simple role name or an ARN. In either
case, the appender will list all roles to verify that it exists.

The `clientRegion` and `clientEndpoint` configuration parameters are ignored when
this parameter is set. If you are running with the VPC or do not have Internet access
for any other reason, you will need to use an application-managed client factory.

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

If specified, identifies a hostname that should be used for SDK connections. This is
intended for use when running inside a VPC without access to the Internet, or with a
simulated environment such as [localstack](https://github.com/localstack/localstack).

Example:

```
log4j.appender.cloudwatch.clientEndpoint=myservice.example.com
```


### `clientRegion`

If specified, identifies the region where the logging destination is found. This allows
you to centralize logging while running your application in multiple regions.

If unable to set the region, the appender will report the error using the logging
framework's internal status logger. It will not create a client in the default
region.

> **Beware:** the SDK validates this against an internal list of regions. Older SDKs
  do not support all regions.

This configuration parameter can also be used to modify `clientEndpoint`: if both are
specified, then the endpoint is configured to use the signature algorithm for the
specified region.

Example:

```
log4j.appender.cloudwatch.clientRegion=us-east-2
```
