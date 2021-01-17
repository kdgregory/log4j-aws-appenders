This module contains the AWS writers and supporting code that's indepent of the logging
framework. For each supported destination you'll find the following classes:

* a `Writer` that does the work.
* a `WriterConfig` that contains configuration information specific to the writer. It
  is populated by the appender.
* a `WriterFactory` that creates instances of the writer. This exists so that (1) the
  appenders can use a shared abstract superclass that doesn't need to know anything
  about the actual writer, and (2) tests can easily substitute a mock writer.
* a `WriterStatistics` object and `WriterStatisticsMXBean` interface that allow the
  writer to report its configuration and status via JMX.

All writers are based on `AbstractLogWriter`, which has two responsibilities:

* Initializing the writer, which involves creating the AWS service client and then
  calling out to the subclass to ensure that the destination is available (which
  may involve creating it).
* Aggregating batches of messages and then calling out to the subclass to send them.

Since initialization happens in the abstract superclass, we use a factory to create
the AWS service client. We explicitly defer client creation until after construction
so that it can make use of whatever subclass-specific features it needs. This also
allows us more control over exception handling: as a general rule, constructors in
this library are not allowed to throw unless they're provided invalid arguments.
