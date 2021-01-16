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

package com.kdgregory.logging.aws;

import org.junit.Test;

import net.sf.kdgcommons.test.StringAsserts;

import com.kdgregory.logging.aws.internal.facade.FacadeFactory;
import com.kdgregory.logging.aws.internal.facade.InfoFacade;


/**
 *  Tests retriever operations that require AWS configuration.
 */
public class TestInfoFacadeBasicEnvironment
{

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testAccountId() throws Exception
    {
        InfoFacade facade = FacadeFactory.createFacade(InfoFacade.class);
        String value = facade.retrieveAccountId();

        StringAsserts.assertRegex("retrieved value (was: " + value + ")",
                                  "\\d{12}",
                                  value);
    }


    @Test
    public void testDefaultRegion() throws Exception
    {
        InfoFacade facade = FacadeFactory.createFacade(InfoFacade.class);
        String value = facade.retrieveDefaultRegion();

        // rather than tie this to my default region, I'll see if it "looks right"
        StringAsserts.assertRegex("retrieved value (was: " + value + ")",
                                  "..-.*-\\d",
                                  value);
    }}
