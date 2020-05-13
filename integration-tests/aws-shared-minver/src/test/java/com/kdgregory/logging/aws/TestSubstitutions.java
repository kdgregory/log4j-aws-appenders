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

import com.amazonaws.util.EC2MetadataUtils;

import com.kdgregory.logging.aws.common.Substitutions;


/**
 *  This class tests substitutions that require AWS credentials and/or running
 *  on an EC2 instance.
 *  <p>
 *  Note: remove the @Ignore annotations when running on EC2.
 */
public class TestSubstitutions
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
        // parameter store isn't in our SDK version, so this is mostly a test that the
        // retriever doesn't throw

        Substitutions subs = new Substitutions(TEST_DATE, 0);

        assertEquals("{ssm:bogus}", subs.perform(("{ssm:bogus}")));
        assertEquals("default", subs.perform(("{ssm:bogus:default}")));
    }
}
