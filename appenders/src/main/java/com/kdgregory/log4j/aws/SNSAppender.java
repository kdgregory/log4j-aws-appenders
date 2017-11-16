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
        super.setBatchDelay(1);
    }


//----------------------------------------------------------------------------
//  Configuration
//----------------------------------------------------------------------------

    public void setTopicArn(String value)
    {
        this.topicArn = value;
    }


    public String getTopicArn()
    {
        return this.topicArn;
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
