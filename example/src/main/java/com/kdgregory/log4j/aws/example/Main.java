// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.example;

import java.util.Random;
import org.apache.log4j.Logger;


/**
 *  Example program to generate log events of varying levels.
 *  <p>
 *  Invoke with the number of event-generator threads you want (default is 2):
 *  <pre>
 *      java -jar target/aws-appenders-example-1.0.0.jar NUM_THREADS
 *  </pre>
 *  Each thread will generate one message per second, writing a short message
 *  with a random number between 0 and 99. If the value is < 75, the message
 *  is logged at DEBUG level; if between 75 and 95, INFO; 95 or above, WARN.
 *  <p>
 *  Any non-example warnings will be logged to the console.
 *  <p>
 *  Terminate the program when you've seen enough messages written to the logs.
 */
public class Main
{
    private static Logger logger = Logger.getLogger(Main.class);


    public static void main(String[] argv)
    throws Exception
    {
        int numThreads = (argv.length > 0)
                       ? Integer.parseInt(argv[0])
                       : 2;
        for (int ii = 0 ; ii < numThreads ; ii++)
        {
            final Random rnd = new Random(ii);
            Thread t = new Thread(new Runnable()
            {
                @Override
                public void run()
                {

                    while (true)
                    {
                        try
                        {
                            Thread.sleep(1000);
                            int value = rnd.nextInt(100);
                            if (value < 75)
                                logger.debug("value is " + value);
                            else if (value < 95)
                                logger.info("value is " + value);
                            else
                                logger.warn("value is " + value);
                        }
                        catch (InterruptedException ignored) { /* */ }
                    }
                }
            });
            t.setName("example-" + ii);
            t.setDaemon(false);
            t.start();
        }
    }
}
