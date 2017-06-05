# CloudWatch Logs

The CloudWatch implementation provides (will provide) the following features:

  [x] User-specified log-group and log-stream names
  [x] Substitution variables to customize log-group and log-stream names
  [x] Rolling log streams
  [ ] Configurable discard in case of network connectivity issues


Your Log4J configuration should look something like this:

		log4j.rootLogger=ERROR, default
		log4j.logger.com.kdgregory.log4j.cloudwatch.TestCloudwatchAppender=DEBUG
		
		log4j.appender.default=com.kdgregory.log4j.cloudwatch.CloudwatchAppender
		log4j.appender.default.layout=org.apache.log4j.PatternLayout
		log4j.appender.default.layout.ConversionPattern=%d [%t] %-5p %c %x - %m%n
		
		log4j.appender.default.logGroup={sysprop:APP_NAME}
		log4j.appender.default.logStream={tTimestamp}
		log4j.appender.default.batchSize=20
		log4j.appender.default.batchTimeout=1000
		log4j.appender.default.rollInterval=86400000


The appender provides the following properties (also described in the JavaDoc, where you'll
see default values):

Name            | Description
----------------|----------------------------------------------------------------
`logGroup`      | Name of the Cloudwatch log group where messages are sent; may use substitutions. If this group doesn't exist it will be created. No default.
`logStream`     | Name of the Cloudwatch log stream where messages are sent; may use substitutions. Defaults to `{startTimestamp}`.
`batchSize`     | Maximum number of messages that will be accumulated before sending a batch.
`batchTimeout`  | Maximum time, in milliseconds, that messages will be accumulated. This ensures that low-volume loggers will actually get logged.
`rollInterval`  | Number of milliseconds that a stream will be written. Note that streams can also be rolled manually, via the `roll()` method.
