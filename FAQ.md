## What's the difference between version 1 and version 2

  Version 1 was a single JAR that supported only Log4J 1.2. Version 2 split the library
  into two parts: a "core" JAR contains all code that interacts with AWS, and "framework"
  JARs that support different logging frameworks.


## Isn't Log4J 1.x at end of life?

  Yes, but it's a stable logging package that's still in use at most of the places I
  have worked. Together with SLF4J, it provides most of the features that you might
  want from a logger. Since replacing a stable framework is pretty low on the priority
  list for most development organizations, I expect it to be around for many more years.

  That said, if you're uncomfortable with using and end-of-lifed logging framework,
  version 2.1.0 supports Logback and version 2.3.0 supports Log4J 2.x.


## What about java.util.logging?

  I have no plans to support java.util.logging. If you're using it, I recommend that you
  use the [jul-to-slf4j bridge](https://www.slf4j.org/legacy.html) with a more-capable
  underlying framework.


## What's with client builders vs contructors?

  Starting with release 1.11.16, the AWS SDK introduced builders for service clients; with
  the 1.11.84 release it started deprecating the client constructors. For more information
  on the builders and why Amazon thinks they're a good thing, read
  [this](https://aws.amazon.com/blogs/developer/client-constructors-now-deprecated/).
  The main difference for the appenders library is that the constructors always use
  region `us-east-1`, while the client builders use a region provider chain.

  If you're using a version of the SDK that supports client builders, the appenders will
  invoke them rather than the client constructor. If you're not, or want more control over
  the client configuration (such as running in `us-east-2` but writing logs to `us-east-1`),
  the appenders give you several options; read [this](docs/client.md).


## What happens when the appender has an error?

  Each appender maintains an internal message queue, and will attempt to resend messages
  until that queue fills up. What happens after the queue fills depends on the [discard
  policy](docs/design.md#message-discard) that you've chosen; by default they drop the
  oldest messages.

  All misbehaviors get logged using the framework's internal logger. See the [troubleshooting
  doc](docs/troubleshooting.md) for information on how to enable it.

  You can also enable [JMX](docs/jmx.md), which will let you see the most recent error (if
  any) along with the time it happened and exception stack trace (if any) using a tool like
  JConsole.


## Is there any way to see how the appenders are configured in a running program?

  This is also available via [JMX](docs/jmx.md); the specific configuration steps
  depend on your logging framework.


## What are all these messages from `com.amazonaws` and `org.apache.http`?

  These happen because the AWS library and its dependencies do their own logging, and
  you attached the AWS appenders to your root logger.

  There are two solutions: the first is to use the appender only with your program's
  classes:

```
log4j.logger.com.myprogram=DEBUG, cloudwatch
```

  Or alternatively, shut off logging for those packages that you don't care about.

```
log4j.logger.org.apache.http=ERROR
log4j.logger.com.amazonaws=ERROR
```

  My preference is to attach the CloudWatch appender only to classes I care about (which
  may include third-party libraries), and use the built-in `ConsoleAppender` as the root
  logger.


## When I undeploy my application from Tomcat I see error messages about threads that have failed to stop. Why does this happen and how do I fix it?

   The AWS appenders use a background thread to perform their AWS calls, and this thread
   will remain running until the Log4J framework is explicitly shut down. To make this
   happen, you will need to add a context listener to your web-app, as described
   [here](docs/tomcat.md).


## I'm running on Lambda, why don't I see any log messages?

   This happens because the appenders use a background thread to batch messages, and [Lambda
   doesn't give that thread a chance to run](https://blog.kdgregory.com/2019/01/multi-threaded-programming-with-aws.html).
   You can enable [synchronous mode](docs/design.md#synchronous-mode) to mitigate this, but
   be aware that it will slow down foreground execution and does not guarantee delivery.

   When running on Lambda, I think that the best approach is to use the built-in logging
   support, which writes logs to CloudWatch. If you use a JSON layout, they will be easily
   searchable with CloudWatch Logs Insights. And you can use a subscription to [stream the
   logs to Elasticsearch](https://blog.kdgregory.com/2019/09/streaming-cloudwatch-logs-to.html).


## Can I contribute?

  At this point I haven't thought through the issues with having other contributors (and,
  to be honest, I'm concerned about adding code that has any legal encumbrances). Please
  add issues for any bugs or enhancement requests, and I'll try to get them resolved as
  soon as possible.


## How can I get help with any problems?

  Feel free to raise an issue here. I check in at least once a week, and will try to give
  whatever help and advice I can. Issues that aren't bugs or feature requests will be
  closed after I answer, but you can still comment.

  Alternatively you can post on [Stack Overflow](https://stackoverflow.com/), although I
  don't often log in there. If you do this, flag the post with `@kdgregory` otherwise I'm
  unlikely to see it.
