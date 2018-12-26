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

package com.kdgregory.logging.testhelpers.cloudwatch;

import java.util.ArrayList;
import java.util.List;

import com.kdgregory.logging.aws.cloudwatch.CloudWatchConstants;
import com.kdgregory.logging.aws.cloudwatch.CloudWatchWriterConfig;
import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.util.DiscardAction;


/**
 *  A mock equivalent of CloudWatchLogWriter, used by appender tests.
 */
public class MockCloudWatchWriter
implements LogWriter
{
    public CloudWatchWriterConfig config;
    
    public List<LogMessage> messages = new ArrayList<LogMessage>();
    public LogMessage lastMessage;

    public boolean stopped;


    public MockCloudWatchWriter(CloudWatchWriterConfig config)
    {
        this.config = config;
    }

//----------------------------------------------------------------------------
//  LogWriter
//----------------------------------------------------------------------------

    @Override
    public boolean isMessageTooLarge(LogMessage message)
    {
        // this is copied from CloudWatchLogWriter
        return (message.size() + CloudWatchConstants.MESSAGE_OVERHEAD)  >= CloudWatchConstants.MAX_BATCH_BYTES;
    }


    @Override
    public void addMessage(LogMessage message)
    {
        messages.add(message);
        lastMessage = message;
    }


    @Override
    public void stop()
    {
        stopped = true;
    }


    @Override
    public void setBatchDelay(long value)
    {
        this.config.batchDelay = value;
    }


    @Override
    public void setDiscardThreshold(int value)
    {
        this.config.discardThreshold = value;
    }


    @Override
    public void setDiscardAction(DiscardAction value)
    {
        this.config.discardAction = value;
    }

//----------------------------------------------------------------------------
//  Runnable
//----------------------------------------------------------------------------

    @Override
    public void run()
    {
        // we're not expecting to be on a background thread, so do nothing
    }

//----------------------------------------------------------------------------
//  Mock-specific methods
//----------------------------------------------------------------------------

    /**
     *  Returns the text for the numbered message (starting at 0).
     */
    public String getMessage(int msgnum)
    throws Exception
    {
        return messages.get(msgnum).getMessage();
    }
}
