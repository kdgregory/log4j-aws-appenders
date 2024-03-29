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

package com.kdgregory.logging.testhelpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *  Writes a sequence of messages to the log. Will be invoked either inline
 *  (for smoketest) or on a thread (for concurrency tests).
 */
public abstract class MessageWriter implements Runnable
{
    // these are useful for other test code, so are public
    public final static String  REGEX   = ".*message from writer (\\d+): (\\d+)";
    public final static Pattern PATTERN = Pattern.compile(REGEX);

    private final static AtomicInteger idGenerator = new AtomicInteger();

    private Logger localLogger = LoggerFactory.getLogger(getClass());

    private int numMessages;
    private int writerId;


    public MessageWriter(int numMessages)
    {
        this.numMessages = numMessages;
        this.writerId = idGenerator.addAndGet(1);
    }


    @Override
    public void run()
    {
        localLogger.debug("writer {} writing {} messages", writerId, numMessages);
        for (int ii = 0 ; ii < numMessages ; ii++)
        {
            writeLogMessage("message from writer " + writerId + ": " + ii);
        }
    }


    /**
     *  Subclasses override this method to write to the module-specific logger.
     */
    protected abstract void writeLogMessage(String message);


    /**
     *  Helper function to take a collection of writers and invoke them on threads,
     *  then wait for those threads to complete.
     */
    public static void runOnThreads(MessageWriter... writers)
    throws Exception
    {
        runOnThreads(Arrays.asList(writers));
    }


    /**
     *  Helper function to take a collection of writers and invoke them on threads,
     *  then wait for those threads to complete.
     */
    public static void runOnThreads(List<MessageWriter> writers)
    throws Exception
    {
        List<Thread> threads = new ArrayList<Thread>(writers.size());
        for (MessageWriter writer : writers)
        {
            Thread thread = new Thread(writer);
            thread.start();
            threads.add(thread);
        }

        for (Thread thread : threads)
        {
            thread.join();
        }
    }


    /**
     *  Retrieves the value of the thread-ID group from the matcher.
     */
    public static Integer getThreadId(Matcher matcher)
    {
        return Integer.valueOf(matcher.group(1));
    }


    /**
     *  Retrieves the value of the message number group from the matcher.
     */
    public static Integer getMessageNumber(Matcher matcher)
    {
        return Integer.valueOf(matcher.group(2));
    }
}
