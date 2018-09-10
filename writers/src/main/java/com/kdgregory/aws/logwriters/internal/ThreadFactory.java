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

package com.kdgregory.aws.logwriters.internal;

import java.lang.Thread.UncaughtExceptionHandler;

import com.kdgregory.aws.logwriters.common.LogWriter;


/**
 *  Creates and starts a new thread for running the LogWriter.
 *  <p>
 *  Appenders are constructed with an instance of {@link DefaultThreadFactory}
 *  (or  perhaps an appender-specific factory), and lazily call this factory on
 *  first append. The thread is not returned by the factory; it should exit
 *  normally when the appender calls {@link LogWriter#stop}.
 *  <p>
 *  To handle unexpected thread death, the appender must provide an uncaught
 *  exception handler. A typical handler will log the event using Log4J's internal
 *  logger, and then create a new writer.
 *  <p>
 *  In the test helpers, you will find <code>MockThreadFactory</code>,  which does
 *  not actually start a thread.
 */
public interface ThreadFactory
{
    void startLoggingThread(LogWriter writer, UncaughtExceptionHandler exceptionHandler);
}
