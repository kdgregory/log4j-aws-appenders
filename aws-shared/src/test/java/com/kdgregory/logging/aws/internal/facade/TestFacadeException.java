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

package com.kdgregory.logging.aws.internal.facade;

import org.junit.Test;
import static org.junit.Assert.*;


public class TestFacadeException
{
    @Test
    public void testFullySpecifiedInvocation() throws Exception
    {
        RuntimeException cause = new RuntimeException();
        FacadeException ex = new FacadeException("test message", cause, true, "myFunction", "foo", "bar");

        assertEquals("actual message",          "myFunction(foo,bar): test message",    ex.getMessage());
        assertSame("cause retained",            cause,                                  ex.getCause());
        assertEquals("function name retained",  "myFunction",                           ex.getFunctionName());
        assertTrue("is retryable",                                                      ex.isRetryable());
    }


    @Test
    public void testMissingFunctionInfo() throws Exception
    {
        FacadeException ex = new FacadeException("test message", null, false, null);

        assertEquals("actual message",  "test message",     ex.getMessage());
        assertNull("no cause",                              ex.getCause());
        assertNull("no function name",                      ex.getFunctionName());
        assertFalse("is retryable",                         ex.isRetryable());
    }
}
