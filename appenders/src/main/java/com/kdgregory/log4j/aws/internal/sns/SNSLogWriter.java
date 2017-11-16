// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.sns;

import java.util.List;

import com.kdgregory.log4j.aws.internal.shared.AbstractLogWriter;
import com.kdgregory.log4j.aws.internal.shared.LogMessage;
import com.kdgregory.log4j.aws.internal.shared.MessageQueue.DiscardAction;

public class SNSLogWriter
extends AbstractLogWriter
{
    public SNSLogWriter(SNSWriterConfig config)
    {
        super(1, 1, DiscardAction.none);
    }
    

//----------------------------------------------------------------------------
//  AbstractLogWriter implementation
//----------------------------------------------------------------------------
    
    @Override
    protected void createAWSClient()
    {
        throw new UnsupportedOperationException("FIXME - implement");
    }

    @Override
    protected boolean ensureDestinationAvailable()
    {
        throw new UnsupportedOperationException("FIXME - implement");
    }

    @Override
    protected List<LogMessage> processBatch(List<LogMessage> currentBatch)
    {
        throw new UnsupportedOperationException("FIXME - implement");
    }

    @Override
    protected int effectiveSize(LogMessage message)
    {
        throw new UnsupportedOperationException("FIXME - implement");
    }

    @Override
    protected boolean withinServiceLimits(int batchBytes, int numMessages)
    {
        throw new UnsupportedOperationException("FIXME - implement");
    }

}
