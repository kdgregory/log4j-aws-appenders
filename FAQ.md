## What's the difference between major versions?

  Version 1 was a single JAR that supported only Log4J 1.2. Version 2 split the library
  into two parts: a "core" JAR contains all code that interacts with AWS, and "framework"
  JARs that support different logging frameworks. Version 3 added support for the AWS
  "version 2" SDK (but still supports version 1!). I don't foresee a version 4.


## Isn't Log4J 1.x at end of life?

  Yes, but it's a stable logging package that's still in use at most of the places I
  have worked. Together with SLF4J, it provides most of the features that you might
  want from a logger. Since replacing a stable framework is pretty low on the priority
  list for most development organizations, I expect it to be around for many more years.

  That said, if you're uncomfortable with using an end-of-lifed logging framework, this
  library also supports Logback (my preference) and Log4J 2.x


## What about java.util.logging?

  I have no plans to support java.util.logging. If you're using it, I recommend that you
  use the [jul-to-slf4j bridge](https://www.slf4j.org/legacy.html) with a more-capable
  underlying framework.


## What happens when an appender has an error?

  Each appender maintains an internal message queue, and will attempt to resend messages
  until that queue fills up. What happens after the queue fills depends on the [discard
  policy](docs/design.md#message-discard) that you've chosen; by default they drop the
  oldest messages.

  All misbehaviors get logged using the framework's internal logger. See the [troubleshooting
  guide](docs/troubleshooting.md) for information on how to enable it.

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


## Why am I getting java.lang.NoClassDefFoundError?

   This indicates that you're missing a required JAR on your classpath. It could
   also mean that you're using the wrong "facade" JAR for your AWS SDK. See the
   [troubleshooting guide](docs/troubleshooting.md#noclassdeffounderror) for more
   explanation and an example.


## I'm running on Lambda, why don't I see any log messages?

   This happens because the appenders use a background thread to batch messages, and [Lambda
   doesn't give that thread a chance to run](https://blog.kdgregory.com/2019/01/multi-threaded-programming-with-aws.html).
   You can enable [synchronous mode](docs/design.md#synchronous-mode) to mitigate this, but
   be aware that it will slow down foreground execution and does not guarantee delivery.

   When running on Lambda, I think that the best approach is to use the built-in logging
   support, which writes logs to CloudWatch. If you use a JSON layout, they will be easily
   searchable with CloudWatch Logs Insights. And you can use a subscription to [stream the
   logs to Elasticsearch](https://blog.kdgregory.com/2019/09/streaming-cloudwatch-logs-to.html).


## [Dependabot](https://dependabot.com/) says you have dependencies with critical vulnerabilities!

   This project targets older versions of various libraries, and those versions are often the subject
   of Dependabot vulnerability reports. However, since this project does not define any transitive
   dependencies, _it will not introduce these vulnerabilities into your application._ You should, of
   course, regularly update your dependencies; just because this library _can_ be used with the
   1.11.233 AWS SDK doesn't mean that it should.


## Can I contribute?

  At this point I haven't thought through the issues with having other contributors (and,
  to be honest, I'm concerned about adding code that has any legal encumbrances). Please
  add issues for any bugs or enhancement requests, and I'll try to get them resolved as
  soon as possible.


## How can I get help with any problems?

  First, check out the [troubleshooting doc](docs/troubleshooting.md). It has examples and
  answers for most of the problems that have been seen "in the wild."

  If that doesn't help, free to raise an issue here. I check in at least once a week, and
  will give whatever help and advice I can. Issues that aren't bugs or feature requests will
  be closed after I answer.

  Alternatively you can post on [Stack Overflow](https://stackoverflow.com/). However, I don't
  often log in there, so flag the post with `@kdgregory` to increase the chance I'll see it.
