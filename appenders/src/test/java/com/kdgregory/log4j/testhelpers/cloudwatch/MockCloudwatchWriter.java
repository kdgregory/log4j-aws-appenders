// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.testhelpers.cloudwatch;

import java.util.ArrayList;
import java.util.List;

import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.LogWriter;

public class MockCloudwatchWriter
implements LogWriter
{
    public List<LogMessage> messages = new ArrayList<LogMessage>();
    public List<LogMessage> lastBatch;
    public boolean stopped;


    @Override
    public void addBatch(List<LogMessage> batch)
    {
        messages.addAll(batch);
        lastBatch = batch;
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