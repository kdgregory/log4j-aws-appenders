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

package com.kdgregory.logging.aws.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.internal.Utils;
import com.kdgregory.logging.common.util.InternalLogger;
import com.kdgregory.logging.common.util.MessageQueue;
import com.kdgregory.logging.common.util.MessageQueue.DiscardAction;


/**
 *  Manages common LogWriter activities.
 */
public abstract class AbstractLogWriter
    <
    ConfigType extends AbstractWriterConfig<ConfigType>,
    StatsType extends AbstractWriterStatistics
    >
implements LogWriter
{
    // flag value for shutdownTime
    private final static long NEVER_SHUTDOWN = Long.MAX_VALUE;

    // these three are provided to constructor, used both here and in subclass
    protected ConfigType config;
    protected StatsType stats;
    protected InternalLogger logger;

    // created during initialization
    private MessageQueue messageQueue;
    private Thread dispatchThread;

    // updated by stop()
    private volatile long shutdownTime = NEVER_SHUTDOWN;

    // this is set when shutdown hooks are in effect, so that the writer
    // can remove it as part of cleanup
    private volatile Thread shutdownHook;

    // intended for testing; set to true on either success or failure
    private volatile boolean initializationComplete;

    // exposed for testing
    private volatile boolean isRunning;

    // exposed for testing
    private volatile int batchCount;


    public AbstractLogWriter(ConfigType config, StatsType appenderStats, InternalLogger logger)
    {
        this.config = config;
        this.stats = appenderStats;
        this.logger = logger;

        messageQueue = new MessageQueue(config.getDiscardThreshold(), config.getDiscardAction());
        this.stats.setMessageQueue(messageQueue);
    }

//----------------------------------------------------------------------------
//  Accessors
//----------------------------------------------------------------------------

    /**
     *  Returns whether or not the writer is currently running. This is intended
     *  for testing.
     */
    public boolean isRunning()
    {
        return isRunning;
    }


    /**
     *  Returns the current batch delay. This is intended for testing.
     */
    public long getBatchDelay()
    {
        return config.getBatchDelay();
    }


    /**
     *  Returns the number of batches processed. This is intended for testing
     */
    public int getBatchCount()
    {
        return batchCount;
    }

//----------------------------------------------------------------------------
//  Implementation of Runnable
//----------------------------------------------------------------------------

    @Override
    public void run()
    {
        logger.debug("log writer starting (thread: " + Thread.currentThread().getName() + ")");

        if (! initialize())
        {
            logger.error("log writer failed to initialize (thread: " + Thread.currentThread().getName() + ")", null);
            return;
        }

        isRunning = true;
        logger.debug("log writer initialization complete (thread: " + Thread.currentThread().getName() + ")");

        // to avoid any mid-initialization interrupts, we don't set the thread until ready to run
        // (was affecting integration tests, shouldn't be an issue in real-world use)

        dispatchThread = Thread.currentThread();

        // the do-while loop ensures that we attempt to process at least one batch, even if
        // the writer is started and immediately stopped; that's not likely to happen in the
        // real world, but was causing problems with the integration tests (which quickly
        // transition writers)

        do
        {
            if (config.getSynchronousMode())
            {
                long timeToShutdown = shutdownTime - System.currentTimeMillis();
                Utils.sleepQuietly(timeToShutdown);
            }
            else
            {
                processBatch(shutdownTime);
            }
        } while (keepRunning());

        cleanup();
        isRunning = false;
        logger.debug("log-writer shut down (thread: " + Thread.currentThread().getName()
                     + " (#" + Thread.currentThread().getId() + ")");
    }

//----------------------------------------------------------------------------
//  Implementation of LogWriter
//----------------------------------------------------------------------------

    @Override
    public void setBatchDelay(long value)
    {
        config.setBatchDelay(value);
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


    @Override
    public boolean isSynchronous()
    {
        return config.getSynchronousMode();
    }


    @Override
    public boolean waitUntilInitialized(long millisToWait)
    {
        long timeoutAt = System.currentTimeMillis() + millisToWait;
        while (! initializationComplete && (System.currentTimeMillis() < timeoutAt))
        {
            try
            {
                Thread.sleep(100);
            }
            catch (InterruptedException ex)
            {
                return false;
            }
        }
        return initializationComplete;
    }


    @Override
    public void addMessage(LogMessage message)
    {
        if (message.size() == 0)
        {
            logger.warn("discarded empty message");
            return;
        }

        if (message.size() > maxMessageSize())
        {
            stats.incrementOversizeMessages();
            if (config.getTruncateOversizeMessages())
            {
                logger.warn("truncated oversize message (" + message.size() + " bytes to " + maxMessageSize() + ")");
                message.truncate(maxMessageSize());
            }
            else
            {
                logger.warn("discarded oversize message (" + message.size() + " bytes, limit is " + maxMessageSize() + ")");
                return;
            }
        }

        messageQueue.enqueue(message);

        if (config.getSynchronousMode())
        {
            processBatch(System.currentTimeMillis());
        }
    }


    @Override
    public void stop()
    {
        // if someone else already called stop then we shouldn't do it again
        if (shutdownTime != NEVER_SHUTDOWN)
            return;

        shutdownTime = System.currentTimeMillis() + config.getBatchDelay();
        if (dispatchThread != null)
        {
            dispatchThread.interrupt();
        }
    }


    @Override
    public void waitUntilStopped(long millisToWait)
    {
        try
        {
            if ((dispatchThread != null) && (dispatchThread != Thread.currentThread()))
            {
                dispatchThread.join(millisToWait);
            }
        }
        catch (InterruptedException ignored)
        {
            // return silently
        }
    }

//----------------------------------------------------------------------------
//  Internals -- these are protected so they can be overridden for testing
//----------------------------------------------------------------------------

    /**
     *  A check for whether we should keep running: either we haven't been shut
     *  down or there's still messages to process
     */
    protected boolean keepRunning()
    {
        return shutdownTime > System.currentTimeMillis()
            || ! messageQueue.isEmpty();
    }


    /**
     *  Called before the main loop, to ensure that the writer is able to perform
     *  its job. If unable, reconfigures the writer to discard all message.
     */
    protected boolean initialize()
    {
        boolean success = true;

        try
        {
            success = ensureDestinationAvailable();
            optAddShutdownHook();
        }
        catch (Exception ex)
        {
            reportError("exception in initializer", ex);
            success = false;
        }

        if (! success)
        {
            messageQueue.setDiscardThreshold(0);
            messageQueue.setDiscardAction(DiscardAction.oldest);
        }

        initializationComplete = true;
        return success;
    }


    /**
     *  Called from the main loop to build a batch of messages and send them.
     *  Waits until the specified timestamp for the first message, then waits
     *  for the batch delay before passing the messages to {@link #sendBatch}.
     */
    protected synchronized void processBatch(long waitUntil)
    {
        List<LogMessage> currentBatch = buildBatch(waitUntil);
        if (currentBatch.size() > 0)
        {
            batchCount++;
            List<LogMessage> failures = sendBatch(currentBatch);
            requeueMessages(failures);

            // note: order of updates is important to avoid race conditions in tests
            stats.setMessagesRequeuedLastBatch(failures.size());
            stats.setMessagesSentLastBatch(currentBatch.size() - failures.size());
            stats.updateMessagesSent(currentBatch.size() - failures.size());
        }
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
    protected List<LogMessage> buildBatch(long waitUntil)
    {
        // presizing to a small-but-possible size to avoid repeated resizes
        List<LogMessage> batch = new ArrayList<LogMessage>(512);

        // we'll wait "forever" unless there's a shutdown timestamp in effect
        LogMessage message = waitForMessage(waitUntil);
        if (message == null)
            return batch;

        long batchTimeout = System.currentTimeMillis() + config.getBatchDelay();
        int batchBytes = 0;
        int batchMsgs = 0;
        while (message != null)
        {
            batchBytes += effectiveSize(message);
            batchMsgs++;

            // if this message would exceed the batch limits, push it back onto the queue
            // the first message must never break this rule -- and shouldn't, as long as
            // appender checks size
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
     *  Attempts to read the message queue, waiting until the specified timestamp
     *  (not elapsed time!).
     */
    private LogMessage waitForMessage(long waitUntil)
    {
        long waitTime = Math.max(1, waitUntil - System.currentTimeMillis());
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


    /**
     *  Called after the main loop exits, to shut down the AWS client. Also removes
     *  any shutdown hook (this is only relevant when the logging framework has been
     *  shut down before the JVM).
     */
    private void cleanup()
    {
        stopAWSClient();

        if (shutdownHook != null)
        {
            try
            {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            }
            catch (Exception ignored)
            {
                // we expect an IllegalThreadStateException
            }
            finally
            {
                shutdownHook = null;
            }
        }
    }


    /**
     *  If the writer is configured to use shutdown hooks, adds one.
     */
    private void optAddShutdownHook()
    {
        if (config.getUseShutdownHook())
        {
            shutdownHook = new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    logger.debug("shutdown hook invoked");
                    setBatchDelay(1);
                    AbstractLogWriter.this.stop();
                    try
                    {
                        if (dispatchThread != null)
                            dispatchThread.join();
                    }
                    catch (InterruptedException e)
                    {
                        // we've done our best, que sera sera
                    }
                }
            });
            shutdownHook.setName(Thread.currentThread().getName() + "-shutdownHook");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }
    }

//----------------------------------------------------------------------------
//  Subclass hooks
//----------------------------------------------------------------------------

    /**
     *  Verifies that the logging destination is available (which may involve
     *  creating it). Return <code>true</code> if successful, <code>false</code>
     *  if not (which will cause the appender to stop running).
     */
    protected abstract boolean ensureDestinationAvailable();


    /**
     *  Sends a batch of messages. The subclass is responsible for returning
     *  any messages that weren't sent, in order, so that they can be requeued.
     */
    protected abstract List<LogMessage> sendBatch(List<LogMessage> currentBatch);


    /**
     *  Calculates the effective size of the message. This includes the message
     *  bytes plus any overhead. It is used to calculate the number of bytes the
     *  message will add to a batch.
     */
    protected abstract int effectiveSize(LogMessage message);


    /**
     *  Determines whether the provided batch size or number of messages would
     *  exceed the service's limits.
     */
    protected abstract boolean withinServiceLimits(int batchBytes, int numMessages);


    /**
     *  This is called when the logwriter is stopped, to explicitly close the
     *  AWS service client. It must be implemented by the subclass because we
     *  don't know the actual type and there's no abstract super-interface.
     */
    protected abstract void stopAWSClient();

//----------------------------------------------------------------------------
//  Subclass helpers
//----------------------------------------------------------------------------

    /**
     *  Reports an operational error to both the internal logger and the stats
     *  bean.
     */
    protected void reportError(String message, Throwable exception)
    {
        logger.error(message, exception);
        stats.setLastError(message, exception);
    }
}
