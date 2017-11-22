// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import com.kdgregory.log4j.aws.internal.shared.AbstractAppender;
import com.kdgregory.log4j.aws.internal.shared.DefaultThreadFactory;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.shared.WriterFactory;
import com.kdgregory.log4j.aws.internal.sns.SNSLogWriter;
import com.kdgregory.log4j.aws.internal.sns.SNSWriterConfig;


/**
 *  Writes messages to Amazon's Simple Notification Service.
 */
public class SNSAppender
extends AbstractAppender<SNSWriterConfig>
{
    // configuration

    private String topicName;
    private String topicArn;


    public SNSAppender()
    {
        super(new DefaultThreadFactory(),
              new WriterFactory<SNSWriterConfig>()
                  {
                        @Override
                        public LogWriter newLogWriter(SNSWriterConfig config)
                        {
                            return new SNSLogWriter(config);
                        }
                   });

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


//----------------------------------------------------------------------------
//  AbstractAppender
//----------------------------------------------------------------------------


    @Override
    protected SNSWriterConfig generateWriterConfig()
    {
        throw new UnsupportedOperationException("FIXME - implement");
    }

    @Override
    protected boolean isMessageTooLarge(LogMessage message)
    {
        throw new UnsupportedOperationException("FIXME - implement");
    }

}
