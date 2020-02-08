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
import com.kdgregory.log4j2.aws.internal.SNSAppenderConfig;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;
import com.kdgregory.logging.aws.sns.SNSWriterFactory;
import com.kdgregory.logging.aws.sns.SNSWriterStatistics;
import com.kdgregory.logging.aws.common.Substitutions;
import com.kdgregory.logging.common.factories.DefaultThreadFactory;
import com.kdgregory.logging.common.util.InternalLogger;


@Plugin(name = "SNSAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class SNSAppender
extends AbstractAppender<SNSAppenderConfig,SNSWriterStatistics,SNSWriterConfig>
{

//----------------------------------------------------------------------------
//  Builder
//----------------------------------------------------------------------------

    @PluginBuilderFactory
    public static SNSAppenderBuilder newBuilder() {
        return new SNSAppenderBuilder();
    }

    public static class SNSAppenderBuilder
    extends AbstractAppenderBuilder<SNSAppender>
    implements SNSAppenderConfig
    {
        // this appender has different defaults than others, and while I could
        // have created an arg-taking constructor for AbstractAppenderBuilder,
        // I'm a little worred that Log4J might try to do something bizarre
        public SNSAppenderBuilder()
        {
            setDiscardThreshold(1000);
        }
        
        @PluginBuilderAttribute("name")
        @Required(message = "SNSAppender: no name provided")
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

        @PluginBuilderAttribute("topicName")
        private String topicName;

        @Override
        public String getTopicName()
        {
            return topicName;
        }

        public void setTopicName(String value)
        {
            this.topicName = value;
        }

        @PluginBuilderAttribute("topicArn")
        private String topicArn;

        @Override
        public String getTopicArn()
        {
            return topicArn;
        }

        public void setTopicArn(String value)
        {
            this.topicArn = value;
        }

        @PluginBuilderAttribute("subject")
        private String subject;

        @Override
        public String getSubject()
        {
            return subject;
        }

        public void setSubject(String value)
        {
            this.subject = value;
        }

        @PluginBuilderAttribute("autoCreate")
        private boolean autoCreate;

        @Override
        public boolean isAutoCreate()
        {
            return autoCreate;
        }

        public void setAutoCreate(boolean value)
        {
            this.autoCreate = value;
        }

        @Override
        public long getBatchDelay()
        {
            // we want a different default than other appenders, and we can't prevent
            // Log4J from changing it (because it bypasses the setter), so we'll return
            // a constant ... unless the super returns 0, which means synchronous mode
            long value = super.getBatchDelay();
            return (value == 0) ? 0 : 1;
        }

        @Override
        public SNSAppender build()
        {
            return new SNSAppender(name, this, null);
        }
    }

//----------------------------------------------------------------------------
//  Appender
//----------------------------------------------------------------------------

    // this is extracted from config so that we can validate

    protected Integer retentionPeriod;

    protected SNSAppender(String name, SNSAppenderConfig config, InternalLogger internalLogger)
    {
        super(
            name,
            new DefaultThreadFactory("log4j2-kinesis"),
            new SNSWriterFactory(),
            new SNSWriterStatistics(),
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
    protected SNSWriterConfig generateWriterConfig()
    {
        Substitutions subs      = new Substitutions(new Date(), sequence.get());

        String actualTopicName  = subs.perform(config.getTopicName());
        String actualTopicArn   = subs.perform(config.getTopicArn());
        String actualSubject    = subs.perform(config.getSubject());

        return new SNSWriterConfig(
            actualTopicName, actualTopicArn, actualSubject, config.isAutoCreate(),
            config.getDiscardThreshold(), discardAction,
            config.getClientFactory(), config.getClientRegion(), config.getClientEndpoint());

    }


    @Override
    protected boolean shouldRotate(long now)
    {
        return false;
    }
}
