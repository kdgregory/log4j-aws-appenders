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

package com.kdgregory.logging.common.factories;

import java.lang.Thread.UncaughtExceptionHandler;

import com.kdgregory.logging.common.LogWriter;


/**
 *  Creates and starts a new thread for running the LogWriter.
 *  <p>
 *  Appenders expected to create a background thread (via factory) at the same time
 *  they create the logwriter (also via factory), and pass the latter to the former.
 *  In practice, this will happen in an abstract superclass, and the factory allows
 *  that superclass to know nothing of the actual writer type.
 *  <p>
 *  To handle unexpected thread death, the appender must provide an uncaught
 *  exception handler. A typical handler will log the event using the internal
 *  logger, and then shut itself down (the assumption is that recoverable exceptions
 *  will be caught).
 *  <p>
 *  The appender may also specify whether the writer thread should register a shutdown
 *  hook. In general, such a hook will call the writer's <code>stop()</code> method
 *  and then wait for the writer thread to finish.
 *  <p>
 *  <code>DefaultThreadFactory</code> provides a standard implementation of this
 *  interface. This module also provides <code>NullThreadFactory</code> and
 *  <code>InlineThreadFactory</code>, which are used for unit tests.
 */
public interface ThreadFactory
{
    void startLoggingThread(LogWriter writer, boolean useShutdownHook, UncaughtExceptionHandler exceptionHandler);
}
