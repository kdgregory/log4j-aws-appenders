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

package com.kdgregory.log4j.testhelpers;

import java.lang.Thread.UncaughtExceptionHandler;

import com.kdgregory.aws.logging.common.LogWriter;
import com.kdgregory.aws.logging.common.ThreadFactory;


/**
 *  A {@link ThreadFactory} used for testing: it doesn't invoke the writer at all.
 */
public class NullThreadFactory implements ThreadFactory
{
    @Override
    public void startLoggingThread(LogWriter writer, UncaughtExceptionHandler exceptionHandler)
    {
        // nothing here
    }
}
