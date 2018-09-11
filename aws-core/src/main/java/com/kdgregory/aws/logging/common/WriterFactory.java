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

package com.kdgregory.aws.logging.common;

/**
 *  Defines a function to create a {@link LogWriter}.
 *  <p>
 *  The factory is passed two appender-specific objects: the first provides
 *  configuration for the writer, the second is used by the writer to report
 *  statistics.
 */
public interface WriterFactory<C,S>
{
    LogWriter newLogWriter(C config, S stats);
}
