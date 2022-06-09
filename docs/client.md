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


## Using a proxy

You can configure the appenders to use a proxy host via the standard application-wide
mechanisms used by your SDK of choice. Unfortunately, doing so is an undocumented hot
mess (so here's some documentation).


### If you're using SDK version 1:

You can configure a proxy using environment variables or system properties. If you use
both, system properties override environment variables.

There are two environment variables: `HTTP_PROXY` and `HTTPS_PROXY`:

* The SDK picks one or the other based on the way the client connects to AWS: clients
  that connect via HTTPS (the default) use `HTTPS_PROXY`, while clients that connect
  via HTTP use `HTTP_PROXY`.

* The URLs have the form `http://HOST:PORT` or `http://USERNAME:PASSWORD@HOST:PORT`
  (eg: `http://squidproxy.internal:3128`, `http://me:pass-123@squidproxy.internal:3128`).
  The first form is for proxies that don't require authentication, the second for those
  that do.

There are eight system properties: `http.proxyHost`, `http.proxyPort`, `http.proxyUser`,
`http.proxyPassword`, `https.proxyHost`, `https.proxyPort`, `https.proxyUser`, and
`https.proxyPassword`:

* Similar to environment variables, the properties starting with `http` are used for
  unsecured client connections, while those starting with `https` are used for
  secured connections.


### If you're using SDK version 2:

There is currently no support for environment variables. I submitted an
[issue](https://github.com/aws/aws-sdk-java-v2/issues/2958) to the SDK project
to rectify this, but as-of this writing it hasn't been implemented.

There are _five_ system properties: `http.proxyHost`, `http.proxyPort`, `http.proxyUser`,
`http.proxyPassword`, and `http.nonProxyHosts`:

* Unlike the v1 SDK, these apply to all connections (I don't believe that v2 supports
  non-HTTPS client connections, so that may be a moot point).

* The property `http.nonProxyHosts` contains a vertical-bar-separated (`|`) list of
  IP addresses that should not be proxied.


### Security

Whether you use either environment variables or system properties, the connection between
your client application and the proxy server happens over HTTP. In order to have a secure
connection to the proxy server, you will need to manually configure your clients.

I do not believe this is an issue: the appenders library use default AWS client-builders,
which make a HTTPS connections to AWS. This means that the connection to the proxy is
irrelevant, as communication is end-to-end encrypted.


### Manual proxy configuration

If you do want to manually configure your clients, you will need to implement a
[client factory](#application-provided-factory-method). This gives you far more
options for proxy configuration, such as support for Windows NTLM authentication.
I've created  an [example](https://gist.github.com/kdgregory/d6bed8c245ce326d461aeb65825358d2)
of how to configure a proxy for both versions of the SDK.

**One important caveat**: factory methods are only supported for the "core" clients
(CloudWatch Logs, Kinesis Streams, and SNS). They cannot be used with "auxilliary"
clients, such as the one used to retrieve SSM Parameter Store values for substitutions.
