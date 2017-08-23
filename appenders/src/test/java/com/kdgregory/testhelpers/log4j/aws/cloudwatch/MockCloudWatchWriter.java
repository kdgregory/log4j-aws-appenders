// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.testhelpers.log4j.aws.cloudwatch;

import java.util.ArrayList;
import java.util.List;

import com.kdgregory.log4j.aws.internal.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;

public class MockCloudWatchWriter
implements LogWriter
{
    public List<LogMessage> messages = new ArrayList<LogMessage>();
    public LogMessage lastMessage;

    public boolean stopped;

    public String logGroup;
    public String logStream;
    public long batchDelay;


    public MockCloudWatchWriter(CloudWatchWriterConfig config)
    {
        this.logGroup = config.logGroup;
        this.logStream = config.logStream;
        this.batchDelay = config.batchDelay;
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
        this.batchDelay = value;
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