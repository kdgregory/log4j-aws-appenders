# JSON Access Layout

This layout transforms Logback's `IAccessEvent` into a JSON format, so that it can be delivered via
data pipeline to Elasticsearch. Like [JsonLayout](jsonlayout.md), you can attach metadata to the
rendered event, allowing you to easily classify log messages. It provides additional features that
support HTTP requests, such as the ability to include parameters or headers (or filter them out, to
avoid logging sensistive information).


## Configuration

The complete list of properties is as follows (also available in the JavaDoc). Boolean properties are disabled if
not specified, explicitly disabled with the case-insensitive value "false", and explicitly enabled with the
case-insensitive value "true".

 Name               | Type      | Description
--------------------|-----------|----------------------------------------------------------------------------------------------------------------
`appendNewlines`    | Boolean   | If "true", a newline will be appended to each record (default is false). This is useful when sending logging output to a file, particularly one read by an agent.
`enableInstanceId`  | Boolean   | If "true", the JSON will include the EC2 instance ID where the application is running. This is retrieved from EC2 metadata, and will delay application startup if it's not running on EC2.
`enableHostname`    | Boolean   | If "true", the JSON will include the name of the machine where the application is running, retrieved from the Java runtime. This is often a better choice than instance ID.
`tags`              | String    | If present, the JSON will include a sub-object with specified user metadata. See below for more information.


## Data

The generated JSON object will have the following properties, some of which are optional:

 Key            | Value
----------------|------------------------------------------------------------------------------------------------------------------------
 `timestamp`    | The date/time that the message was logged, formatted as an [ISO-8601](https://en.wikipedia.org/wiki/ISO_8601) timestamp with milliseconds (example: `2017-10-15T23:19:02.123Z`)


## Metadata

The `tags` property is intended to provide metadata for search-based log analysis. It is specified using
a comma-separated list of `NAME=VALUE` pairs, and results in the creation of a `tags` sub-object in the log
message (see example below). Values may include [substitutions](substitutions.md), which are evaluated when
the layout is instantiated.


## Example

