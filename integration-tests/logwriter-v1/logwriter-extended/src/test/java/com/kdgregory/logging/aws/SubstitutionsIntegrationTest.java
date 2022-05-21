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

import java.util.Date;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

import net.sf.kdgcommons.test.StringAsserts;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClientBuilder;
import com.amazonaws.services.simplesystemsmanagement.model.ParameterType;
import com.amazonaws.util.EC2MetadataUtils;

import com.kdgregory.logging.aws.common.Substitutions;
import com.kdgregory.logging.testhelpers.ParameterStoreTestHelper;


/**
 *  Tests that AWS-specific substitutions will retrieve an InfoFacade when running
 *  in a real environment.
 *  <p>
 *  Note: remove the @Ignore annotations when running on EC2.
 */
public class SubstitutionsIntegrationTest
{
    // most tests create a Substitutions instance with this date
    private static Date TEST_DATE = new Date(1496082062000L);    // Mon May 29 14:21:02 EDT 2017


    @Test
    public void testAWSAccountId() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);

        StringAsserts.assertRegex("[0-9]{12}", subs.perform("{aws:accountId}"));
    }


    // if not running on EC2 this test will take a long time to run and then fail
    // ... trust me that I've tested it on EC2
    @Test @Ignore
    public void testInstanceId() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);

        assertEquals(EC2MetadataUtils.getInstanceId(), subs.perform("{instanceId}"));
        assertEquals(EC2MetadataUtils.getInstanceId(), subs.perform("{ec2:instanceId}"));
    }


    // if not running on EC2 this test will take a long time to run and then fail
    // ... trust me that I've tested it on EC2
    @Test @Ignore
    public void testEC2Region() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);

        assertEquals(EC2MetadataUtils.getEC2InstanceRegion(), subs.perform("{ec2:region}"));
    }


    @Test
    public void testParameterStore() throws Exception
    {
        final String basicName  = "TestSubstitutions-testParameterStore-basic";
        final String basicValue = "basicValue";
        final String listName   = "TestSubstitutions-testParameterStore-list";
        final String listValue  = "list,value";
        final String secureName = "TestSubstitutions-testParameterStore-secure";
        final String secureValue = "you shouldn't see this";

        AWSSimpleSystemsManagement ssmClient = AWSSimpleSystemsManagementClientBuilder.defaultClient();
        try
        {
            ParameterStoreTestHelper.createParameter(ssmClient, basicName, ParameterType.String, basicValue);
            ParameterStoreTestHelper.createParameter(ssmClient, listName, ParameterType.StringList, listValue);
            ParameterStoreTestHelper.createParameter(ssmClient, secureName, ParameterType.SecureString, secureValue);
            Substitutions subs = new Substitutions(TEST_DATE, 0);

            assertEquals("string" ,         basicValue,                     subs.perform("{ssm:" + basicName + "}"));
            assertEquals("string list" ,    listValue,                      subs.perform("{ssm:" + listName + "}"));
            assertEquals("secure string" ,  "{ssm:" + secureName + "}",     subs.perform("{ssm:" + secureName + "}"));
            assertEquals("secure string" ,  "default",                      subs.perform("{ssm:" + secureName + ":default}"));
            assertEquals("bogus" ,          "{ssm:bogus}",                  subs.perform("{ssm:bogus}"));
            assertEquals("bogus" ,          "default",                      subs.perform("{ssm:bogus:default}"));
        }
        finally
        {
            ParameterStoreTestHelper.deleteParameter(ssmClient, basicName);
            ParameterStoreTestHelper.deleteParameter(ssmClient, listName);
            ParameterStoreTestHelper.deleteParameter(ssmClient, secureName);
        }
    }

}
