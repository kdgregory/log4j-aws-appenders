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


// verifies that th facade factory properly handles a missing implementation
// (if this succeeds, the dependencies on this module are incorrect!)
public class TestFacadeFactory
{
    @Test
    public void testMissingFacadeType() throws Exception
    {
        try
        {
            FacadeFactory.createFacade(CloudWatchFacade.class, null);
            fail("should have thrown");
        }
        catch (IllegalArgumentException ex)
        {
            assertEquals("exception message",
                         "no implementation class for " + CloudWatchFacade.class.getName(),
                         ex.getMessage());
        }
    }
}
