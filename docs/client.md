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


## Using a Proxy

Proxy configuration for the AWS SDK is, bluntly, a hot mess:

* Version 1 can be configured using environment variables or system properties;
  the latter takes precedence over the former.

  The environment variables are named `HTTP_PROXY` and `HTTPS_PROXY`, and hold
  the URL of your proxy server. The system property names start with `http.proxy`
  or `https.proxy`, and define each component of the proxy URL (ie, `http.proxyHost`,
  `http.proxyPort`, `http.proxyUser`, and `http.proxyPassword`).

  The specific environment variable or set of sytem properties depends on the
  protocol used for _client connections_: if you use HTTPS (the default), the
  proxy is configured from the `HTTPS_PROXY` environment variable or `https.proxyXXX`
  system properties. If you use an HTTP connection your client uses the other
  variable/properties.

* Version 2 does not, at this writing, support environment variables. It does support
  system properties, all named starting with `http.proxy`, but otherwise equivalent
  to those supported by v1 (ie,  `http.proxyHost`,`http.proxyPort`, `http.proxyUser`,
  and `http.proxyPassword`).

* You can also configure the clients manually. The way that you do this differs by
  SDK. For v1 you configure a `ClientConfiguration` object and attach it to your
  client-builder. For v2, you create a `ProxyConfiguration` object, attach it to
  your _HTTP_ client-builder (eg, `ApacheHttpClient.Builder`), then attach that
  to your AWS client-builder.

If you use the SDK-defined environment variables or system properties, the appender
clients will be configured to match your other AWS clients. This is probably the
best (if painful) way to use the appenders with a proxy.

To configure _just_ the logging library, set the `COM_KDGREGORY_LOGGING_PROXY_URL`
environment variable:

```
export COM_KDGREGORY_LOGGING_PROXY_URL=http://squidproxy.internal:3128
```

This variable takes a URL of the form `SCHEME://USERNAME:PASSWORD@HOSTNAME:PORT`,
where `SCHEME` is either `http` or `https`, and `USERNAME:PASSWORD` is optional.

With all that out of the way, and the hindsight of having implemented proxies for
the library, I believe that avoiding proxies entirely is the best course of action.
If your goal is to control egress from your VPC, I believe it's better to use VPC
endpoints or a NAT with Network Firewall.
