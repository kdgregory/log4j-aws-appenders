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

package com.kdgregory.log4j.aws.example;

import java.lang.management.ManagementFactory;
import java.util.Random;

import javax.management.ObjectName;

import org.apache.log4j.Logger;
import org.apache.log4j.jmx.HierarchyDynamicMBean;

import com.kdgregory.log4j.aws.StatisticsMBean;


/**
 *  Example program to generate log events of varying levels.
 *  <p>
 *  Invoke with the number of event-generator threads you want (default is 2):
 *  <pre>
 *      java -jar target/aws-appenders-example-1.0.0.jar NUM_THREADS
 *  </pre>
 *  Each thread will generate one message per second, writing a short message
 *  with a random number between 0 and 99. If the value is < 65, the message
 *  is logged at DEBUG level; if between 65 and 84, INFO; 85 to 95, WARN; and
 *  over 95 is ERROR.
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

        ManagementFactory.getPlatformMBeanServer().createMBean(
                HierarchyDynamicMBean.class.getName(),
                new ObjectName("log4j:name=Config"));

        ManagementFactory.getPlatformMBeanServer().createMBean(
                StatisticsMBean.class.getName(),
                new ObjectName("log4j:name=Statistics"));

        for (int ii = 0 ; ii < numThreads ; ii++)
        {
            Thread t = new Thread(new LogGeneratorRunnable(new Random(ii)));
            t.setName("example-" + ii);
            t.setDaemon(false);
            t.start();
        }
    }


    private static class LogGeneratorRunnable
    implements Runnable
    {
        private Random rnd;

        public LogGeneratorRunnable(Random rnd)
        {
            this.rnd = rnd;
        }

        @Override
        public void run()
        {
            while (true)
            {
                try
                {
                    Thread.sleep(1000);
                    int value = rnd.nextInt(100);
                    if (value < 65)
                        logger.debug("value is " + value);
                    else if (value < 85)
                        logger.info("value is " + value);
                    else if (value < 95)
                        logger.warn("value is " + value);
                    else
                        logger.error("value is " + value);
                }
                catch (InterruptedException ignored) { /* */ }
            }
        }
    }
}
