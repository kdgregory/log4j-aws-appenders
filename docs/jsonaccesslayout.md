# JSON Access Layout (Logback only)

This layout transforms Logback's `IAccessEvent` into a JSON format, so that it can be delivered via
data pipeline to Elasticsearch. Like [JsonLayout](jsonlayout.md), you can attach metadata to the
rendered event, allowing you to easily classify log messages. It provides additional features that
support HTTP requests, such as the ability to include parameters or headers (or filter them out, to
avoid logging sensistive information).

Note: access logging from within the app-server should be considered a complement to [ELB access
logs](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-access-logs.html),
not a replacement for them.


## Configuration

See the [logback-access documentation](https://logback.qos.ch/access.html) for information about configuring
your server. You will need to copy both the `logback-aws-appenders` and `aws-shared` JARs into your server's
lib directory, in addition to the Logback JARs and any required AWS SDK JARs and their dependencies.

The complete list of properties is as follows (also available in the JavaDoc). Boolean properties are
explicitly enabled with the case-insensitive value "true", explicitly disabled with the case-insensitive
value "false", and default to "false" unless otherwise noted.

 Name                   | Type      | Description
------------------------|-----------|----------------------------------------------------------------------------------------------------------------
`appendNewlines`        | Boolean   | If "true", a newline will be appended to each record (default is false). This is useful when sending logging output to a file, particularly one read by an agent.
`enableInstanceId`      | Boolean   | If "true", the JSON will include the EC2 instance ID where the application is running. *WARNING*: This is retrieved from EC2 metadata, and will delay application startup if you're not running on EC2.
`enableHostname`        | Boolean   | Defaults to "true", including the logging server's hostname in the output; may be disabled by setting to "false".
`enableSessionId`       | Boolean   | If "true", the JSON will include the user's session ID.
`enableRemoteHost`      | Boolean   | If "true", the JSON will include the remote hostname, retrieved from the HTTP request. This may involve a DNS lookup.
`enableRemoteUser`      | Boolean   | If "true", the JSON will include the remote user's name, retrieved from the HTTP request.
`enableServer`          | Boolean   | If "true", the JSON will include the destination server name, retrieved from the HTTP request. Explicitly including the `Host` header is probably a better choice.
`enableQueryString`     | Boolean   | If "true", the JSON will include the query string. It's generally a better idea to enable paremeters, so that you can avoid [leaking secrets](#secrets-leakage).
`enableRequestHeaders`  | Boolean   | If "true", the JSON will include a sub-object containing request headers. You must additionally configure `includeHeaders` and `excludeHeaders`.
`enableResponseHeaders` | Boolean   | If "true", the JSON will include a sub-object containing response headers. You must additionally configure `includeHeaders` and `excludeHeaders`.
`includeHeaders`        | String    | A comma-separated list of header names to be included in the output, subject to filtering by `excludeHeaders`. You can specify "*" to include all headers.
`excludeHeaders`        | String    | A comma-separated list of header names that will be removed from the output.
`enableCookies`         | Boolean   | If "true", the JSON will include a list containing request cookies. You must additionally configure `includeCookies` and `excludeCookies`.
`includeCookies`        | String    | A comma-separated list of cookie names to be included in the output, subject to filtering by `excludeCookies`. You can specify "*" to include all cookies.
`excludeCookies`        | String    | A comma-separated list of cookie names that will be removed from the output.
`enableParameters`      | Boolean   | If "true", the JSON will include a sub-object containing request parameters. You must additionally configure `includeParameters` and `excludeParameters`.
`includeParameters`     | String    | A comma-separated list of parameter names to be included in the output, subject to filtering by `excludeParameters`. You can specify "*" to include all parameters.
`excludeParameters`     | String    | A comma-separated list of parameters names that will be removed from the output.
`tags`                  | String    | If present, the JSON will include a sub-object with specified user metadata. See [below](#metadata) for more information.


## Data

The generated JSON object will have the following properties. In general, the property names follow those in the
[Logback PatternLayout documentation](https://logback.qos.ch/manual/layouts.html#logback-access). 

 Key                | Value
--------------------|------------------------------------------------------------------------------------------------------------------------
`timestamp`         | The date/time that the message was logged, formatted as a UTC [ISO-8601](https://en.wikipedia.org/wiki/ISO_8601) timestamp with milliseconds (example: `2017-10-15T23:19:02.123Z`).
`thread`            | The name of the thread that handled the request.
`processId`         | The PID of the invoking process, if available (this is retrieved from `RuntimeMxBean` and may not be available on all platforms).
`hostname`          | The name of the machine where the logger is running, if available (this is retrieved from `RuntimeMxBean` and may not be available on all platforms).
`instanceId`        | The EC2 instance ID of the machine where the logger is running, if enabled.
`remoteIP`          | The IP address of the requesting host.
`remoteHost`        | The hostname of the requesting host, if available and enabled.
`forwardedFor`      | The value of the `X-Forwarded-For` header, if it exists. This is used to identify the requesting IP when running behind a load balancer.
`server`            | The hostname of the destination server, if enabled.
`protocol`          | The name and version of the request protocol (eg, "HTTP/1.1"). Note that, while this name is consistent with logback-access and the J2EE servlet spec, the HTTP spec (RFC 2616) calls it "HTTP-Version".
`requestMethod`     | The HTTP request method (eg: `GET`).
`requestURI`        | The path component of the requested URL, _not including the context root_.
`queryString`       | If enabled, the unparsed query string from the requested URL. Enabling request parameters is more generally useful.
`user`              | The name of the remote user, if enabled and known.
`sessionId`         | The user's session ID, if enabled. Will be blank if the session has not been set (the layout catches a [Logback-triggered exception](https://jira.qos.ch/browse/LOGBACK-1437)).
`cookies`           | If enabled, contains a child list with the request's cookies, subject to [filtering](#secrets-leakage). Each cookie is an object with fields `name`, `domain`, `path`, `value`, `maxAge`, `comment`, `version`, `isSecure`, and `isHttpOnly`. See the [J2EE docs](https://docs.oracle.com/javaee/6/api/javax/servlet/http/Cookie.html) for information about these fields.
`statusCode`        | The HTTP status code returned to the requester.
`elapsedTime`       | The amount of time taken to process the request, in milliseconds.
`bytesSent`         | The content length of the response.
`requestHeaders`    | If enabled, contains a child object with the request's headers, subject to [filtering](#secrets-leakage).
`responseHeaders`   | If enabled, contains a child object with the response's headers, subject to [filtering](#secrets-leakage).
`parameters`        | If enabled, contains a child object with the request parameters, subject to [filtering](#secrets-leakage). If a parameter has multiple values, they are converted to a "stringified list" (eg, `[val1,val2,val3]`).

The following properties are available from `PatternLayout` but have been intentionally omitted from this layout:

 Key                | Value
--------------------|------------------------------------------------------------------------------------------------------------------------
`requestUrl`        | This is not, in fact, the request URL, but what RFC 2616 refers to as the "Request Line": a combination of HTTP method, URI, and HTTP version. This information is available as individual elements in the JSON record.
`requestContent`    | In addition to the possibility of exceeding supported message sizes, writing unsanitized request content is a significant security risk.
`responseContent`   | In addition to the possibility of exceeding supported message sizes, writing unsanitized response content is a significant security risk.


## Metadata

The `tags` property is intended to provide metadata for search-based log analysis. It is specified using
a comma-separated list of `NAME=VALUE` pairs, and results in the creation of a `tags` sub-object in the log
message (see example below). Values may include [substitutions](substitutions.md), which are evaluated when
the layout is instantiated.


## Secrets Leakage

By writing headers, cookies, and parameters to the log, this layout opens the possibility for
leaking secrets: passwords, credit card numbers, authentication tokens, &c. To avoid accidental
leakage, you must configure a whitelist and optional blacklist in addition to enabling each type
of secret-containing output.

* The whitelist, configured with `include...`, identifies keys that are to appear in the output.
  The special value "*" bypasses the whitelist and includes all values.
* The blacklist, configured with `exclude...`, identifies keys that are not to appear in the output,
  even if they're listed in the whitelist (ie, the blacklist takes precedence). An empty blacklist
  bypasses this check.

For example, if `enableRequestHeaders` is "true", and `includeHeaders` contains "Content-Length",
that will be the only request header written to the output. If the `includeHeaders` is "*" and
`excludeHeaders` contains "Content-Length", then every header _except_ `Content-Length` will be
written to the output.

Keys are matched without regard for case sensititivy (so "password", "PASSWORD", and "Password" are
all equivalent). However, they are written into the output in their original form.


## Example

The following configuration writes request information to CloudWatch Logs, where it can be queried using
[CloudWatch Logs Insights](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/AnalyzingLogData.html).

```
<configuration>
  <statusListener class="ch.qos.logback.core.status.OnConsoleStatusListener" />  

  <appender name="CLOUDWATCH" class="com.kdgregory.logback.aws.CloudWatchAppender">
    <logGroup>AppenderExample</logGroup>
    <logStream>WebApp-AccessLogs-{date}-{hostname}-{pid}</logStream>
    <layout class="com.kdgregory.logback.aws.JsonAccessLayout">
      <enableSessionId>false</enableSessionId>
      <enableParameters>true</enableParameters>
      <includeParameters>*</includeParameters>
      <excludeParameters>password,creditCardNumber</excludeParameters>
      <enableRequestHeaders>true</enableRequestHeaders>
      <enableResponseHeaders>false</enableResponseHeaders>
      <includeHeaders>Host,X-Amzn-Trace-Id</includeHeaders>
      <enableCookies>true</enableCookies>
      <includeCookies>*</includeCookies>
      <tags>app-server=example,startedAt={startupTimestamp}</tags>
    </layout>
  </appender>

  <appender-ref ref="CLOUDWATCH" />
</configuration>
```

Start Tomcat, deploy the [Logback example webapp](../examples/logback-webapp), open
`http://localhost:8080/logback-aws-appenders-webapp-2.1.0/example?argle=bargle&password=dummy`
in your browser, and you should see output like this in CloudWatch Logs (note that the password
is not present):

```
{
    "bytesSent": 703,
    "cookies": [{
        "comment": null,
        "domain": null,
        "isHttpOnly": false,
        "isSecure": false,
        "maxAge": -1,
        "name": "JSESSIONID",
        "path": null,
        "value": "AE43199714AF778E792C98D5778FEC27",
        "version": 0
    }],
    "elapsedTime": 31,
    "forwardedFor": "192.168.1.123",
    "parameters": {
        "argle": "bargle"
    },
    "processId": "20121",
    "protocol": "HTTP/1.1",
    "remoteIP": "127.0.0.1",
    "requestHeaders": {
        "host": "localhost:8080"
    },
    "requestMethod": "GET",
    "requestURI": "/logback-aws-appenders-webapp-2.1.0/example",
    "statusCode": 200,
    "tags": {
        "app-server": "example",
        "startedAt": "20181222231350"
    },
    "thread": "http-nio-8080-exec-1",
    "timestamp": "2018-12-22T23:13:58.438Z"
}
```
