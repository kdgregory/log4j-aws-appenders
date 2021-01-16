# Design

These are the primary design constraints for this library:

* We may receive logging events on any thread, and don't want to block the thread while communicating
  with the service.
* Services may reject our requests, either temporarily or permanently.
* We generally want to batch individual messages to improve throughput, respecting any limits on
  number of messages or bytes that are imposed by the service.
* The amount of time taken to build a batch should be controllable by the user, as a trade-off
  between efficiency and potential for lost messages.


## Message Queue and Writer Thread

To meet these constraints, the appender creates a separate thread for communication with the service,
along with a concurrent queue to hold appended messages. When the logging framework calls `append()`,
the appender converts the passed event into a string, verifies that it conforms to limitations imposed
by the service, and adds it to the queue.

The writer runs on a separate thread (created when the appender is initialized), reading that queue
and attempting to batch together messages into a single request. Once it has a batch (based either
on size or a configurable timeout) it attempts to write the entire batch to the service.

The writer thread handles most exceptions internally, reporting them via [JMX](jmx.md) and requeing
messages for a later retry (this is in addition to any retries handled within the AWS SDK).


## Message Discard

One of the drawbacks of retrying messages is that an unbounded queue can consume all of memory.
To avoid such errors, the message queue has a maximum size, configured by the `discardThreshold`
parameter. How the queue behaves once full is further configured by the `discardAction` parameter:

* `oldest`

  The oldest message in the queue is discarded. This is the default, as it allows you to
  track the current behavior of the application if/when the failure condition is resolved.

* `newest`

  The newest message in the queue is discarded. This is useful if you want to see what
  was happening at the time the failure condition occured.

* `none`

  No messages are discarded. If you expect intermittent connectivity problems, have lots of
  memory, and don't want to miss any logging then this option may be reasonable. However, it's
  probably better to increase the threshold and use one of the other discard actions.

The default threshold is 10,000 messages. Assuming 1kb per message, that's roughly 10MB of heap
that can be used by the queue. 


## Message Batches

Most AWS services allow batching of messages for efficiency. While sending maximum-sized requests is
more efficient when there's a high volume of logging, it would cause an excessive delay if there's a
low volume. And it will leave more messages unwritten if the program shuts down without waiting for
all messages to be sent.

The user can control message batching via the `batchDelay` configuration variable, which specifies
the number of milliseconds that the writer will wait after dequeueing the first message in a batch.
The writer sends the batch either once the timer expires or the service-defined batch size limit is
reached. Then it blocks on the queue, waiting for a message to start the next batch.

The default value, 2000, is intended as a tradeoff between keeping the log up to date and reducing
the likelihood of throttling. For long-running applications this default should be fine, but for
applications that only run for a few seconds it may cause message loss (note that the appenders
add a shutdown hook to avoid this). For such applications it makes sense to reduce the batch size
to maybe 250 milliseconds, but beware that increasing the message rate may result in throttling
by the service. Which may then _increase_ the delay between the time that the application logs an
event and the time that event is delivered to its destination.

If you absolutely, positively cannot lose messages, you should use a different appender. But beware:
even the standard `FileAppender` is not guaranteed to save all messages, because file writes are
buffered in memory before they're actually written to the disk.


## Synchronous Mode

While batching and asynchronous delivery is the most efficient way to send messages, it is not
appropriate when the background thread does not have the opportunity to run, as with a [short-duration
Lambda](http://blog.kdgregory.com/2019/01/multi-threaded-programming-with-aws.html). To support that
use-case, the appenders offer "synchronous" mode, enabled by setting the `synchronous` configuration
parameter to `true`. When enabled, each call to `append()` attempts to send the message immediately,
using the invoking thread.

While useful for specific situations, _synchronous mode is not intended as the default_. In addition
to slowing down the invoking thread (perhaps significantly), it _does not guarantee delivery_. There
is still the possibility of an exception during the send, which will requeue the message(s) for later
deliver (which might never happen).


## Shutdown

When the logging framework is shut down (or an indivudal appender is explicitly stopped), the
log-writer attempts to send one last batch. The configured `batchDelay` is the maximum amount
of time that it will wait to build that batch, and any failures are not retried.

A bigger issue is the JVM shutting down without first shutting down the logging framework. Since the
log-writer runs on a daemon thread, it would not normally get a chance to send any queued messages.
This is a particular issue with short-running applications.

To avoid this problem, by default the Log4J1 and Logback appenders install a
[shutdown hook](https://docs.oracle.com/javase/8/docs/api/java/lang/Runtime.html#addShutdownHook)
when they start the writer thread. This hook calls the writer's `stop()` method, and then
joins to the writer thread, delaying shutdown until that thread finishes (which will take
`batchDelay` milliseconds).

> Note: Log4J2 has its own shutdown hook, and the appenders leverage it. While the configuration
  option is retained for consistency, it is ignored.

When the shutdown hook is enabled, the JVM will not shut down until the final batch is sent (or it's
hard-killed). This means that the main thread can continue running unexpectedly (ie, it's in a loop
and the program was killed with `kill -15` or Ctrl-C).

If you do not want this shutdown hook, you can set the `useShutdownHook` configuration parameter
to `false`. Beware that doing so means you might lose messages.
