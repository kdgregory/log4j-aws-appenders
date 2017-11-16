// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.testhelpers.aws.sns;

import java.util.ArrayList;
import java.util.List;

import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;
import com.kdgregory.log4j.aws.internal.sns.SNSWriterConfig;


public class MockSNSWriter
implements LogWriter
{
    public List<LogMessage> messages = new ArrayList<LogMessage>();
    public LogMessage lastMessage;

    public boolean stopped;

    public String topicArn;


    public MockSNSWriter(SNSWriterConfig config)
    {
        this.topicArn = config.topicArn;
    }


    @Override
    public void addMessage(LogMessage message)
    {
        messages.add(message);
        lastMessage = message;
    }


    @Override
    public void setBatchDelay(long value)
    {
        throw new IllegalStateException("this function should never be called");
    }


    /**
     *  Returns the text for the numbered message (starting at 0).
     */
    public String getMessage(int msgnum)
    throws Exception
    {
        return messages.get(msgnum).getMessage();
    }


    @Override
    public void stop()
    {
        stopped = true;
    }


    @Override
    public void run()
    {
        // we're not expecting to be on a background thread, so do nothing
    }
}