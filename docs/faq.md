## What's the difference between version 1 and version 2

  Version 1 was a single JAR that supported only Log4J 1.2. Version 2 split the library
  into two parts: a "core" JAR contains all code that interacts with AWS, and a "framework"
  JAR that supports a particular logging framework. Version 2.0.0 provides the same
  functionality as version 1.3.0, but makes a slight change to JMX integration.

## Isn't Log4J 1.x at end of life?

  Yes, but it's a stable logging package that's still in use at most of the places I
  have worked. Together with SLF4J, it provides most of the features that you might
  want from a logger. Since replacing a stable framework is pretty low on the priority
  list for most development organizations, I expect it to be around for many more years.

  That said, version 2 exists so that I can support other logging frameworks. I plan to
  start with Logback, since that's used by Spring.

## There are other appender libraries, why did you write this?

  Reinventing wheels can be a great spur to creativity. It also gives me a deeper
  understanding of the services involved, which is a Good Thing. And of course I've
  added features that I didn't find elsewhere.

## What's with client builders vs contructors?

  Starting with release 1.11.16, Amazon introduced client builders; with the 1.11.84
  release they started deprecating the client constructors. For more information on
  the builders and why Amazon thinks they're a good thing, read
  [this](https://aws.amazon.com/blogs/developer/client-constructors-now-deprecated/).
  The main difference for the appenders library is that the constructors always use
  region `us-east-1`, while the client builders use a region provider chain.

  If you're using a version of the SDK that supports client builders, the appenders will
  invoke them rather than the client constructor. If you're not, or want more control over
  the client configuration (such as running in `us-east-2` but writing logs to `us-east-1`),
  you have the option to provide an application-specific client factory. Alternatively, you
  can specify an explicit service endpoint, or use the `AWS_REGION` environment variable.

  For more information, read [this](service-client.md).

## What happens when the appender has an error?

  Each appender maintains an internal message queue, and will attempt to resend messages
  until that queue fills up. What happens after the queue fills depends on the [discard
  policy](design.md#message-discard) that you've chosen; by default they drop the
  oldest messages.

  All misbehaviors get logged using the Log4J internal logger. To see messages from this
  logger, set the system property `log4j.configDebug=true` (note: the internal logger
  always writes messages to StdErr, so you must have a console or redirect to see them).
  You can also enable [JMX](jmx.md), which will let you see the most recent error (if any)
  along with the time it happened and exception stack trace (if any).

## Is there any way to see how the appenders are configured in a running program?

  This is also available via [JMX](jmx.md).

## What are all these messages from `com.amazonaws` and `org.apache.http`?

  These happen because the AWS library and its dependencies do their own logging, and
  you attached the AWS appenders to your root logger.

  There are two solutions: the first is to use the appender only with your program's
  classes:

    log4j.logger.com.myprogram=DEBUG, cloudwatch

  Or alternatively, shut off logging for those packages that you don't care about.

    log4j.logger.org.apache.http=ERROR
    log4j.logger.com.amazonaws=ERROR

  My preference is to attach the CloudWatch appender only to classes I care about (which
  may include third-party libraries), and use the built-in `ConsoleAppender` as the root
  logger.

## When I undeploy my application from Tomcat I see error messages about threads that have failed to stop. Why does this happen and how do I fix it?

   The AWS appenders use a background thread to perform their AWS calls, and this thread
   will remain running until the Log4J framework is explicitly shut down. To make this
   happen, you will need to add a context listener to your web-app, as described
   [here](tomcat.md).

## Can I contribute?

  At this point I haven't thought through the issues with having other contributors (and,
  to be honest, I'm concerned about adding code that has any legal encumbrances). Please
  add issues for any bugs or enhancement requests, and I'll try to get them resolved as
  soon as possible.

## How can I get help with any problems?

  Feel free to raise an issue here. I check in at least once a week, and will try to give
  whatever help and advice I can. Issues that aren't bugs or feature requests will be
  closed within a few weeks.

  Alternatively you can post on [Stack Overflow](https://stackoverflow.com/), which I also
  check once a week. If you do this, flag the post with `@kdgregory` otherwise I'm unlikely
  to see it.

