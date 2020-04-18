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

package com.kdgregory.logging.aws.internal;

import org.junit.Test;
import static org.junit.Assert.*;


public class TestReflectionBasedInvoker
{
    @Test
    public void testHappyPath() throws Exception
    {
        final String testValue = "test";
        ReflectionBasedInvoker retriever = new ReflectionBasedInvoker(HappyPathClient.class.getName(),
                                                    HappyPathRequest.class.getName(),
                                                    HappyPathResponse.class.getName());

        assertNull("no exception reported", retriever.exception);
        assertSame("client class",          HappyPathClient.class,      retriever.clientKlass);
        assertSame("request class",         HappyPathRequest.class,     retriever.requestKlass);
        assertSame("response class",        HappyPathResponse.class,    retriever.responseKlass);

        Object client = retriever.instantiate(retriever.clientKlass);
        assertNotNull("instantiate() succeeded", client);

        Object request = retriever.instantiate(retriever.requestKlass);
        retriever.setRequestValue(request, "setValue", String.class, testValue);
        Object response = retriever.invokeRequest(client, "doSomething", request);
        String result = retriever.getResponseValue(response, "getValue", String.class);

        assertEquals("returned result", testValue, result);
    }


    public static class HappyPathClient
    {
        public HappyPathResponse doSomething(HappyPathRequest request)
        {
            return new HappyPathResponse(request.value);
        }
    }


    public static class HappyPathRequest
    {
        protected String value;

        public void setValue(String value)
        {
            this.value = value;
        }
    }


    public static class HappyPathResponse
    {
        private String value;

        public HappyPathResponse(String value)
        {
            this.value = value;
        }

        public String getValue()
        {
            return value;
        }
    }


    @Test
    public void testBaseRetrieverBogusClass() throws Exception
    {
        ReflectionBasedInvoker retriever = new ReflectionBasedInvoker("com.example.Bogus", "com.example.Bogus", "com.example.Bogus");

        assertNotNull("exception reported", retriever.exception);
        assertNull("client class",          retriever.clientKlass);
        assertNull("request class",         retriever.requestKlass);
        assertNull("response class",        retriever.responseKlass);

        // for these we pass a bogus class; should silently fail due to prior exception
        assertNull("instantiate()",         retriever.instantiate(String.class));
        assertNull("invokeRequest()",       retriever.invokeRequest("bogus", "concat", "bogus"));
        assertNull("getResponseValue",      retriever.getResponseValue("bogus", "getBytes", byte[].class));

        // for this, not throwing is a good thing
        retriever.setRequestValue("bogus", "concat", String.class, "bogus");
    }
}
