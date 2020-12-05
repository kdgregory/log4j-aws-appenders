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

package com.kdgregory.logging.aws.facade;

import org.junit.Test;
import static org.junit.Assert.*;

import com.kdgregory.logging.aws.facade.v1.CloudWatchFacadeImpl;
import com.kdgregory.logging.aws.facade.v1.KinesisFacadeImpl;
import com.kdgregory.logging.aws.facade.v1.SNSFacadeImpl;
import com.kdgregory.logging.aws.internal.facade.CloudWatchFacade;
import com.kdgregory.logging.aws.internal.facade.FacadeFactory;
import com.kdgregory.logging.aws.internal.facade.KinesisFacade;
import com.kdgregory.logging.aws.internal.facade.SNSFacade;


/**
 *  Verifies that the shared facade factory returns the correct implementation
 *  classes for this library.
 */
public class TestFacadeFactory
{
    @Test
    public void testSupportedFacadeTypes() throws Exception
    {
        // we just care that an instance of the correct type has been returned
        // the cast will throw if it's the wrong type

        assertNotNull("CloudWatchFacade",   (CloudWatchFacadeImpl)FacadeFactory.createFacade(CloudWatchFacade.class, null));
        assertNotNull("KinesisFacade",      (KinesisFacadeImpl)FacadeFactory.createFacade(KinesisFacade.class, null));
        assertNotNull("SNSFacade",          (SNSFacadeImpl)FacadeFactory.createFacade(SNSFacade.class, null));
    }


    @Test
    public void testBogusFacadeType() throws Exception
    {
        try
        {
            FacadeFactory.createFacade(String.class, null);
            fail("should have thrown");
        }
        catch (IllegalArgumentException ex)
        {
            assertEquals("exception message",
                         "no implementation class for java.lang.String",
                         ex.getMessage());
        }
    }

}
