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

package com.kdgregory.logging.aws.testhelpers;

import java.util.ArrayList;
import java.util.List;

import com.kdgregory.logging.common.util.InternalLogger;


/**
 *  An implementation of <code>InternalLogger</code> that retains messages for
 *  analysis by test code.
 */
public class TestableInternalLogger
implements InternalLogger
{
    public List<String> debugMessages = new ArrayList<String>();
    public List<String> warnMessages = new ArrayList<String>();
    public List<String> errorMessages = new ArrayList<String>();
    public List<Throwable> errorExceptions = new ArrayList<Throwable>();


    @Override
    public void debug(String message)
    {
        debugMessages.add(message);
    }



    @Override
    public void warn(String message)
    {
        warnMessages.add(message);
    }



    @Override
    public void error(String message, Throwable ex)
    {
        errorMessages.add(message);
        errorExceptions.add(ex);
    }

}
