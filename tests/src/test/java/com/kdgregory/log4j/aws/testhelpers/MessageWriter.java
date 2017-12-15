// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.testhelpers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;


/**
 *  Writes a sequence of messages to the log. Will be invoked either inline
 *  (for smoketest) or on a thread (for concurrency tests).
 */
public class MessageWriter implements Runnable
{
    /**
     *  This is useful when asserting the messages that were written.
     */
    public final static Pattern PATTERN = Pattern.compile(".*message on thread (\\d+): (\\d+)");


    private Logger logger;
    private int numMessages;

    public MessageWriter(Logger logger, int numMessages)
    {
        this.logger = logger;
        this.numMessages = numMessages;
    }


    @Override
    public void run()
    {
        for (int ii = 0 ; ii < numMessages ; ii++)
        {
            logger.debug("message on thread " + Thread.currentThread().getId() + ": " + ii);
        }
    }


    /**
     *  Helper function to take a collection of writers and invoke them on threads,
     *  then wait for those threads to complete.
     */
    public static void runOnThreads(MessageWriter... writers)
    throws Exception
    {
        List<Thread> threads = new ArrayList<Thread>(writers.length);
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