// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.cloudwatch.helpers;

import java.util.ArrayList;
import java.util.List;

import com.kdgregory.log4j.shared.LogWriter;
import com.kdgregory.log4j.shared.LogMessage;

public class MockCloudwatchWriter
implements LogWriter
{
    public List<LogMessage> messages = new ArrayList<LogMessage>();
    public List<LogMessage> lastBatch;


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
}