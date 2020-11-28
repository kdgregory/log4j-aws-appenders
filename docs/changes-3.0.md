This release was an opportunity to make some behavioral changes that became apparent
after the library received wider use. My expectation is that most people will not be
affected; for those that are, this document is intended as a guide to updating your
logging configuration.


# CloudWatch Logs no longer supports logstream rotation

When I originally wrote the appender library, CloudWatch Logs had extremely limited
search capabilities. To simplify searching for specific events, it was nice to have
log streams that only contained a day's worth of messages. Since then, [CloudWatch Logs
Insights](https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/AnalyzingLogData.html)
became available, providing much more control over search (especially if you use JSON as
your log output format). It also searches all log streams in a group, so there is no need
to restrict the number of events in any given stream.

Even though only the CloudWatch appender made use of rotation, support for it had to be
built into the lowest level of the appender implementation. This increased maintenance
and testing for the entire library. It also introduced an operational bottleneck, as the
"should I rotate" test happened inside a critical section.

Given these points, I've decided to remove it entirely.

This means that the following configuration parameters are no longer supported. You must
remove them from your configuration, or you'll get an appender configuration error (the
practical effect of that error -- whether it disables the logger or not -- depends on the
framework you're using).

 * `rotationMode`
 * `rotationInterval`
 * `sequence`

In addition, the `{sequence}` substitution variable is no longer relevant. I have decided
to continue supporting it, to avoid breaking any external software that relies on the
format of a group or stream name, but the value will always be zero.


# CloudWatch Logs now sets `dedicatedWriter` true by default

In the initial deployment, we had multiple instances of the same service sending logs to the
same destination (again, due to limited search capabilities). This required each writer to
retrieve the latest [sequence token](https://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_PutLogEvents.html#CWL-PutLogEvents-request-sequenceToken)
before calling `PutLogEvents`, which it did by calling `DescribeLogStream`. This latter call
is limited to 5 transactions per second (with a burst allotment), with the result that a [large
deployment would see throttling errors](https://github.com/kdgregory/log4j-aws-appenders/issues/89).

I originally implemented the default value of this flag as `false`, for consistency with legacy
behavior. However, the appender will properly retrieve the sequence token, even if the property
is set to `true` (it's simply less efficient, as it must try its cached value and then retry).

Given the improvements to search, I don't think there's any good reason for multiple appenders to
write to the same stream. Therefore, rather than force all people to explicitly set the flag to
`true`, I'm defaulting it to that.
