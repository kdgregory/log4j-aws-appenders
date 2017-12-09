// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.io.UnsupportedEncodingException;
import java.util.Date;

import com.kdgregory.log4j.aws.internal.kinesis.KinesisConstants;
import com.kdgregory.log4j.aws.internal.kinesis.KinesisLogWriter;
import com.kdgregory.log4j.aws.internal.kinesis.KinesisWriterConfig;
import com.kdgregory.log4j.aws.internal.shared.AbstractAppender;
import com.kdgregory.log4j.aws.internal.shared.DefaultThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.Substitutions;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;


/**
 *  Appender that writes to a Kinesis stream.
 */
public class KinesisAppender extends AbstractAppender<KinesisWriterConfig>
{
    // these are the only configuration vars specific to this appender

    private String          streamName;
    private String          partitionKey;
    private int             shardCount;
    private Integer         retentionPeriod;    // we only set if not null

    // these variables hold the post-substitution log-group and log-stream names
    // (held here for testing, as they're passed to the writer for use)

    private String          actualStreamName;
    private String          actualPartitionKey;

    // the length of the actual partition key, after being converted to UTF-8
    private int             partitionKeyLength;


    /**
     *  Base constructor: assigns default values to configuration properties.
     */
    public KinesisAppender()
    {
        super(new DefaultThreadFactory(),
              new WriterFactory<KinesisWriterConfig>()
                  {
                        @Override
                        public LogWriter newLogWriter(KinesisWriterConfig config)
                        {
                            return new KinesisLogWriter(config);
                        }
                   });

        partitionKey = "{startupTimestamp}";
        shardCount = 1;
    }

//----------------------------------------------------------------------------
//  Configuration
//----------------------------------------------------------------------------

    /**
     *  Sets the Kinesis Stream name associated with this appender.
     *  <p>
     *  This property is intended for initial configuration only. Once messages
     *  have been sent to the appender, it cannot be changed.
     *  <p>
     *  There is no default value. If you do not configure a stream, the
     *  appender will be disabled and will report its misconfiguration.
     */
    public void setStreamName(String value)
    {
        if (actualStreamName != null)
        {
            throw new IllegalArgumentException("appender cannot be reconfigured after processing messages");
        }

        streamName = value;
    }


    /**
     *  Returns the unsubstituted stream name; see {@link #setStreamName}.
     *  Intended primarily for testing.
     */
    public String getStreamName()
    {
        return streamName;
    }


    /**
     *  Sets the partition key associated with this appender. This key is used to
     *  assign messages to shards: all messages with the same partition key will
     *  be sent to the same shard.
     *  <p>
     *  This property is intended for initial configuration only. Once messages
     *  have been sent to the appender, it cannot be changed.
     *  <p>
     *  Default value is "{startupTimestamp}".
     */
    public void setPartitionKey(String value)
    {
        if (actualStreamName != null)
        {
            throw new IllegalArgumentException("appender cannot be reconfigured after processing messages");
        }

        partitionKey = value;
    }


    /**
     *  Returns the unsubstituted partition key name; see {@link #setPartitionKey}.
     *  Intended primarily for testing.
     */
    public String getPartitionKey()
    {
        return partitionKey;
    }


    /**
     *  Sets the desired number of shards to use when creating the stream.
     *  This setting has no effect if the stream already exists.
     */
    public void setShardCount(int shardCount)
    {
        this.shardCount = shardCount;
    }


    /**
     *  Returns the configured number of shards for the stream. This may not
     *  correspond to the actual shards in the stream.
     */
    public int getShardCount()
    {
        return shardCount;
    }


    /**
     *  Sets the message retention period, in hours. Only applied when creating
     *  a new stream. Per AWS, minimum value is 24, maximum value is 168. Note
     *  that non-default retention periods increase your stream cost.
     */
    public void setRetentionPeriod(int value)
    {
        if ((value <= KinesisConstants.MINIMUM_RETENTION_PERIOD) || (value > KinesisConstants.MAXIMUM_RETENTION_PERIOD))
        {
            throw new IllegalArgumentException(
                "retentionPeriod must be between " + (KinesisConstants.MINIMUM_RETENTION_PERIOD + 1)
                + " and " + KinesisConstants.MAXIMUM_RETENTION_PERIOD);
        }
        retentionPeriod = Integer.valueOf(value);
    }


    /**
     *  Returns the configured retention period. Note that this may not
     *  correspond to the actual retention period of the stream.
     */
    public int getRetentionPeriod()
    {
        return (retentionPeriod != null)
             ? retentionPeriod.intValue()
             : KinesisConstants.MINIMUM_RETENTION_PERIOD;
    }

//----------------------------------------------------------------------------
//  Appender-specific methods
//----------------------------------------------------------------------------


//----------------------------------------------------------------------------
//  AbstractAppender overrides
//----------------------------------------------------------------------------

    @Override
    protected KinesisWriterConfig generateWriterConfig()
    {
        Substitutions subs = new Substitutions(new Date(), sequence.get());
        actualStreamName  = KinesisConstants.ALLOWED_NAME_REGEX.matcher(subs.perform(streamName)).replaceAll("");
        actualPartitionKey  = KinesisConstants.ALLOWED_NAME_REGEX.matcher(subs.perform(partitionKey)).replaceAll("");

        try
        {
            partitionKeyLength = actualPartitionKey.getBytes("UTF-8").length;
        }
        catch (UnsupportedEncodingException ex)
        {
            throw new RuntimeException("JVM doesn't support UTF-8 (should never happen)");
        }

        return new KinesisWriterConfig(actualStreamName, shardCount, retentionPeriod,
                                       actualPartitionKey, partitionKeyLength, batchDelay,
                                       discardThreshold, discardAction);
    }


    @Override
    protected boolean isMessageTooLarge(LogMessage message)
    {
        // note: we assume that the writer config has been generated as part of
        //       initialization, prior to any message being processed

        return (message.size() + partitionKeyLength) >= KinesisConstants.MAX_MESSAGE_BYTES;
    }
}
