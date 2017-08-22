// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.util.Date;

import com.kdgregory.log4j.aws.internal.AbstractAppender;
import com.kdgregory.log4j.aws.internal.kinesis.KinesisConstants;
import com.kdgregory.log4j.aws.internal.kinesis.KinesisLogWriter;
import com.kdgregory.log4j.aws.internal.shared.DefaultThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.Substitutions;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;


/**
 *  Appender that writes to a Kinesis stream.
 */
public class KinesisAppender extends AbstractAppender
{
    // these are the only configuration vars specific to this appender

    private String          streamName;
    private String          partitionKey;

    // these variables hold the post-substitution log-group and log-stream names
    // (mostly useful for testing)

    private String          actualStreamName;
    private String          actualPartitionKey;


    /**
     *  Base constructor: assigns default values to configuration properties.
     */
    public KinesisAppender()
    {
        super();

        threadFactory = new DefaultThreadFactory();
        writerFactory = new WriterFactory()
                      {
                            @Override
                            public LogWriter newLogWriter()
                            {
                                return new KinesisLogWriter(getActualStreamName(), getActualPartitionKey(), getBatchDelay());
                            }
                       };

        partitionKey = "{startupTimestamp}";
    }


//----------------------------------------------------------------------------
//  Configuration
//----------------------------------------------------------------------------

    /**
     *  Sets the Kinesis Stream name associated with this appender.
     *  <p>
     *  There is no default value. If you do not configure a stream, the
     *  appender will be disabled and will report its misconfiguration.
     */
    public void setStreamName(String value)
    {
        streamName = value;
    }


    /**
     *  Returns the log group name; see {@link #setLogGroup}. Primarily used
     *  for testing.
     */
    public String getStreamName()
    {
        return streamName;
    }


    /**
     *  Returns the current log group name, post-substitutions. This is lazily
     *  populated, so will be <code>null</code> until first append.
     */
    public String getActualStreamName()
    {
        return actualStreamName;
    }


    /**
     *  Sets the partition key associated with this appender. This key is used to
     *  assign messages to shards: all messages with the same partition key will
     *  be sent to the same shard.
     *  <p>
     *  Default value is "{startupTimestamp}".
     */
    public void setPartitionKey(String value)
    {
        partitionKey = value;
    }


    /**
     *  Returns the log group name; see {@link #setLogGroup}. Primarily used
     *  for testing.
     */
    public String getPartitionKey()
    {
        return partitionKey;
    }


    /**
     *  Returns the current log group name, post-substitutions. This is lazily
     *  populated, so will be <code>null</code> until first append.
     */
    public String getActualPartitionKey()
    {
        return actualPartitionKey;
    }


//----------------------------------------------------------------------------
//  Appender-specific methods
//----------------------------------------------------------------------------


//----------------------------------------------------------------------------
//  Subclass hooks
//----------------------------------------------------------------------------

    @Override
    protected void prepareWriterFactory()
    {
        Substitutions subs = new Substitutions(new Date(), sequence.get());
        actualStreamName  = KinesisConstants.ALLOWED_NAME_REGEX.matcher(subs.perform(streamName)).replaceAll("");
        actualPartitionKey  = KinesisConstants.ALLOWED_NAME_REGEX.matcher(subs.perform(partitionKey)).replaceAll("");
    }


    @Override
    protected boolean isMessageTooLarge(LogMessage message)
    {
        // FIXME - this has to account for UTF-8
        // TODO  - add partition key, if any
        return message.size() >= KinesisConstants.MAX_MESSAGE_BYTES;
    }
}
