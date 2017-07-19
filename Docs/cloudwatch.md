# CloudWatch Logs

The CloudWatch implementation provides (will provide) the following features:

* [x] User-specified log-group and log-stream names
* [x] Substitution variables to customize log-group and log-stream names
* [x] Auto-rotation of log streams, either fixed-delay or hourly/daily
* [ ] Configurable discard in case of network connectivity issues


Your Log4J configuration will look something like this:

		log4j.rootLogger=ERROR, default
		log4j.logger.com.kdgregory.log4j.aws.TestCloudwatchAppender=DEBUG
		
		log4j.appender.default=com.kdgregory.log4j.aws.CloudWatchAppender
		log4j.appender.default.layout=org.apache.log4j.PatternLayout
		log4j.appender.default.layout.ConversionPattern=%d [%t] %-5p %c %x - %m%n
		
		log4j.appender.default.logGroup={sysprop:APP_NAME}
		log4j.appender.default.logStream={date}-{pid}
		log4j.appender.default.batchSize=50
		log4j.appender.default.batchTimeout=1000
        log4j.appender.default.rotationMode=daily


The appender provides the following properties (also described in the JavaDoc):

Name                | Description
--------------------|----------------------------------------------------------------
`logGroup`          | Name of the Cloudwatch log group where messages are sent; may use substitutions. If this group doesn't exist it will be created. No default.
`logStream`         | Name of the Cloudwatch log stream where messages are sent; may use substitutions. Defaults to `{startTimestamp}`.
`batchSize`         | Maximum number of messages that will be queued in the appender before passing them to the writer.
`maxDelay`          | Maximum time, in milliseconds, that messages will be queued in the appender. This ensures that low-volume loggers will regularly write something.
`rotationMode`      | Controls whether auto-rotation is enabled. Values are `none`, `interval`, `hourly`, and `daily`. Default is `none`, which means the stream will be written forever (unless explicitly rotated via the `rotate()` method).
`rotationInterval`  | For interval rotation only; the number of milliseconds between automatic rotations.
`sequence`          | A value that is incremented each time the stream is rotated. Defaults to 0 and typically won't be changed.
