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

package com.kdgregory.logging.testhelpers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.model.*;


/**
 *  Static utility methods for interacting with SSM Parameter Store. Not for
 *  use with minimum-supported-version tests!
 */
public class ParameterStoreTestHelper
{
    private static Logger logger = LoggerFactory.getLogger(ParameterStoreTestHelper.class);


    /**
     * Creates a parameter.
     */
    public static void createParameter(AWSSimpleSystemsManagement client, String name, ParameterType type, String value)
    {
        PutParameterRequest putRequest = new PutParameterRequest()
                                         .withName(name)
                                         .withType(type)
                                         .withValue(value);
        client.putParameter(putRequest);
    }


    /**
     *  Deletes a parameter, logging but otherwise suppressing any failure.
     */
    public static void deleteParameter(AWSSimpleSystemsManagement client, String name)
    {
        try
        {
            DeleteParameterRequest deleteRequest = new DeleteParameterRequest()
                                                   .withName(name);
            client.deleteParameter(deleteRequest);
        }
        catch (Exception ex)
        {
            logger.warn("failed to delete parameter: {}", name, ex);
        }
    }
}
