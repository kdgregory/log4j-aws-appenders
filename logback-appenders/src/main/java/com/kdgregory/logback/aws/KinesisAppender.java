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
import com.kdgregory.logging.aws.common.Substitutions;
import com.kdgregory.logging.aws.kinesis.KinesisConstants;
import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;
import com.kdgregory.logging.aws.kinesis.KinesisWriterFactory;
import com.kdgregory.logging.aws.kinesis.KinesisWriterStatistics;
import com.kdgregory.logging.aws.kinesis.KinesisWriterStatisticsMXBean;
import com.kdgregory.logging.common.factories.DefaultThreadFactory;


/**
 *  An appender that writes to a Kinesis stream.
 *  <p>
 *  This appender supports the following configuration parameters:
 *  <p>
 *  <table>
 *  <tr VALIGN="top">
 *      <th> streamName
 *      <td> The name of the Kinesis data stream where messages are written.
 *
 *  <tr VALIGN="top">
 *      <th> partitionKey
 *      <td> The partition key to use for messages from this appender. All
 *           messages with the same partition key will be sent to the same
 *           Kinesis shard.
 *           <p>
 *           Default value is "{startupTimestamp}".
 *           <p>
 *           The value <code>{random}</code> will configure the appender to
 *           use random partition keys. This is useful for an application that
 *           produces high log volume, and which would be throttled if it sent
 *           all messages to the same shard.
 *
 *  <tr VALIGN="top">
 *      <th> autoCreate
 *      <td> If true, the appender will create the stream if it does not already
 *           exist. If false, a missing stream will be reported as an error and
 *           the appender will be disabled.
 *           <p>
 *           Default is <code>false</code>.
 *
 *  <tr VALIGN="top">
 *      <th> shardCount
 *      <td> For auto-created streams, the number of shards in the stream.
 *
 *  <tr VALIGN="top">
 *      <th> retentionPeriod
 *      <td> For auto-created streams, the number of hours that messages will be
 *           retained in the stream. Allowed range is 25 to 168.
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
 *           the maximum length permitted by Kinesis. If <code>false</code> they are
 *           discarded. In either case, the oversized message is reported to the
 *           Logback debug log.
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
 *           <p>
 *           Note that the region must be supported by the current SDK version.
 *
 *  <tr VALIGN="top">
 *      <th> clientEndpoint
 *      <td> Specifies a non-default service endpoint. This is intended for use with
 *           older AWS SDK versions that do not provide client factories and default
 *           to us-east-1 for constructed clients, although it can be used for newer
 *           releases when you want to override the default region provider. This
 *           setting is ignored if you use a client factory.
 *
 *  <tr VALIGN="top">
 *      <th> useShutdownHook
 *      <td> Controls whether the appender uses a shutdown hook to attempt to process
 *           outstanding messages when the JVM exits. This is true by default; set to
 *           false to disable.
 *  </table>
 *
 *  @see <a href="https://github.com/kdgregory/log4j-aws-appenders/blob/master/docs/kinesis.md">Appender documentation</a>
 */
public class KinesisAppender<LogbackEventType>
extends AbstractAppender<KinesisWriterConfig,KinesisWriterStatistics,KinesisWriterStatisticsMXBean,LogbackEventType>
{
    // appender-specific configuration variables

    private String          streamName;
    private String          partitionKey;
    private boolean         autoCreate;
    private int             shardCount;
    private Integer         retentionPeriod;    // we only set if not null

    // these variables are assigned when the writer is initialized, are used
    // to prevent attempts at reconfiguration

    private String          actualStreamName;
    private String          actualPartitionKey;


    public KinesisAppender()
    {
        super(new DefaultThreadFactory("logback-kinesis"),
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
     *  Sets the <code>streamName</code> configuration property.
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
     *  Rturns the <code>streamName</code> configuration property.
     */
    public String getStreamName()
    {
        return streamName;
    }


    /**
     *  Sets the <code>partitionKey</code> configuration property.
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
     *  Returns the <code>partitionKey</code> configuration property.
     */
    public String getPartitionKey()
    {
        return partitionKey;
    }


    /**
     *  Sets the <code>autoCreate</code> configuration property.
     */
    public void setAutoCreate(boolean autoCreate)
    {
        this.autoCreate = autoCreate;
    }


    /**
     *  Returns the <code>autoCreate</code> configuration property.
     */
    public boolean isAutoCreate()
    {
        return autoCreate;
    }



    /**
     *  Sets the <code>shardCount</code> configuration property.
     */
    public void setShardCount(int shardCount)
    {
        this.shardCount = shardCount;
    }



    /**
     *  Returns the <code>shardCount</code> configuration property.
     */
    public int getShardCount()
    {
        return shardCount;
    }


    /**
     *  Sets the <code>retentionPeriod</code> configuration property.
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
     *  Returns the <code>retentionPeriod</code> configuration property.
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

        return new KinesisWriterConfig()
               .setStreamName(actualStreamName)
               .setPartitionKey(actualPartitionKey)
               .setAutoCreate(autoCreate)
               .setShardCount(shardCount)
               .setRetentionPeriod(retentionPeriod)
               .setTruncateOversizeMessages(truncateOversizeMessages)
               .setBatchDelay(batchDelay)
               .setDiscardThreshold(discardThreshold)
               .setDiscardAction(discardAction)
               .setClientFactoryMethod(clientFactory)
               .setAssumedRole(assumedRole)
               .setClientRegion(clientRegion)
               .setClientEndpoint(clientEndpoint);
    }


    @Override
    protected boolean shouldRotate(long now)
    {
        return false;
    }
}
