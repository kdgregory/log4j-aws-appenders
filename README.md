# log4j-cloudwatch-appender

An appender for Log4J 1.x that writes to AWS Cloudwatch.

Wait, isn't there already one of those?

Not that I could find. Well, that's not true. I did find an appender for Log4J 2.x. And of course
there's the Amazon appender if you're running in Lambda. But I didn't find one for 1.x, which is
still what I use in most projects. Perhaps most people use the CloudWatch agent?

## Usage

## Building

There are two child projects in this repository:

* `appender` is the actual appender code.
* `test` is a set of integration tests. These are in a separate module so that they can be run as
  desired, rather than as part of every build.
