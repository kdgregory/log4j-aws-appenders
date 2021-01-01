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

import java.lang.reflect.Field;

import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.ClassUtil;

import com.kdgregory.logging.aws.internal.AbstractLogWriter;
import com.kdgregory.logging.aws.internal.AbstractWriterStatistics;
import com.kdgregory.logging.common.LogWriter;


/**
 *  Common utility methods for integration tests.
 */
public class CommonTestHelper
{
    /**
     *  Waits until the passed appender's writer (1) exists, and (2) has been
     *  initialized. As you might guess, this uses a lot of reflection.
     */
    public static <T extends LogWriter> void waitUntilWriterInitialized(Object appender, Class<T> writerKlass, long timeoutMillis)
    throws Exception
    {
        T writer = null;
        long timeoutAt = System.currentTimeMillis() + timeoutMillis;

        while (System.currentTimeMillis() < timeoutAt)
        {
            writer = ClassUtil.getFieldValue(appender, "writer", writerKlass);
            if ((writer != null) && writer.waitUntilInitialized(timeoutAt - System.currentTimeMillis()))
                return;

            Thread.sleep(100);
        }

        fail("writer not initialized within timeout");
    }


    /**
     *  Waits until the passed statistics object shows that the desired number of messages have been sent.
     */
    public static void waitUntilMessagesSent(AbstractWriterStatistics stats, int expectedMessages, long timeoutMillis)
    throws Exception
    {
        long timeoutAt = System.currentTimeMillis() + timeoutMillis;

        while (System.currentTimeMillis() < timeoutAt)
        {
            if (stats.getMessagesSent() == expectedMessages)
                return;

            Thread.sleep(100);
        }

        fail("messages not sent within timeout: expected " + expectedMessages + ", was " + stats.getMessagesSent());
    }


    /**
     *  Attempts to retrieve the writer's credentials provider. This is a hack to support
     *  assumed-role tests, since there's no way to determine whether a role was actually
     *  used to perform an operation (both CloudTrail and the role's "last accessed"
     *  timestamp are updated long after the test completes).
     */
    public static Class<?> getCredentialsProviderClass(AbstractLogWriter<?,?> writer)
    throws Exception
    {
        Object client = ClassUtil.getFieldValue(writer, "client", Object.class);
        Field cpField = client.getClass().getDeclaredField("awsCredentialsProvider");
        cpField.setAccessible(true);
        Object provider = cpField.get(client);
        return provider.getClass();
    }
}
