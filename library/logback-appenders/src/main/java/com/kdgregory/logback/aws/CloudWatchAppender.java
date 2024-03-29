// Copyright (c) Keith D Gregory
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.kdgregory.logback.aws;

import java.util.Date;

import com.kdgregory.logback.aws.internal.AbstractAppender;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchConstants;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterFactory;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatisticsMXBean;
import com.kdgregory.logging.aws.common.Substitutions;
import com.kdgregory.logging.common.util.DefaultThreadFactory;


/**
 *  An appender that writes to a CloudWatch log stream.
 *  <p>
 *  This appender supports the following configuration parameters:
 *  <p>
 *  <table>
 *  <tr VALIGN="top">
 *      <th> logGroup
 *      <td> Name of the CloudWatch log group where messages are sent; may use
 *           substitutions. If this group doesn't exist it will be created.
 *           <p>
 *           You typically assign a single log group to an application, and then
 *           use multiple log streams for instances of that application.
 *           <p>
 *           There is no default value. If you do not configure the log group,
 *           the appender will be disabled and will report its misconfiguration.
 *
 *  <tr VALIGN="top">
 *      <th> logStream
 *      <td> Name of the CloudWatch log stream where messages are sent; may use
 *           substitutions. If this stream doesn't exist it will be created.
 *           <p>
 *           You typically create a separate log stream for each instance of
 *           the application.
 *           <p>
 *           Default value is <code>{startTimestamp}</code>, the JVM startup
 *           timestamp.
 *
 *  <tr VALIGN="top">
 *      <th> retentionPeriod
 *      <td> (optional) Specifies a retention period for created CloudWatch log
 *           groups. If omitted, messages are retained forever. Note that values
 *           are restricted; see {@link <a href="https://docs.aws.amazon.com/AmazonCloudWatchLogs/latest/APIReference/API_PutRetentionPolicy.html#CWL-PutRetentionPolicy-request-retentionInDays">AWS API doc</a>}.
 *
 *  <tr VALIGN="top">
 *      <th> dedicatedWriter
 *      <td> If true (the default), the appender assumes that it is the only thing
 *           writing to the log stream, and does not fetch a sequence token before
 *           each write. This improves performance and reduces the likelihood of
 *           throttling when there are a large number of processes (as long as they
 *           write to different streams). If you need to have multiple appenders
 *           writing to the same stream, set this to false.
 *
 *  <tr VALIGN="top">
 *      <th> synchronous
 *      <td> If true, the appender will operate in synchronous mode: calls to
 *           append() execute on the caller's thread, and will not return until
 *           writer has attempted to send a batch (errors may still result in
 *           messages being requeued for the next batch). This negates the benefits
 *           of message batching, and should only be used in cases (like AWS Lambda)
 *           where background thread processing is not guaranteed.
 *          <p>
 *          Note: setting <code>synchronous</code> to true also sets
 *          <code>batchDelay</code> to 0.
 *
 *  <tr VALIGN="top">
 *      <th> batchDelay
 *      <td> The time, in milliseconds, that the writer will wait to accumulate
 *           messages for a batch.
 *           <p>
 *           The writer attempts to gather multiple logging messages into a batch,
 *           to reduce communication with the service. The batch delay controls
 *           the time that a message will remain in-memory while the writer builds
 *           this batch. In a low-volume environment it will be the main determinant
 *           of when the batch is sent; in a high volume environment it's likely
 *           that the maximum request size will be reached before the delay elapses.
 *           <p>
 *           The default value is 2000, which is rather arbitrarily chosen.
 *           <p>
 *           If the appender is in synchronous mode, this setting is ignored.
 *
 *  <tr VALIGN="top">
 *      <th> truncateOversizeMessages
 *      <td> If <code>true</code> (the default), oversize messages are truncated to
 *           the maximum length permitted by CloudWatch Logs. If <code>false</code>
 *           they are discarded. In either case, the oversized message is reported
 *           to the Log4J debug log.
 *
 *  <tr VALIGN="top">
 *      <th> discardThreshold
 *      <td> The number of unsent messages that will trigger message discard. A
 *           high value is useful when network connectivity is intermittent and/or
 *           overall AWS communication is causing throttling. However, a value that
 *           is too high may cause out-of-memory errors.
 *           <p>
 *           The default, 10,000, is based on the assumptions that (1) each message
 *           will be 1k or less, and (2) any app that uses remote logging can afford
 *           10MB.
 *
 *  <tr VALIGN="top">
 *      <th> discardAction
 *      <td> The action to take when the number of unsent messages exceeds the
 *           discard threshold. Values are "none" (retain all messages), "oldest"
 *           (discard oldest messages), and "newest" (discard most recent messages).
 *           <p>
 *           The default is "oldest". Attempting to set an incorrect value will throw
 *           a configuration error.
 *
 *  <tr VALIGN="top">
 *      <th> assumedRole
 *      <td> Specifies role name or ARN that will be assumed by this appender. Useful
 *           for cross-account logging. If the appender does not have permission to
 *           assume this role, initialization will fail.
 *
 *  <tr VALIGN="top">
 *      <th> clientFactory
 *      <td> The fully-qualified name of a static method to create the correct AWS
 *           client, which will be called instead of the writer's internal client
 *           factory. This is useful if you need non-default configuration, such as
 *           using a proxy server.
 *           <p>
 *           The passed string is of the form <code>com.example.Classname.methodName</code>.
 *           If this does not reference a class/method on the classpath then writer
 *           initialization will fail.
 *
 *  <tr VALIGN="top">
 *      <th> clientRegion
 *      <td> Specifies a non-default service region. This setting is ignored if you
 *           use a client factory.
 *
 *  <tr VALIGN="top">
 *      <th> clientEndpoint
 *      <td> Specifies a non-default service endpoint. Typically used when running in
 *           a VPC, when the normal endpoint is not available.
 *
 *  <tr VALIGN="top">
 *      <th> initializationTimeout
 *      <td> Milliseconds to wait for appender to initialize. If this timeout expires,
 *           the appender will shut down its writer thread and discard any future log
 *           events. The only reason to change this is if you're deploying to a high-
 *           contention environment (and even then, the default of 60 seconds should be
 *           more than enough).
 *
 *  <tr VALIGN="top">
 *      <th> useShutdownHook
 *      <td> Controls whether the appender uses a shutdown hook to attempt to process
 *           outstanding messages when the JVM exits. This is true by default; set to
 *           false to disable.
 *  </table>
 *
 *  @see <a href="https://github.com/kdgregory/log4j-aws-appenders/blob/master/docs/cloudwatch.md">Appender documentation</a>
 */
public class CloudWatchAppender<LogbackEventType>
extends AbstractAppender
    <
    CloudWatchWriterConfig,
    CloudWatchWriterStatistics,
    CloudWatchWriterStatisticsMXBean,
    LogbackEventType
    >
{
    public CloudWatchAppender()
    {
        super(new CloudWatchWriterConfig(),
              new DefaultThreadFactory("logback-cloudwatch"),
              new CloudWatchWriterFactory(),
              new CloudWatchWriterStatistics(),
              CloudWatchWriterStatisticsMXBean.class);
    }

//----------------------------------------------------------------------------
//  Configuration
//----------------------------------------------------------------------------

    /**
     *  Sets the <code>logGroup</code> configuration property.
     */
    public void setLogGroup(String value)
    {
        appenderConfig.setLogGroupName(value);
    }


    /**
     *  Returns the <code>logGroup</code> configuration property.
     */
    public String getLogGroup()
    {
        return appenderConfig.getLogGroupName();
    }


    /**
     *  Sets the <code>logStream</code> configuration property.
     */
    public void setLogStream(String value)
    {
        appenderConfig.setLogStreamName(value);
    }


    /**
     *  Returns the <code>logStream</code> configuration property.
     */
    public String getLogStream()
    {
        return appenderConfig.getLogStreamName();
    }


    /**
     *  Sets the <code>retentionPeriod</code> configuration property.
     */
    public void setRetentionPeriod(int value)
    {
        appenderConfig.setRetentionPeriod(CloudWatchConstants.validateRetentionPeriod(value));
    }


    /**
     *  Returns the <code>retentionPeriod</code> configuration property,
     *  null to indicate unlimited retention.
     */
    public Integer getRetentionPeriod()
    {
        return appenderConfig.getRetentionPeriod();
    }


    /**
     *  Sets the <code>dedicatedWriter</code> configuration property.
     */
    public void setDedicatedWriter(boolean value)
    {
        appenderConfig.setDedicatedWriter(value);
    }


    /**
     *  Returns the <code>dedicatedWriter</code> configuration property.
     */
    public boolean getDedicatedWriter()
    {
        return appenderConfig.getDedicatedWriter();
    }

//----------------------------------------------------------------------------
//  AbstractAppender overrides
//----------------------------------------------------------------------------

    @Override
    protected CloudWatchWriterConfig generateWriterConfig()
    {
        Substitutions subs     = new Substitutions(new Date(), 0);
        String actualLogGroup   = subs.perform(getLogGroup());
        String actualLogStream  = subs.perform(getLogStream());

        return ((CloudWatchWriterConfig)appenderConfig.clone())
               .setLogGroupName(actualLogGroup)
               .setLogStreamName(actualLogStream);
    }
}
