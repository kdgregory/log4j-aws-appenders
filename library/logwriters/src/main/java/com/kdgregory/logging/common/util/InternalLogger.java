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


/**
 *  The appender is expected to provide the writer with an instance of this
 *  interface, used for logging status and error messages.
 */
public interface InternalLogger
{
    /**
     *  Called to log general status messages.
     */
    public void debug(String message);


    /**
     *  Called to log warning messages. These may be translated to either debug
     *  or error messages if the implementation does not support warnings.
     */
    public void warn(String message);


    /**
     *  Called to log warning messages that include an exception. These may be
     *  translated to either debug or error messages if the implementation does
     *  not support warnings.
     */
    public void warn(String message, Throwable ex);


    /**
     *  Called to log error messages. Exception may be null.
     */
    public void error(String message, Throwable ex);
}
