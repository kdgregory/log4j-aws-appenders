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

package com.kdgregory.log4j2.aws;

import java.util.Date;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;

import com.kdgregory.log4j2.aws.internal.AbstractAppender;
import com.kdgregory.log4j2.aws.internal.AbstractAppenderBuilder;
import com.kdgregory.log4j2.aws.internal.KinesisAppenderConfig;
import com.kdgregory.logging.aws.kinesis.KinesisConstants;
import com.kdgregory.logging.aws.kinesis.KinesisWriterConfig;
import com.kdgregory.logging.aws.kinesis.KinesisWriterFactory;
import com.kdgregory.logging.aws.kinesis.KinesisWriterStatistics;
import com.kdgregory.logging.aws.common.Substitutions;
import com.kdgregory.logging.common.factories.DefaultThreadFactory;
import com.kdgregory.logging.common.util.InternalLogger;


@Plugin(name = "KinesisAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class KinesisAppender
extends AbstractAppender<KinesisAppenderConfig,KinesisWriterStatistics,KinesisWriterConfig>
{

//----------------------------------------------------------------------------
//  Builder
//----------------------------------------------------------------------------

    @PluginBuilderFactory
    public static KinesisAppenderBuilder newBuilder() {
        return new KinesisAppenderBuilder();
    }

    public static class KinesisAppenderBuilder
    extends AbstractAppenderBuilder<KinesisAppenderBuilder>
    implements KinesisAppenderConfig, org.apache.logging.log4j.core.util.Builder<KinesisAppender>
    {
        @PluginBuilderAttribute("name")
        @Required(message = "KinesisAppender: no name provided")
        private String name;

        @Override
        public String getName()
        {
            return name;
        }

        public KinesisAppenderBuilder setName(String value)
        {
            this.name = value;
            return this;
        }

        @PluginBuilderAttribute("streamName")
        private String streamName;

        @Override
        public String getStreamName()
        {
            return streamName;
        }

        public KinesisAppenderBuilder setStreamName(String value)
        {
            this.streamName = value;
            return this;
        }

        @PluginBuilderAttribute("partitionKey")
        private String partitionKey = "{startupTimestamp}";

        @Override
        public String getPartitionKey()
        {
            return partitionKey;
        }

        public KinesisAppenderBuilder setPartitionKey(String value)
        {
            this.partitionKey = value;
            return this;
        }

        @PluginBuilderAttribute("autoCreate")
        private boolean autoCreate;

        @Override
        public boolean getAutoCreate()
        {
            return autoCreate;
        }

        public KinesisAppenderBuilder setAutoCreate(boolean value)
        {
            this.autoCreate = value;
            return this;
        }

        @PluginBuilderAttribute("shardCount")
        private int shardCount = 1;

        @Override
        public int getShardCount()
        {
            return shardCount;
        }

        public KinesisAppenderBuilder setShardCount(int value)
        {
            this.shardCount = value;
            return this;
        }

        @PluginBuilderAttribute("retentionPeriod")
        private Integer retentionPeriod;

        @Override
        public Integer getRetentionPeriod()
        {
            return (retentionPeriod != null)
                 ? retentionPeriod.intValue()
                 : KinesisConstants.MINIMUM_RETENTION_PERIOD;
        }

        public KinesisAppenderBuilder setRetentionPeriod(Integer value)
        {
            // note: validation happens in constructor
            this.retentionPeriod = value;
            return this;
        }

        @Override
        public KinesisAppender build()
        {
            return new KinesisAppender(name, this, null);
        }
    }

//----------------------------------------------------------------------------
//  Appender
//----------------------------------------------------------------------------

    // this is extracted from config so that we can validate

    protected Integer retentionPeriod;

    protected KinesisAppender(String name, KinesisAppenderConfig config, InternalLogger internalLogger)
    {
        super(
            name,
            new DefaultThreadFactory("log4j2-kinesis"),
            new KinesisWriterFactory(),
            new KinesisWriterStatistics(),
            config,
            internalLogger);
    }

//----------------------------------------------------------------------------
//  Additional public API
//----------------------------------------------------------------------------


//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    @Override
    protected KinesisWriterConfig generateWriterConfig()
    {
        Substitutions subs        = new Substitutions(new Date(), sequence.get());
        String actualStreamName   = subs.perform(config.getStreamName());
        String actualPartitionKey = subs.perform(config.getPartitionKey());

        return new KinesisWriterConfig(
            actualStreamName, actualPartitionKey,
            config.getAutoCreate(), config.getShardCount(), config.getRetentionPeriod(),
            config.getBatchDelay(), config.getDiscardThreshold(), discardAction,
            config.getClientFactory(), config.getClientRegion(), config.getClientEndpoint());

    }


    @Override
    protected boolean shouldRotate(long now)
    {
        return false;
    }
}
