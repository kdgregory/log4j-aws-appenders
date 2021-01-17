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

package com.kdgregory.log4j.aws;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

import com.kdgregory.log4j.aws.internal.AbstractAppender;
import com.kdgregory.log4j.testhelpers.TestableLog4JInternalLogger;


/**
 *  Common functionality for unit tests.
 */
public class AbstractUnitTest<T extends AbstractAppender<?,?,?>>
{
    // these are provided by constructor
    private String baseResourcePath;
    private String appenderName;

    // these are set by initialize()
    protected Logger logger;
    protected T appender;
    protected TestableLog4JInternalLogger appenderInternalLogger;


    public AbstractUnitTest(String baseResourcePath, String appenderName)
    {
        this.baseResourcePath = baseResourcePath;
        this.appenderName = appenderName;
    }


    protected void initialize(String testName)
    throws Exception
    {
        String propsName = baseResourcePath + testName + ".properties";
        URL config = ClassLoader.getSystemResource(propsName);
        assertNotNull("was able to retrieve config", config);
        PropertyConfigurator.configure(config);

        logger = Logger.getLogger(getClass());

        // sometimes we just want to initialize the logging framework
        if (appenderName != null)
        {
            Logger rootLogger = Logger.getRootLogger();
            appender = (T)rootLogger.getAppender(appenderName);

            // a hack because we don't have a common superclass for testable appenders
            Method m = appender.getClass().getMethod("getInternalLogger");
            appenderInternalLogger = (TestableLog4JInternalLogger)m.invoke(appender);
        }
    }


    protected void runLoggingThreads(final int numThreads, final int messagesPerThread)
    throws Exception
    {
        List<Thread> threads = new ArrayList<Thread>();
        for (int ii = 0 ; ii < numThreads ; ii++)
        {
            threads.add(new Thread(new Runnable() {
                @Override
                public void run()
                {
                    for (int jj = 0 ; jj < messagesPerThread ; jj++)
                    {
                        logger.debug(Thread.currentThread().getName() + " " + jj);
                        Thread.yield();
                    }
                }
            }));
        }

        for (Thread thread : threads)
            thread.start();

        for (Thread thread : threads)
            thread.join();
    }


}
