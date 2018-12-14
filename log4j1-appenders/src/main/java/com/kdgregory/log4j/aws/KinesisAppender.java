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

package com.kdgregory.log4j.aws;

import java.util.Date;

import com.kdgregory.log4j.aws.internal.AbstractAppender;
import com.kdgregory.logging.aws.common.Substitutions;
import com.kdgregory.logging.aws.kinesis.KinesisWriterStatistics;
import com.kdgregory.logging.aws.kinesis.KinesisWriterStatisticsMXBean;
import com.kdgregory.logging.aws.kinesis.KinesisConstants;
import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;
import com.kdgregory.logging.aws.kinesis.KinesisWriterFactory;
import com.kdgregory.logging.common.factories.DefaultThreadFactory;


/**
 *  Appender that writes to a Kinesis stream.
 */
public class KinesisAppender
extends AbstractAppender<KinesisWriterConfig,KinesisWriterStatistics,KinesisWriterStatisticsMXBean>
{
    // these are the only configuration vars specific to this appender

    private String          streamName;
    private String          partitionKey;
    private boolean         autoCreate;
    private int             shardCount;
    private Integer         retentionPeriod;    // we only set if not null

    // these variables hold the post-substitution log-group and log-stream names
    // (held here for testing, as they're passed to the writer for use)

    private String          actualStreamName;
    private String          actualPartitionKey;


    /**
     *  Base constructor: assigns default values to configuration properties.
     */
    public KinesisAppender()
    {
        super(new DefaultThreadFactory("log4j-kinesis"),
              new KinesisWriterFactory(),
              new KinesisWriterStatistics(),
              KinesisWriterStatisticsMXBean.class);

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
     *  Default value is "{startupTimestamp}".
     *  <p>
     *  Setting this value to blank will result in generating a pseudo-random
     *  8-digit partition key for each message.
     *  <p>
     *  This property is intended for initial configuration only. Once messages
     *  have been sent to the appender, it cannot be changed.
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
     *  Sets the auto-creation policy: if <code>true</code>, the stream will be created
     *  if it does not already exist.
     */
    public void setAutoCreate(boolean autoCreate)
    {
        this.autoCreate = autoCreate;
    }


    /**
     *  Returns the auto-creation policy.
     */
    public boolean isAutoCreate()
    {
        return autoCreate;
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
        actualStreamName   = subs.perform(streamName);
        actualPartitionKey = subs.perform(partitionKey);

        return new KinesisWriterConfig(actualStreamName, actualPartitionKey,
                                       batchDelay, discardThreshold, discardAction,
                                       clientFactory, clientEndpoint,
                                       autoCreate, shardCount, retentionPeriod);
    }
}
