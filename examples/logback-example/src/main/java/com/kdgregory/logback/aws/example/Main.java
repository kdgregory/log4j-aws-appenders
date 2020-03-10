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

package com.kdgregory.logback.aws.example;

import java.lang.management.ManagementFactory;
import java.util.Random;

import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kdgregory.logback.aws.StatisticsMBean;


/**
 *  Example program to generate log events of varying levels.
 *  <p>
 *  Invoke with the number of event-generator threads you want (default is 2):
 *  <pre>
 *      java -jar target/aws-appenders-example-1.0.0.jar NUM_THREADS
 *  </pre>
 *  
 *  Each thread will take a random walk, starting at the value 50 and moving up
 *  or down by a small amount at each step. When the current value is in the
 *  range 10..90, the program emits a debug log message. When in the range 0..9
 *  or 91..100, it emits a warning message. If the value moves outside of the
 *  range 0..100, the program emits an error message and resets the value to the
 *  bound.
 *  <p>
 *  Terminate the program when you've seen enough messages written to the logs.
 */
public class Main
{
    private static Logger logger = LoggerFactory.getLogger(Main.class);


    public static void main(String[] argv)
    throws Exception
    {
        int numThreads = (argv.length > 0)
                       ? Integer.parseInt(argv[0])
                       : 2;
                       
        ManagementFactory.getPlatformMBeanServer().createMBean(
                StatisticsMBean.class.getName(),
                new ObjectName("com.kdgregory.logback.aws:name=statistics"));

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
        private int value = 50;

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
                    updateValue();
                    Thread.sleep(1000);
                }
                catch (InterruptedException ignored) { /* */ }
            }
        }
        
        private void updateValue()
        {
            value += 2 - rnd.nextInt(5);
            if (value < 0)
            {
                logger.error("value is " + value + "; was reset to 0");
                value = 0;
            }
            else if (value > 100)
            {
                logger.error("value is " + value + "; was reset to 100");
                value = 100;
            }
            else if ((value <= 10) || (value >= 90))
            {
                logger.warn("value is " + value);
            }
            else
            {
                logger.debug("value is " + value);
            }
        }
    }
}
