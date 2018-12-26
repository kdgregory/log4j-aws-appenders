This module contains the appender implementations for Log4J 1.x.

The "public" classes in `com.kdgregory.log4j.aws` implement configuration options and a method
called by `AbstractAppender` to prepare the writer config. This latter class is where the work
occurs (along with handling configuration options that are shared between appenders).

Appenders are initialized by the first call to `append()`; at this point all configuration is
assumed to be complete (although some properties may be reconfigured after initialization).

Because that first call may happen on multiple threads at the same time, initialization is
synchronized. Writer rotation is logically re-initialization, and is synchronized with the
same lock.

Within the append code there is another lock, primarily intended to ensure that two threads
do not trigger concurrent writer rotation (I could have reused the initialization lock, but
wanted to clearly delineate the scope of each lock).

In normal usage there should be no contention for either of these locks: Log4J invokes
appenders within its own synchronized block. However, it appears to me that loggers from
different parts of the tree will not be synchronized.
