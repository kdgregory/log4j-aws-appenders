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

package com.kdgregory.logging.aws.kinesis;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.kdgregory.logging.aws.internal.AbstractWriterConfig;


/**
 *  Configuration for KinesisLogWriter.
 */
public class KinesisWriterConfig
extends AbstractWriterConfig<KinesisWriterConfig>
{
    public final static String          DEFAULT_PARTITION_KEY   = "{startupTimestamp}";
    public final static boolean         DEFAULT_AUTO_CREATE     = false;
    public final static int             DEFAULT_SHARD_COUNT     = 1;


    private String                      streamName;
    private String                      partitionKey            = DEFAULT_PARTITION_KEY;
    private boolean                     autoCreate              = DEFAULT_AUTO_CREATE;
    private int                         shardCount              = DEFAULT_SHARD_COUNT;
    private Integer                     retentionPeriod;

    // this is assigned by setPartitionKey()
    private PartitionKeyHelper partitionKeyHelper;

//----------------------------------------------------------------------------
//  Accessors
//----------------------------------------------------------------------------

    public String getStreamName()
    {
        return streamName;
    }

    public KinesisWriterConfig setStreamName(String value)
    {
        streamName = value;
        return this;
    }


    public String getPartitionKey()
    {
        return partitionKey;
    }

    public KinesisWriterConfig setPartitionKey(String value)
    {
        partitionKey = value;
        partitionKeyHelper = new PartitionKeyHelper(value);
        return this;
    }


    public boolean getAutoCreate()
    {
        return autoCreate;
    }

    public KinesisWriterConfig setAutoCreate(boolean value)
    {
        autoCreate = value;
        return this;
    }


    public int getShardCount()
    {
        return shardCount;
    }

    public KinesisWriterConfig setShardCount(int value)
    {
        shardCount = value;
        return this;
    }


    public Integer getRetentionPeriod()
    {
        return retentionPeriod;
    }

    public KinesisWriterConfig setRetentionPeriod(Integer value)
    {
        retentionPeriod = value;
        return this;
    }

//----------------------------------------------------------------------------
//  Other public methods
//----------------------------------------------------------------------------

    /**
     *  Validates the configuration, returning a list of any validation errors.
     *  An empty list indicates a valid config.
     */
    public List<String> validate()
    {
        List<String> result = new ArrayList<>();

        if (streamName == null)
        {
            result.add("missing stream name");
        }
        else if (streamName.isEmpty())
        {
            result.add("blank stream name");
        }
        else if (streamName.length() > 128)
        {
            result.add("stream name too long");
        }
        else if (! Pattern.matches(KinesisConstants.ALLOWED_STREAM_NAME_REGEX, streamName))
        {
            result.add("invalid stream name: " + streamName);
        }

        if (partitionKey == null)
        {
            result.add("missing partition key");
        }
        else if (partitionKey.length() > 256)
        {
            result.add("partition key too long");
        }

        if (autoCreate && (retentionPeriod != null))
        {
            if (retentionPeriod < 24)
                result.add("minimum retention period is 24 hours");
            else if (retentionPeriod > 8760)
                result.add("maximum retention period is 8760 hours");
        }

        return result;
    }


    /**
     *  Returns the object that manages partition keys; use this rather than directly accessing
     *  the key.
     */
    public PartitionKeyHelper getPartitionKeyHelper()
    {
        return partitionKeyHelper;
    }
}
