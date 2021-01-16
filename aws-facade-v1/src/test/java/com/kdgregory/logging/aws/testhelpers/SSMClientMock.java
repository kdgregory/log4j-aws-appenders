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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.model.*;


/**
 *  Supports mock-object testing of the InfoFacade parameter store retrieval.
 *  <p>
 *  This is a proxy-based mock: you create an instance of the mock, and from it
 *  create an instance of a proxy that implements the client interface. Each of
 *  the supported client methods is implemented in the mock, and called from the
 *  invocation handler. To test specific behaviors, subclasses should override
 *  the method implementation.
 *  <p>
 *  Each method has an associated invocation counter, along with variables that
 *  hold the last set of arguments passed to this method. These variables are
 *  public, to minimize boilerplate code; if testcases modify the variables, they
 *  only hurt themselves.
 *  <p>
 *  The mock is assumed to be invoked from a single thread, so no effort has been
 *  taken to make it threadsafe.
 */
public class SSMClientMock
implements InvocationHandler
{
    // the known parameter values
    private Map<String,String> knownParams = new HashMap<>();

    // tracked information for getParameter()
    public int getParameterInvocationCount;
    public String getParameterName;


    /**
     *  Creates an instance with specified known key-value pairs.
     */
    public SSMClientMock(String... keysAndValues)
    {
        for (int ii = 0 ; ii < keysAndValues.length ; ii += 2)
        {
            knownParams.put(keysAndValues[ii], keysAndValues[ii + 1]);
        }
    }

//----------------------------------------------------------------------------
//  Mock Object Creation and Invocation Handler
//----------------------------------------------------------------------------

    public AWSSimpleSystemsManagement createClient()
    {
        return (AWSSimpleSystemsManagement)Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] { AWSSimpleSystemsManagement.class },
                            SSMClientMock.this);
    }


    /**
     *  The invocation handler; test code should not care about this.
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable
    {
        String methodName = method.getName();
        if (methodName.equals("getParameter"))
        {
            getParameterInvocationCount++;
            GetParameterRequest request = (GetParameterRequest)args[0];
            getParameterName = request.getName();
            return getParameter(request);
        }

        // if nothing matches, fall through to here
        System.err.println("invocation handler called unexpectedly: " + methodName);
        throw new IllegalArgumentException("unexpected client call: " + methodName);
    }

//----------------------------------------------------------------------------
//  Default mock implementations -- override for specific tests
//----------------------------------------------------------------------------

    public GetParameterResult getParameter(GetParameterRequest request)
    {
        String name = request.getName();
        String value = knownParams.get(name);

        if (value == null)
            throw new ParameterNotFoundException(name);

        Parameter param = new Parameter()
                          .withName(name)
                          .withType(ParameterType.String)
                          .withValue(value);
        return new GetParameterResult().withParameter(param);
    }
}
