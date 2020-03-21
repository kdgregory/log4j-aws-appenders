# Implementation Notes

These may be helpful if you dig into the code ... or when I go back to the code after
six months away.


## Log4J1 Initialization

Unlike Logback and Log4J2, Log4J1 does not have an appender life-cycle. This means that
the Log4J1 appenders must be initialized by the first call to `append()`. As a result,
many unit tests include a single dummy log message to trigger initialization.

In practice this means that you won't discover logging configuration problems until the
first time that you try to use a logger, which may be some time after your application
starts. As a work-around -- and a generally good practice -- log an "I'm here!" message
at the start of your `main` method.


## Locking

There are two locks used by the appenders: `initializationLock`, which is called around
writer creation, and `appendLock`, which single-threads the rotation check and message
enqueue to ensure that we don't initiate rotation from two threads. In normal operation
these locks should have low contention: while the append lock is called for every append,
it covers very few instructions (message formatting, for example, happens outside the
lock).


## Writer Rotation

In retrospect, writer rotation is one of the worst features of this library. When first
designed, of course, it seemed perfectly reasonable: the library supported only CloudWatch
Logs, with few thoughts of other destinations, and in those pre-Insight days long log
streams were a pain to review.

The main problem with rotation is that it has to tie into the appender at a very low level:
the "do we rotate?" decision has to happen every time a message is written. And this happens
in all appenders, even though it's only relevant for the CloudWatch appender. Worse, the
same code is replicated across all framework implementations.

In an attempt to minimize the cost of this action, the `shouldRotate()` method is overridden
in the Kinesis and SNS appenders. My expectation is that Hotspot will eventually inline the
`return false` (at least for Kinesis), reducing the impact of this decision. The default
implementation of this method (which again, is only relevant to CloudWatch) remains in the
abstract appender, because it needs access to the internal variables that track message
count and rotation times.
