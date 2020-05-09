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
import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.ClassUtil;

import com.kdgregory.logging.aws.internal.retrievers.ParameterStoreRetriever;


/**
 *  Parameter Store did not exist when 1.11.0 was released, which means this
 *  is a test for graceful failure.
 */
public class TestParameterStoreRetriever
{
    @Test
    public void testGracefulFailure() throws Exception
    {
        ParameterStoreRetriever retriever = new ParameterStoreRetriever();
        
        assertNull("parameter class not available",
                   ClassUtil.getFieldValue(retriever, "parameterKlass", Object.class));
        assertNull("doesn't throw when invoked",
                   retriever.invoke("bogus"));
    }
}
