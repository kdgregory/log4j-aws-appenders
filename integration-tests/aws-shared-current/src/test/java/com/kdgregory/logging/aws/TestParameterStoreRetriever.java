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
import com.amazonaws.services.simplesystemsmanagement.model.DeleteParameterRequest;
import com.amazonaws.services.simplesystemsmanagement.model.*;

import com.kdgregory.logging.aws.internal.retrievers.ParameterStoreRetriever;


/**
 *  Tests the retriever by creating Parameter Store entries. All needed entries are
 *  created in a @BeforeClass, and torn down in the @AfterClass
 */
public class TestParameterStoreRetriever
{
    private final static String BASIC_NAME      = "/TestParameterStoreRetriever/" + UUID.randomUUID().toString();
    private final static String BASIC_VALUE     = "this is a test";

    private final static String LIST_NAME       = "/TestParameterStoreRetriever/" + UUID.randomUUID().toString();
    private final static String LIST_VALUE      = "this,is,a,test";

    private final static String SECURE_NAME     = "/TestParameterStoreRetriever/" + UUID.randomUUID().toString();
    private final static String SECURE_VALUE    = "you should never see me";

    private static AWSSimpleSystemsManagement ssmClient;

//----------------------------------------------------------------------------
//  Helpers
//----------------------------------------------------------------------------

    private static void createParameter(String name, ParameterType type, String value)
    {
        PutParameterRequest putRequest = new PutParameterRequest()
                                         .withName(name)
                                         .withType(type)
                                         .withValue(value);
        ssmClient.putParameter(putRequest);
    }


    private static void deleteParameter(String name)
    {
        DeleteParameterRequest deleteRequest = new DeleteParameterRequest()
                                               .withName(name);
        ssmClient.deleteParameter(deleteRequest);
    }

//----------------------------------------------------------------------------
//  JUnit scaffolding
//----------------------------------------------------------------------------

    @BeforeClass
    public static void init()
    {
        ssmClient = AWSSimpleSystemsManagementClientBuilder.defaultClient();

        createParameter(BASIC_NAME, ParameterType.String, BASIC_VALUE);
        createParameter(LIST_NAME, ParameterType.StringList, LIST_VALUE);
        createParameter(SECURE_NAME, ParameterType.SecureString, SECURE_VALUE);
    }


    @AfterClass
    public static void shutdown()
    {
        deleteParameter(BASIC_NAME);
        deleteParameter(LIST_NAME);
        deleteParameter(SECURE_NAME);

        ssmClient.shutdown();
    }

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testBasicOperation() throws Exception
    {
        ParameterStoreRetriever retriever = new ParameterStoreRetriever();
        assertEquals(BASIC_VALUE, retriever.invoke(BASIC_NAME));
    }


    @Test
    public void testStringList() throws Exception
    {
        ParameterStoreRetriever retriever = new ParameterStoreRetriever();
        assertEquals(LIST_VALUE, retriever.invoke(LIST_NAME));
    }


    @Test
    public void testSecureString() throws Exception
    {
        ParameterStoreRetriever retriever = new ParameterStoreRetriever();
        assertNull(retriever.invoke(SECURE_NAME));
    }
}
