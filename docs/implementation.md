# Implementation Notes

These may be helpful if you dig into the code ... or when I go back to the code after
six months away.

## Facades

This is the big change for the 3.0 release: to support old and new AWS SDKs (which changed
class, package, and method names, but <em>not</em> the underlying API), the log-writers
now interact with a facade class rather than the SDK.

There are currently four facade classes: `CloudWatchFacade`, `KinesisFacade`, `SNSFacade`,
and `InfoFacade`. The first three are thin wrappers around the corresponding service API;
all logic for retries and exceptional conditions is in the corresponding log-writer. The
fourth is intended to provide information about the deployment environment in a "best
effort" manner; the appenders do not depend on its operation.

All facades are instantiated via `FacadeFactory.createFacade()`, a static method that uses
reflection to determine which implementation library is linked into the application (and
which throws if none is). Since this is a static method, it can be called from any point
in the codebase (and this is leveraged by [substitutions](substitutions.md) to access the
`InfoFacade`).

The "service" facades all take an object that contains appender configuration. They use
this object to (1) figure out how to [create the service client](client.md), and (2) to
provide information such as log group/stream name to the SDK.

As noted above, the various log-writers are responsible for all logic (such as retries)
when interacting with the service; the facade is a thin wrapper that translates
SDK-specific classes into something else. In most cases, this something else is a
service-specific facade exception (eg, `CloudWatchFacadeException`); the log-writer
interprets the exception's "reason code" to decide whether the failure is something
important. In the case of describe operations, the "something else" is `null` to signify
that the thing being described doesn't exist.

I went back and forth several times on how heavyweight the facades should be. In the end,
I decided based on limiting the amount of code that would have to be duplicated to support
the v2 SDK. I think this also improved the layerability and testability of the framework.


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


## Log4J1 Initialization

Unlike Logback and Log4J2, Log4J1 does not have an appender life-cycle. This means that
the Log4J1 appenders must be initialized by the first call to `append()`. As a result,
many unit tests include a single dummy log message to trigger initialization.

In practice this means that you won't discover logging configuration problems until the
first time that you try to use a logger, which may be some time after your application
starts. As a work-around -- and a generally good practice -- log an "I'm here!" message
at the start of your `main` method.
