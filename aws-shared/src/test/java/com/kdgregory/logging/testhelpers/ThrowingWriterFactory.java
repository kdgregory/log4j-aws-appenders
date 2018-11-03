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

import java.util.concurrent.CountDownLatch;

import com.kdgregory.logging.common.LogMessage;
import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.factories.WriterFactory;
import com.kdgregory.logging.common.util.DiscardAction;
import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  This factory creates a LogWriter that throws on its second invocation.
 *  It's used to test the uncaught exception handling in the appender.
 */
public class ThrowingWriterFactory<C,S> implements WriterFactory<C,S>
{
        @Override
        public LogWriter newLogWriter(C ignored1, S ignored2, InternalLogger ignored3)
        {
            return new LogWriter()
            {
                private CountDownLatch appendLatch = new CountDownLatch(2);

                @Override
                public void run()
                {
                    try
                    {
                        appendLatch.await();
                        throw new TestingException("danger, danger Will Robinson!");
                    }
                    catch (InterruptedException ex)
                    { /* nothing to do */ }
                }

                @Override
                public void addMessage(LogMessage message)
                {
                    appendLatch.countDown();
                }

                @Override
                public void stop()
                {
                    // not used
                }

                @Override
                public void setBatchDelay(long value)
                {
                    // not used
                }

                @Override
                public void setDiscardThreshold(int value)
                {
                    // not used
                }

                @Override
                public void setDiscardAction(DiscardAction value)
                {
                    // not used
                }
            };
        }
}
