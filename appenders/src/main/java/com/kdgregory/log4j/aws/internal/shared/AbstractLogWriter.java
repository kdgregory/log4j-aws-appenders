// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.shared;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.log4j.helpers.LogLog;

import com.kdgregory.log4j.aws.internal.shared.MessageQueue.DiscardAction;


/**
 *  Manages common LogWriter activities.
 */
public abstract class AbstractLogWriter
implements LogWriter
{
    private MessageQueue messageQueue;
    private long batchDelay;

    private Thread dispatchThread;

    private volatile Long shutdownTime;                     // this is an actual timestamp, not an elapsed time

    private volatile int batchCount;                        // these can be read via accessor methods; they're intended for testing
    private volatile String initializationMessage;
    private volatile Throwable initializationException;


    public AbstractLogWriter(long batchDelay, int discardThreshold, DiscardAction discardAction)
    {
        this.batchDelay = batchDelay;
        messageQueue = new MessageQueue(discardThreshold, discardAction);
    }


//----------------------------------------------------------------------------
//  Accessors
//----------------------------------------------------------------------------

    /**
     *  Returns the current batch delay. This is intended for testing.
     */
    public long getBatchDelay()
    {
        return batchDelay;
    }


    /**
     *  Returns the number of batches processed. This is intended for testing
     */
    public int getBatchCount()
    {
        return batchCount;
    }


    /**
     *  Returns any initialization error. This will be null until initialization
     *  completes, and an empty string if initialization was successful. A non-empty
     *  string indicates that an error took place, and you can call {@link
     *  #getInitializationException} to get more information.
     */
    public String getInitializationMessage()
    {
        return initializationMessage;
    }


    /**
     *  Returns the exception associated with an initialization error. This might
     *  be null; initialization can dail due to invalid conditions rather than a thrown
     *  exception.
     */
    public Throwable getInitializationException()
    {
        return initializationException;
    }



//----------------------------------------------------------------------------
//  Implementation of LogWriter
//----------------------------------------------------------------------------

    @Override
    public void addMessage(LogMessage message)
    {
        messageQueue.enqueue(message);
    }


    @Override
    public void setBatchDelay(long value)
    {
        this.batchDelay = value;
    }


    @Override
    public void stop()
    {
        shutdownTime = new Long(System.currentTimeMillis() + batchDelay);
        if (dispatchThread != null)
        {
            dispatchThread.interrupt();
        }
    }


//----------------------------------------------------------------------------
//  Implementation of Runnable
//----------------------------------------------------------------------------

    @Override
    public void run()
    {
        if (! initialize())
        {
            if (initializationException != null)
                LogLog.error("initialization failed: " + initializationMessage, initializationException);
            else
                LogLog.error("initialization failed: " + initializationMessage);

            return;
        }

        // this reports that initialization succeeded

        initializationMessage = "";

        // the do-while loop ensures that we attempt to process at least one batch, even if
        // the writer is started and immediately stopped; that's not likely to happen in the
        // real world, but was causing problems with the smoketest (which is configured to
        // quickly transition writers)

        do
        {
            List<LogMessage> currentBatch = buildBatch();
            if (currentBatch.size() > 0)
            {
                batchCount++;
                List<LogMessage> failures = processBatch(currentBatch);
                requeueMessages(failures);
            }
        } while (keepRunning());
    }


//----------------------------------------------------------------------------
//  Subclass hooks
//----------------------------------------------------------------------------

    /**
     *  Creates the appropriate AWS client. This is called as the first thing
     *  in the run() method (because, for some reason calling it in the ctor
     *  was failing).
     */
    protected abstract void createAWSClient();


    /**
     *  Verifies that the logging destination is available, including creating
     *  it if not. Return <code>true</code> if successful, <code>false</code>
     *  if not (which will cause the appender to stop running).
     */
    protected abstract boolean ensureDestinationAvailable();


    /**
     *  Processes a batch of messages. The subclass is responsible for returning
     *  any messages that weren't sent, in order, so that they can be requeued.
     */
    protected abstract List<LogMessage> processBatch(List<LogMessage> currentBatch);


    /**
     *  Calculates the effective size of the message. This includes the message
     *  bytes plus any overhead.
     */
    protected abstract int effectiveSize(LogMessage message);


    /**
     *  Determines whether the provided batch size or number of messages would
     *  exceed the service's limits.
     */
    protected abstract boolean withinServiceLimits(int batchBytes, int numMessages);


//----------------------------------------------------------------------------
//  Subclass helpers
//----------------------------------------------------------------------------

    /**
     *  Records an initialization failure in the "post-mortem" variables. This
     *  method returns false so that the failure handler can return its result
     *  to the caller.
     */
    protected boolean initializationFailure(String message, Exception exception)
    {
        initializationMessage = message;
        initializationException = exception;
        return false;
    }


    /**
     *  Attempts to read a list of messages from the queue. Will wait "forever"
     *  (or until shutdown) for the first message, then read as many messages
     *  as possible within the batch delay.
     *  <p>
     *  For each message, the subclass is called to determine the effective size
     *  of the message, and whether the aggregate batch size is within the range
     *  accepted by the service.
     */
    protected List<LogMessage> buildBatch()
    {
        // presizing to a small-but-possible size to avoid repeated resizes
        List<LogMessage> batch = new ArrayList<LogMessage>(512);

        // we'll wait "forever" unless there's a shutdown timestamp in effect
        long firstMessageTimeout = (shutdownTime != null) ? shutdownTime.longValue() : Long.MAX_VALUE;
        LogMessage message = messageQueue.dequeue(firstMessageTimeout);
        if (message == null)
            return batch;

        long batchTimeout = System.currentTimeMillis() + batchDelay;
        int batchBytes = 0;
        int batchMsgs = 0;
        while (message != null)
        {
            batchBytes += effectiveSize(message);
            batchMsgs++;

            // if this message would exceed the batch limits, push it back onto the queue
            // the first message must never break this rule -- and shouldn't, as appender checks size
            if (! withinServiceLimits(batchBytes, batchMsgs))
            {
                messageQueue.requeue(message);
                break;
            }

            batch.add(message);
            message = waitForMessage(batchTimeout);
        }

        return batch;
    }


//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    /**
     *  Performs initialization at the start of {@link #run}. Extracted so that
     *  we can be a bit cleaner about try/catch behavior. Returns true if successful,
     *  false if not.
     */
    private boolean initialize()
    {
        try
        {
            dispatchThread = Thread.currentThread();
            createAWSClient();
            return ensureDestinationAvailable();
        }
        catch (Exception ex)
        {
            return initializationFailure("uncaught exception", ex);
        }
    }


    /**
     *  A check for whether we should keep running: either we haven't been shut
     *  down or there's still messages to process
     */
    private boolean keepRunning()
    {
        return (shutdownTime == null)
             ? true
             : shutdownTime.longValue() > System.currentTimeMillis()
               && messageQueue.isEmpty();
    }


    /**
     *  Attempts to read the message queue, waiting until the specified timestamp
     *  (not elapsed time!).
     */
    private LogMessage waitForMessage(long waitUntil)
    {
        long waitTime = waitUntil - System.currentTimeMillis();
        return messageQueue.dequeue(waitTime);
    }


    /**
     *  Requeues all messages in the passed list, preserving order (ie, the first
     *  passed message in the list will be the first in the queue).
     */
    private void requeueMessages(List<LogMessage> messages)
    {
        Collections.reverse(messages);
        for (LogMessage message : messages)
        {
            messageQueue.requeue(message);
        }
    }

}
