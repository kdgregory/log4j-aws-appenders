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

package com.kdgregory.logging.aws.internal.retrievers;

import org.junit.Test;

import static org.junit.Assert.*;

import net.sf.kdgcommons.test.SelfMock;

import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityResult;

/**
 *  Mock-object tests.
 */
public class TestAccountIdRetriever
{

    @Test
    public void testAccountIdRetrieverHappyPath() throws Exception
    {
        AccountIdRetriever retriever = new AccountIdRetriever();

        assertNull("no exception",          retriever.exception);
        assertSame("client class",          AWSSecurityTokenServiceClient.class,        retriever.clientKlass);
        assertSame("request class",         GetCallerIdentityRequest.class,             retriever.requestKlass);
        assertSame("response class",        GetCallerIdentityResult.class,              retriever.responseKlass);

        retriever.clientKlass = TestAccountIdRetrieverHappyPath.class;

        assertEquals("retrieved value",     TestAccountIdRetrieverHappyPath.ACCOUNT_ID, retriever.invoke());
    }


    public static class TestAccountIdRetrieverHappyPath
    extends SelfMock<AWSSecurityTokenServiceClient>
    {
        public final static String ACCOUNT_ID = "123456789012";

        public TestAccountIdRetrieverHappyPath()
        {
            super(AWSSecurityTokenServiceClient.class);
        }

        @SuppressWarnings("unused")
        public GetCallerIdentityResult getCallerIdentity(GetCallerIdentityRequest request)
        {
            return new GetCallerIdentityResult()
                   .withAccount(ACCOUNT_ID);
        }
    }


    @Test
    public void testAccountIdRetrieverException() throws Exception
    {
        AccountIdRetriever retriever = new AccountIdRetriever();

        assertNull("no exception",          retriever.exception);
        assertSame("client class",          AWSSecurityTokenServiceClient.class,        retriever.clientKlass);
        assertSame("request class",         GetCallerIdentityRequest.class,             retriever.requestKlass);
        assertSame("response class",        GetCallerIdentityResult.class,              retriever.responseKlass);

        retriever.clientKlass = TestAccountIdRetrieverException.class;

        assertNull("retrieved value",       retriever.invoke());
    }


    public static class TestAccountIdRetrieverException
    extends SelfMock<AWSSecurityTokenServiceClient>
    {
        public TestAccountIdRetrieverException()
        {
            super(AWSSecurityTokenServiceClient.class);
        }

        @SuppressWarnings("unused")
        public GetCallerIdentityResult getCallerIdentity(GetCallerIdentityRequest request)
        {
            // for modern SDKs this is AWSSecurityTokenServiceException, but that's not in 1.11.0
            // any exception will do in a pinch...
            throw new RuntimeException("message not important");
        }
    }
}
