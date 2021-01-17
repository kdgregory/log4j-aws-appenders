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

package com.kdgregory.logback.aws;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

import org.slf4j.LoggerFactory;

import com.kdgregory.logback.aws.internal.AbstractAppender;
import com.kdgregory.logback.testhelpers.TestableLogbackInternalLogger;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;


/**
 *  Common functionality for unit tests.
 */
public class AbstractUnitTest<T extends AbstractAppender<?,?,?,?>>
{
    // these are provided by constructor
    private String baseResourcePath;
    private String appenderName;

    // these are set by initialize()
    protected Logger logger;
    protected T appender;
    protected TestableLogbackInternalLogger appenderInternalLogger;


    protected AbstractUnitTest(String baseResourcePath, String appenderName)
    {
        this.baseResourcePath = baseResourcePath;
        this.appenderName = appenderName;
    }


    protected void initialize(String testName)
    throws Exception
    {
        String propsName = baseResourcePath + testName + ".xml";
        URL config = ClassLoader.getSystemResource(propsName);
        assertNotNull("was able to retrieve config", config);

        LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
        context.reset();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(config);

        logger = context.getLogger(getClass());

        // sometimes we just want to initialize the logging framework
        if (appenderName != null)
        {
            appender = (T)logger.getAppender(appenderName);

            // a hack because we don't have a common superclass for testable appenders
            Method m = appender.getClass().getMethod("getInternalLogger");
            appenderInternalLogger = (TestableLogbackInternalLogger)m.invoke(appender);
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

