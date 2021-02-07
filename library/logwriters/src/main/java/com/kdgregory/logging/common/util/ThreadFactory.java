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

package com.kdgregory.logging.common.util;

import java.lang.Thread.UncaughtExceptionHandler;

import com.kdgregory.logging.common.LogWriter;


/**
 *  Creates and starts a new thread for running the LogWriter.
 *  <p>
 *  Appenders create a background thread (via factory) at the same time that they
 *  create the logwriter (also via factory), and pass the latter to the former. In
 *  normal operation they'll use {@link DefaultThreadFactory}. However, tests may
 *  instead use an factory that doesn't actually create a thread (so all operations
 *  happen inline) or use some other configuration.
 *  <p>
 *  To handle unexpected thread death, the appender must provide an uncaught
 *  exception handler. A typical handler logs what happens and then shuts down
 *  the appender(the assumption is that recoverable exceptions will be handled).
 */
public interface ThreadFactory
{
    void startWriterThread(LogWriter writer, UncaughtExceptionHandler exceptionHandler);
}
