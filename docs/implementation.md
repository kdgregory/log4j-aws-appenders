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

The logging framework may or may not synchronize calls to the appender. Log4J1, for
example, uses one large synchronized block to call all appenders, while Log4J2 assumes
they'll synchronize themselves.

The appenders don't do any explicitly synchronization of the `append()` call. Instead,
they perform as much work as they can in a thread-safe manner, and then put the message
on a concurrent queue for consumption by the log writer.

The one thing that is explicitly synchronized within this library is writer creation
and shutdown, using the `initializationLock` variable. I think this may be a "belt and
suspenders" protection for Logback and Log4J2, because the library is responsible for
initializing the appender (and, one presumes, is smart enough to only do that once).
And even Log4J1, with lazily initialization, shouldn't need this protection because
of the framework's synchronization. However, leaving it in place is a low-pain way to
ensure that we don't have hard-to-diagnose issues.
