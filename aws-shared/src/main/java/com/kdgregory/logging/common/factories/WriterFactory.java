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

import com.kdgregory.logging.common.LogWriter;
import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  Defines a function to create a {@link LogWriter}. Each of the writers
 *  defines its own implementation, which returns the concrete writer object.
 *  <p>
 *  This class exists primarily to support testing: appenders are constructed
 *  with the default implementation, but tests replace with an implementation
 *  that creates a "testable" writer.
 */
public interface WriterFactory<C,S>
{
    /**
     *  @param  config  A writer-specific configuration object.
     *  @param  stats   A writer-specific statistics object.
     *  @param  logger  Used by the writer to log its actions.
     */
    LogWriter newLogWriter(C config, S stats, InternalLogger logger);
}
