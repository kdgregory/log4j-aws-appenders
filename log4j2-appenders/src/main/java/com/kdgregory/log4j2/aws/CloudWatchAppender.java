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
import com.kdgregory.log4j2.aws.internal.CloudWatchAppenderConfig;
import com.kdgregory.log4j2.aws.internal.Log4J2InternalLogger;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchConstants;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterFactory;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterStatistics;
import com.kdgregory.logging.aws.common.Substitutions;
import com.kdgregory.logging.common.factories.DefaultThreadFactory;
import com.kdgregory.logging.common.util.RotationMode;


@Plugin(name = "CloudWatchAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class CloudWatchAppender
extends AbstractAppender<CloudWatchAppenderConfig,CloudWatchWriterStatistics,CloudWatchWriterConfig>
{

//----------------------------------------------------------------------------
//  Builder
//----------------------------------------------------------------------------

    @PluginBuilderFactory
    public static CloudWatchAppenderBuilder newBuilder() {
        return new CloudWatchAppenderBuilder();
    }

    public static class CloudWatchAppenderBuilder
    extends AbstractAppenderBuilder<CloudWatchAppender>
    implements CloudWatchAppenderConfig
    {
        @PluginBuilderAttribute("name")
        @Required(message = "CloudWatchAppender: no name provided")
        private String name;

        @Override
        public String getName()
        {
            return name;
        }

        public void setName(String value)
        {
            this.name = value;
        }

        @PluginBuilderAttribute("logGroup")
        private String logGroup;

        @Override
        public String getLogGroup()
        {
            return logGroup;
        }

        public void setLogGroup(String value)
        {
            this.logGroup = value;
        }

        @PluginBuilderAttribute("logStream")
        private String logStream = "{startupTimestamp}";

        @Override
        public String getLogStream()
        {
            return logStream;
        }

        public void setLogStream(String value)
        {
            this.logStream = value;
        }

        @PluginBuilderAttribute("retentionPeriod")
        private Integer retentionPeriod;

        @Override
        public Integer getRetentionPeriod()
        {
            return retentionPeriod;
        }

        public void setRetentionPeriod(Integer value)
        {
            // note: validation happens in appender because Log4J bypasses
            //       this setter during normal configuration
            this.retentionPeriod = value;
        }

        @PluginBuilderAttribute("rotationMode")
        private String rotationMode = RotationMode.none.name();

        @Override
        public String getRotationMode()
        {
            return rotationMode;
        }

        public void setRotationMode(String value)
        {
            // note: validation happens in appender because Log4J bypasses
            //       this setter during normal configuration
            this.rotationMode = value;
        }

        @PluginBuilderAttribute("rotationInterval")
        private long rotationInterval = -1;

        @Override
        public long getRotationInterval()
        {
            return rotationInterval;
        }

        public void setRotationInterval(long value)
        {
            this.rotationInterval = value;
        }

        @PluginBuilderAttribute("sequence")
        private int sequence;

        @Override
        public int getSequence()
        {
            return sequence;
        }

        public void setSequence(int value)
        {
            this.sequence = value;
        }

        @PluginBuilderAttribute("dedicatedWriter")
        private boolean dedicatedWriter;

        @Override
        public boolean isDedicatedWriter()
        {
            return dedicatedWriter;
        }

        public void setDedicatedWriter(boolean value)
        {
            this.dedicatedWriter = value;
        }

        @Override
        public CloudWatchAppender build()
        {
            return new CloudWatchAppender(name, this, null);
        }
    }

//----------------------------------------------------------------------------
//  Appender
//----------------------------------------------------------------------------

    // this is extracted from config so that we can validate

    protected Integer retentionPeriod;

    protected CloudWatchAppender(String name, CloudWatchAppenderConfig config, Log4J2InternalLogger internalLogger)
    {
        super(
            name,
            new DefaultThreadFactory("log4j2-cloudwatch"),
            new CloudWatchWriterFactory(),
            new CloudWatchWriterStatistics(),
            config,
            internalLogger);

        try
        {
            retentionPeriod = CloudWatchConstants.validateRetentionPeriod(config.getRetentionPeriod());
        }
        catch (IllegalArgumentException ex)
        {
            internalLogger.error(ex.getMessage(), null);
        }
    }

//----------------------------------------------------------------------------
//  Additional public API
//----------------------------------------------------------------------------

    /**
     *  Explicitly switch to a new log stream.
     */
    @Override
    protected void rotate()
    {
        super.rotate();
    }

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    @Override
    protected CloudWatchWriterConfig generateWriterConfig()
    {
        Substitutions subs     = new Substitutions(new Date(), sequence.get());
        String actualLogGroup  = subs.perform(config.getLogGroup());
        String actualLogStream = subs.perform(config.getLogStream());

        return new CloudWatchWriterConfig(
            actualLogGroup, actualLogStream, retentionPeriod,
            config.isDedicatedWriter(), config.getBatchDelay(),
            config.getDiscardThreshold(), discardAction,
            config.getClientFactory(), config.getClientRegion(), config.getClientEndpoint());
    }
}
