This module contains the appender implementations for Logback

The "public" classes in `com.kdgregory.log4j.logback` implement configuration options and a method
called by `AbstractAppender` to prepare the writer config. This latter class is where the work
occurs (along with handling configuration options that are shared between appenders).

Appenders are explicitly initialized by Logback, after configuration but before the first
message is written. I have not researched whether Logback ensures that this happens within
a synchronized block, but have retained the initialization lock from the Log4J implementation.

As with the Log4J appender, the append operation uses another lock for its critical path. This
is particularly important here because the appenders are built on `UnsynchronizedAppenderBase`
and may be invoked concurrently.
