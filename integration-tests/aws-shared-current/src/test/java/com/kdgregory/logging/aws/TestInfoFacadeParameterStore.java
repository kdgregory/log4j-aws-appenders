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

import java.util.UUID;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.ParameterType;

import com.kdgregory.logging.aws.internal.facade.FacadeFactory;
import com.kdgregory.logging.aws.internal.facade.InfoFacade;
import com.kdgregory.logging.testhelpers.ParameterStoreTestHelper;


/**
 *  Tests the retriever by creating Parameter Store entries. All needed entries are
 *  created in a @BeforeClass, and torn down in the @AfterClass
 */
public class TestInfoFacadeParameterStore
{
    private final static String BASIC_NAME      = "/TestParameterStoreRetriever/" + UUID.randomUUID().toString();
    private final static String BASIC_VALUE     = "this is a test";

    private final static String LIST_NAME       = "/TestParameterStoreRetriever/" + UUID.randomUUID().toString();
    private final static String LIST_VALUE      = "this,is,a,test";

    private final static String SECURE_NAME     = "/TestParameterStoreRetriever/" + UUID.randomUUID().toString();
    private final static String SECURE_VALUE    = "you should never see me";

    private static AWSSimpleSystemsManagement ssmClient;

//----------------------------------------------------------------------------
//  JUnit scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void init()
    {
        ssmClient = AWSSimpleSystemsManagementClientBuilder.defaultClient();

        ParameterStoreTestHelper.createParameter(ssmClient, BASIC_NAME, ParameterType.String, BASIC_VALUE);
        ParameterStoreTestHelper.createParameter(ssmClient, LIST_NAME, ParameterType.StringList, LIST_VALUE);
        ParameterStoreTestHelper.createParameter(ssmClient, SECURE_NAME, ParameterType.SecureString, SECURE_VALUE);
    }


    @AfterClass
    public static void shutdown()
    {
        ParameterStoreTestHelper.deleteParameter(ssmClient, BASIC_NAME);
        ParameterStoreTestHelper.deleteParameter(ssmClient, LIST_NAME);
        ParameterStoreTestHelper.deleteParameter(ssmClient, SECURE_NAME);

        ssmClient.shutdown();
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testBasicOperation() throws Exception
    {
        InfoFacade facade = FacadeFactory.createFacade(InfoFacade.class);
        String value = facade.retrieveParameter(BASIC_NAME);
        assertEquals(BASIC_VALUE, value);
    }


    @Test
    public void testStringList() throws Exception
    {
        InfoFacade facade = FacadeFactory.createFacade(InfoFacade.class);
        String value = facade.retrieveParameter(LIST_NAME);
        assertEquals(LIST_VALUE, value);
    }


    @Test
    public void testSecureString() throws Exception
    {
        InfoFacade facade = FacadeFactory.createFacade(InfoFacade.class);
        String value = facade.retrieveParameter(SECURE_NAME);
        assertNull(value);
    }
}
