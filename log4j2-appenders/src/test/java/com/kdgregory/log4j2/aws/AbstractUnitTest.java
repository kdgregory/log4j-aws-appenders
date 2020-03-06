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

package com.kdgregory.log4j2.aws;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import com.kdgregory.log4j2.aws.internal.AbstractAppender;
import com.kdgregory.log4j2.testhelpers.TestableLog4J2InternalLogger;


/**
 *  Common functionality for unit tests.
 */
public abstract class AbstractUnitTest<T extends AbstractAppender<?,?,?>>
{
    // these are provided by constructor
    private String baseResourcePath;
    private String appenderName;

    // these are set by initialize()
    protected Logger logger;
    protected T appender;
    protected TestableLog4J2InternalLogger appenderInternalLogger;


    public AbstractUnitTest(String baseResourcePath, String appenderName)
    {
        this.baseResourcePath = baseResourcePath;
        this.appenderName = appenderName;
    }


    protected void initialize(String testName)
    throws Exception
    {
        String propsName = baseResourcePath + testName + ".xml";
        URI config = ClassLoader.getSystemResource(propsName).toURI();
        assertNotNull("was able to retrieve config", config);

        LoggerContext context = LoggerContext.getContext();
        context.setConfigLocation(config);

        logger = context.getLogger(getClass().getName());

        // sometimes we just want to initialize the logging framework
        if (appenderName != null)
        {
            appender = (T)logger.getAppenders().get(appenderName);
            assertNotNull("was able to retrieve appender", appender);

            // a hack because we don't have a common superclass for testable appenders
            Method m = appender.getClass().getMethod("getInternalLogger");
            appenderInternalLogger = (TestableLog4J2InternalLogger)m.invoke(appender);
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
