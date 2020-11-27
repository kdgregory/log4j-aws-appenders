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
import org.apache.logging.log4j.core.lookup.StrSubstitutor;

import com.kdgregory.log4j2.aws.internal.AbstractAppender;
import com.kdgregory.log4j2.aws.internal.AbstractAppenderBuilder;
import com.kdgregory.log4j2.aws.internal.SNSAppenderConfig;
import com.kdgregory.logging.aws.sns.SNSWriterConfig;
import com.kdgregory.logging.aws.sns.SNSWriterFactory;
import com.kdgregory.logging.aws.sns.SNSWriterStatistics;
import com.kdgregory.logging.aws.sns.SNSWriterStatisticsMXBean;
import com.kdgregory.logging.aws.common.Substitutions;
import com.kdgregory.logging.common.factories.DefaultThreadFactory;
import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  An appender that writes to an SNS topic.
 *  <p>
 *  This appender supports the following configuration parameters:
 *  <p>
 *  <table>
 *  <tr VALIGN="top">
 *      <th> topicName
 *      <td> The name of the destination SNS topic; substitutions are allowed.
 *           <p>
 *           Must refer to a topic in the current region; if not, and you do not
 *           enable auto-create, initialization fails.
 *           <p>
 *           If you specify both <code>topicName</code> and <code>topicArn</code>,
 *           the latter takes precedence.
 *
 *  <tr VALIGN="top">
 *      <th> topicArn
 *      <td> The ARN of the destination SNS topic; substitutions are allowed.
 *           <p>
 *           Must refer to a topic in the current region; if not, initialization
 *           fails.
 *           <p>
 *           If you specify both <code>topicName</code> and <code>topicArn</code>,
 *           the latter takes precedence.
 *
 *  <tr VALIGN="top">
 *      <th> autoCreate
 *      <td> If true, and the topic is specified by name, the appender will create
 *           the topic if it does not already exist. If false, a missing topic
 *           will be reported as an error and the appender will be disabled.
 *           <p>
 *           Default is <code>false</code>.
 *
 *  <tr VALIGN="top">
 *      <th> subject
 *      <td> (optional) The subject for messages that are delivered via email. This
 *           is constrained by the SNS API to be less than 100 characters, ASCII
 *           only, and not start with whitespace.
 *
 *  <tr VALIGN="top">
 *      <th> truncateOversizeMessages
 *      <td> If <code>true</code> (the default), oversize messages are truncated to
 *           the maximum length permitted by SNS. If <code>false</code>, they are
 *           discarded. In either case, the oversized message is reported to the
 *           Log4J debug log.
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
 *  @see <a href="https://github.com/kdgregory/log4j-aws-appenders/blob/master/docs/sns.md">Appender documentation</a>
 */
@Plugin(name = "SNSAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public class SNSAppender
extends AbstractAppender<SNSAppenderConfig,SNSWriterStatistics,SNSWriterStatisticsMXBean,SNSWriterConfig>
{

//----------------------------------------------------------------------------
//  Builder
//----------------------------------------------------------------------------

    @PluginBuilderFactory
    public static SNSAppenderBuilder newBuilder() {
        return new SNSAppenderBuilder();
    }

    public static class SNSAppenderBuilder
    extends AbstractAppenderBuilder<SNSAppenderBuilder>
    implements SNSAppenderConfig, org.apache.logging.log4j.core.util.Builder<SNSAppender>
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

        public SNSAppenderBuilder setName(String value)
        {
            this.name = value;
            return this;
        }

        @Override
        public String getName()
        {
            return name;
        }


        @PluginBuilderAttribute("topicName")
        private String topicName;

        /**
         *  Sets the <code>topicName</code> configuration property.
         */
        public SNSAppenderBuilder setTopicName(String value)
        {
            this.topicName = value;
            return this;
        }

        /**
         *  Returns the <code>topicName</code> configuration property, <code>null</code>
         *  if the appender was configured via ARN.
         */
        @Override
        public String getTopicName()
        {
            return topicName;
        }


        @PluginBuilderAttribute("topicArn")
        private String topicArn;

        /**
         *  Sets the <code>topicArn</code> configuration property.
         */
        public SNSAppenderBuilder setTopicArn(String value)
        {
            this.topicArn = value;
            return this;
        }

        /**
         *  Returns the <code>topicArn</code> configuration property, <code>null</code>
         *  if the appender was configured via name.
         */
        @Override
        public String getTopicArn()
        {
            return topicArn;
        }


        @PluginBuilderAttribute("autoCreate")
        private boolean autoCreate;

        /**
         *  Sets the <code>autoCreate</code> configuration property.
         */
        public SNSAppenderBuilder setAutoCreate(boolean value)
        {
            this.autoCreate = value;
            return this;
        }

        /**
         *  Returns the <code>autoCreate</code> configuration property.
         */
        @Override
        public boolean isAutoCreate()
        {
            return autoCreate;
        }


        @PluginBuilderAttribute("subject")
        private String subject;

        /**
         *  Sets the <code>subject</code> configuration property.
         */
        public SNSAppenderBuilder setSubject(String value)
        {
            this.subject = value;
            return this;
        }

        /**
         *  Returns the <code>subject</code> configuration property.
         */
        @Override
        public String getSubject()
        {
            return subject;
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
            SNSWriterStatisticsMXBean.class,
            config,
            internalLogger);
    }

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    @Override
    protected SNSWriterConfig generateWriterConfig()
    {
        StrSubstitutor l4jsubs  = config.getConfiguration().getStrSubstitutor();
        Substitutions subs      = new Substitutions(new Date(), 0);

        String actualTopicName  = subs.perform(l4jsubs.replace(config.getTopicName()));
        String actualTopicArn   = subs.perform(l4jsubs.replace(config.getTopicArn()));
        String actualSubject    = subs.perform(l4jsubs.replace(config.getSubject()));

        return new SNSWriterConfig()
               .setTopicName(actualTopicName)
               .setTopicArn(actualTopicArn)
               .setSubject(actualSubject)
               .setAutoCreate(config.isAutoCreate())
               .setTruncateOversizeMessages(false)
               .setDiscardThreshold(config.getDiscardThreshold())
               .setDiscardAction(discardAction)
               .setClientFactoryMethod(config.getClientFactory())
               .setAssumedRole(config.getAssumedRole())
               .setClientRegion(config.getClientRegion())
               .setClientEndpoint(config.getClientEndpoint());
    }
}
