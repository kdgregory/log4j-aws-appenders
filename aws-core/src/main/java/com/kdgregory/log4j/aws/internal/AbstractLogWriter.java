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

package com.kdgregory.log4j.aws.internal;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.regions.Regions;

import com.kdgregory.log4j.common.DiscardAction;
import com.kdgregory.log4j.common.LogMessage;
import com.kdgregory.log4j.common.LogWriter;
import com.kdgregory.log4j.common.MessageQueue;


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

    private AbstractAppenderStatistics appenderStats;       // used for reporting errors

    private volatile int batchCount;                        // these can be read via accessor methods; they're intended for testing
    private volatile boolean initializationComplete;
    private volatile String factoryMethodUsed;


    public AbstractLogWriter(AbstractAppenderStatistics appenderStats, long batchDelay, int discardThreshold, DiscardAction discardAction)
    {
        this.appenderStats = appenderStats;
        this.batchDelay = batchDelay;
        messageQueue = new MessageQueue(discardThreshold, discardAction);

        this.appenderStats.setMessageQueue(messageQueue);
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
     *  Returns a flag indicating that initialization has completed (whether or not
     *  successful).
     */
    public boolean isInitializationComplete()
    {
        return initializationComplete;
    }


    /**
     *  Returns the factory method used to create the client, if any. Null if
     *  the client was created via constructor.
     */
    public String getClientFactoryUsed() {
        return factoryMethodUsed;
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
        shutdownTime = Long.valueOf(System.currentTimeMillis() + batchDelay);
        if (dispatchThread != null)
        {
            dispatchThread.interrupt();
        }
    }


    @Override
    public void setDiscardThreshold(int value)
    {
        messageQueue.setDiscardThreshold(value);
    }


    @Override
    public void setDiscardAction(DiscardAction value)
    {
        messageQueue.setDiscardAction(value);
    }

//----------------------------------------------------------------------------
//  Implementation of Runnable
//----------------------------------------------------------------------------

    @Override
    public void run()
    {
        if (! initialize())
        {
            messageQueue.setDiscardThreshold(0);
            messageQueue.setDiscardAction(DiscardAction.oldest);
            return;
        }

        // this is set after initialization because the integration tests were telling
        // the writer to shut down while it was still initializing; in the real world
        // that should never happen without the appender being shut down as well

        dispatchThread = Thread.currentThread();

        initializationComplete = true;

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
     *  Records an initialization failure in the appender stats. This method returns
     *  false so that the failure handler can return its result to the caller.
     */
    protected boolean initializationFailure(String message, Exception exception)
    {
        appenderStats.setLastError(message, exception);
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


    /**
     *  Attempts to use a factory method to create the service client.
     *
     *  @param  clientFactoryName   Fully qualified name of a static factory method.
     *                              If empty or null, this function returns null (used
     *                              to handle optionally-configured factories).
     *  @param  expectedClientClass The interface fullfilled by this client.
     *  @param  rethrow             If true, any reflection exceptions will be wrapped
     *                              and rethrown; if false, exceptions return null
     */
    protected <T> T tryClientFactory(String clientFactoryName, Class<T> expectedClientClass, boolean rethrow)
    {
        if ((clientFactoryName == null) || clientFactoryName.isEmpty())
            return null;

        try
        {
            int methodIdx = clientFactoryName.lastIndexOf('.');
            if (methodIdx < 0)
                throw new RuntimeException("invalid AWS client factory specified: " + clientFactoryName);
            Class<?> factoryKlass = Class.forName(clientFactoryName.substring(0, methodIdx));
            Method factoryMethod = factoryKlass.getDeclaredMethod(clientFactoryName.substring(methodIdx + 1));
            T client = expectedClientClass.cast(factoryMethod.invoke(null));
            factoryMethodUsed = clientFactoryName;
            // TODO - report factory creation
//            LogLog.debug(getClass().getSimpleName() + ": created client from factory: " + clientFactoryName);
            return client;
        }
        catch (Exception ex)
        {
            if (rethrow)
                throw new RuntimeException("unable to invoke AWS client factory", ex);
            else
                return null;
        }
    }


    /**
     *  Common support code: attempts to configure client endpoint and/or region.
     *
     *  @param  client      A constructed writer-specific service client.
     *  @param  endpoint    A possibly-null endpoint specification.
     */
    protected <T extends AmazonWebServiceClient> T tryConfigureEndpointOrRegion(T client, String endpoint)
    {
        // explicit endpoint takes precedence over region retrieved from environment
        if (endpoint != null)
        {
            // TODO - report endpoint configuration
//            LogLog.debug(getClass().getSimpleName() + ": configuring endpoint: " + endpoint);
            client.setEndpoint(endpoint);
            return client;
        }

        String region = System.getenv("AWS_REGION");
        if (region != null)
        {
            // TODO - report region configuration
//            LogLog.debug(getClass().getSimpleName() + ": configuring region: " + region);
            client.configureRegion(Regions.fromName(region));
            return client;
        }

        return client;
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
