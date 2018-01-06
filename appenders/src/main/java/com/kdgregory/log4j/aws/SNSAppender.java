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

import com.kdgregory.log4j.aws.internal.shared.AbstractAppender;
import com.kdgregory.log4j.aws.internal.shared.DefaultThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.Substitutions;
import com.kdgregory.log4j.aws.internal.sns.SNSConstants;
import com.kdgregory.log4j.aws.internal.sns.SNSWriterConfig;
import com.kdgregory.log4j.aws.internal.sns.SNSWriterFactory;


/**
 *  Writes messages to Amazon's Simple Notification Service.
 */
public class SNSAppender
extends AbstractAppender<SNSWriterConfig>
{
    // configuration

    private String topicName;
    private String topicArn;
    private String subject;


    public SNSAppender()
    {
        super(new DefaultThreadFactory(),
              new SNSWriterFactory());

        super.setDiscardThreshold(1000);
        super.setBatchDelay(1);
    }


//----------------------------------------------------------------------------
//  Configuration
//----------------------------------------------------------------------------

    /**
     *  Sets the topic name. The list of existing topics will be searched for
     *  this name (picking one arbitrarily if there are topics with the same
     *  name in multiple regions). If no topic exists with the specified name
     *  one will be created.
     *  <p>
     *  Supports substitution values.
     *  <p>
     *  If you specify both topic name and topic ARN, the latter takes precedence.
     *  If no topic exists that matches the ARN, one will <em>not</em> be created.
     */
    public void setTopicName(String value)
    {
        this.topicName = value;
    }


    /**
     *  Returns the configured topic name, <em>without</em> substitutions applied.
     *  Returns <code>null</code> if the topic has been configured by ARN.
     */
    public String getTopicName()
    {
        return this.topicName;
    }


    /**
     *  Specifies the destination topic by ARN; the topic must already exist.
     *  <p>
     *  Supports substitution values.
     *  <p>
     *  If you specify both topic name and topic ARN, the latter takes precedence.

     */
    public void setTopicArn(String value)
    {
        this.topicArn = value;
    }


    /**
     *  Returns the configured topic ARN, <em>without</em> substitutions applied.
     *  Returns <code>null</code> if the topic has been configured by name.
     */
    public String getTopicArn()
    {
        return this.topicArn;
    }


    /**
     *  Any configured batch delay will be ignored; the appender attempts to send
     *  all messages as soon as they are appended.
     */
    @Override
    public void setBatchDelay(long value)
    {
        super.setBatchDelay(1);
    }


    /**
     *  Sets the subject that will be applied to messages; blank disables subjects.
     *  At present this may only be configured at appender start.
     */
    public void setSubject(String value)
    {
        this.subject = value;
    }


    /**
     *  Returns the current subject value, as set.
     */
    public String getSubject()
    {
        return this.subject;
    }


//----------------------------------------------------------------------------
//  AbstractAppender
//----------------------------------------------------------------------------

    @Override
    protected SNSWriterConfig generateWriterConfig()
    {
        Substitutions subs = new Substitutions(new Date(), sequence.get());

        String actualTopicName  = subs.perform(topicName);
        String actualTopicArn   = subs.perform(topicArn);
        String actualSubject    = subs.perform(subject);

        return new SNSWriterConfig(actualTopicName, actualTopicArn, actualSubject, discardThreshold, discardAction, clientFactory, clientEndpoint);
    }

    @Override
    protected boolean isMessageTooLarge(LogMessage message)
    {
        return message.size() > SNSConstants.MAX_MESSAGE_BYTES;
    }

}
