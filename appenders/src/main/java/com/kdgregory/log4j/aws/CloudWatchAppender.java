// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.util.Date;

import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchConstants;
import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchLogWriter;
import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.log4j.aws.internal.shared.AbstractAppender;
import com.kdgregory.log4j.aws.internal.shared.DefaultThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.Substitutions;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;


/**
 *  Appender that writes to a CloudWatch log stream.
 */
public class CloudWatchAppender extends AbstractAppender<CloudWatchWriterConfig>
{
    // these are the only configuration vars specific to this appender

    private String          logGroup;
    private String          logStream;

    // these variables hold the post-substitution log-group and log-stream names
    // (mostly useful for testing)

    private String  actualLogGroup;
    private String  actualLogStream;


    /**
     *  Base constructor: assigns default values to configuration properties.
     */
    public CloudWatchAppender()
    {
        super(new DefaultThreadFactory(),
              new WriterFactory<CloudWatchWriterConfig>()
                  {
                        @Override
                        public LogWriter newLogWriter(CloudWatchWriterConfig config)
                        {
                            return new CloudWatchLogWriter(config);
                        }
                   });

        logStream = "{startupTimestamp}";
    }


//----------------------------------------------------------------------------
//  Configuration
//----------------------------------------------------------------------------

    /**
     *  Sets the CloudWatch Log Group associated with this appender.
     *  <p>
     *  You typically assign a single log group to an application, and then
     *  use multiple log streams for instances of that application.
     *  <p>
     *  There is no default value. If you do not configure the log group, the
     *  appender will be disabled and will report its misconfiguration.
     */
    public void setLogGroup(String value)
    {
        logGroup = value;
    }


    /**
     *  Returns the log group name; see {@link #setLogGroup}. Primarily used
     *  for testing.
     */
    public String getLogGroup()
    {
        return logGroup;
    }


    /**
     *  Sets the CloudWatch Log Stream associated with this appender.
     *  <p>
     *  You typically create a separate log stream for each instance of the
     *  application.
     *  <p>
     *  Default value is <code>{startTimestamp}</code>, the JVM startup timestamp.
     */
    public void setLogStream(String value)
    {
        logStream = value;
    }


    /**
     *  Returns the log stream name; see {@link #setLogStream}. Primarily used
     *  for testing.
     */
    public String getLogStream()
    {
        return logStream;
    }


//----------------------------------------------------------------------------
//  Appender-specific methods
//----------------------------------------------------------------------------

    /**
     *  Rotates the log stream: flushes all outstanding messages to the current
     *  stream, and opens a new stream. This is called internally, and exposed
     *  for testing.
     */
    @Override
    public void rotate()
    {
        super.rotate();
    }


//----------------------------------------------------------------------------
//  Subclass hooks
//----------------------------------------------------------------------------

    @Override
    protected CloudWatchWriterConfig generateWriterConfig()
    {
        Substitutions subs = new Substitutions(new Date(), sequence.get());
        actualLogGroup  = CloudWatchConstants.ALLOWED_NAME_REGEX.matcher(subs.perform(logGroup)).replaceAll("");
        actualLogStream = CloudWatchConstants.ALLOWED_NAME_REGEX.matcher(subs.perform(logStream)).replaceAll("");
        return  new CloudWatchWriterConfig(actualLogGroup, actualLogStream, batchDelay);
    }


    @Override
    protected boolean isMessageTooLarge(LogMessage message)
    {
        return (message.size() + CloudWatchConstants.MESSAGE_OVERHEAD)  >= CloudWatchConstants.MAX_BATCH_BYTES;
    }
}
