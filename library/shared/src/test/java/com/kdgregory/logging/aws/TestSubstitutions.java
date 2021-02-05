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

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TimeZone;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.StringUtil;
import static net.sf.kdgcommons.test.StringAsserts.*;

import com.kdgregory.logging.aws.common.Substitutions;
import com.kdgregory.logging.testhelpers.MockInfoFacade;


/**
 *  This tests substituions that don't involve AWS. Look at the integration tests
 *  for those that do.
 */
public class TestSubstitutions
{
    // most tests create a Substitutions instance with this date
    private static Date TEST_DATE = new Date(1496082062000L);    // Mon May 29 14:21:02 EDT 2017

    // these are populated by @BeforeClass, using independent implementation
    // of the logic in Substitutions
    private static String pid;
    private static String hostname;
    private static String startupTimestamp;

    // this is initialized for every test, can be configured however you want
    private MockInfoFacade mockInfoFacade = new MockInfoFacade();


    @BeforeClass
    public static void initExpectedValues()
    {
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();

        String vmName = runtimeMxBean.getName();
        pid = StringUtil.extractLeft(vmName, "@");
        hostname = StringUtil.extractRight(vmName, "@");

        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
        startupTimestamp = formatter.format(new Date(runtimeMxBean.getStartTime()));
    }


    @Test
    public void testNullInput() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0, mockInfoFacade);
        assertEquals(null, subs.perform(null));
    }


    @Test
    public void testEmptyInput() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0, mockInfoFacade);
        assertEquals("", subs.perform(""));
    }


    @Test
    public void testDate() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0, mockInfoFacade);

        assertEquals("20170529", subs.perform("{date}"));
    }


    @Test
    public void testTimestamp() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0, mockInfoFacade);

        assertEquals("20170529182102", subs.perform("{timestamp}"));
    }


    @Test
    public void testHourlyTimestamp() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0, mockInfoFacade);

        assertEquals("20170529180000", subs.perform("{hourlyTimestamp}"));
    }


    @Test
    public void testStartupTimestamp() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0, mockInfoFacade);

        assertEquals(startupTimestamp, subs.perform("{startupTimestamp}"));
    }


    @Test
    public void testPid() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0, mockInfoFacade);

        assertEquals(pid, subs.perform("{pid}"));
    }


    @Test
    public void testHostname() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0, mockInfoFacade);

        assertEquals(hostname, subs.perform("{hostname}"));
    }


    @Test
    public void testUUID() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0, mockInfoFacade);

        assertRegex("\\p{XDigit}{8}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{4}-\\p{XDigit}{12}",
                    subs.perform("{uuid}"));
    }


    // this test assumes that you're running on Linux and have the HOME and USER
    // variables defined; if not, feel free to @Ignore
    @Test
    public void testEnvar() throws Exception  {
        Substitutions subs = new Substitutions(TEST_DATE, 0, mockInfoFacade);

        // happy paths
        assertEquals(System.getenv("HOME"),                         subs.perform("{env:HOME}"));
        assertEquals("zippy",                                       subs.perform("{env:frobulator:zippy}"));
        assertEquals(System.getenv("HOME")+System.getenv("USER"),   subs.perform("{env:HOME}{env:USER}"));

        // sad paths
        assertEquals("{env:frobulator}",                            subs.perform("{env:frobulator}"));
        assertEquals("{env:frobulator:}",                           subs.perform("{env:frobulator:}"));

        // this is probably not what the user wanted, but it's how defaults work
        assertEquals("frobulator",                                  subs.perform("{env::frobulator}"));
    }


    @Test
    public void testSysprop() throws Exception  {
        String value1 = "this is a test";
        System.setProperty("TestSubstitutions.testSysprop-1", value1);
        String value2 = "this is another test";
        System.setProperty("TestSubstitutions.testSysprop-2", value2);

        Substitutions subs = new Substitutions(TEST_DATE, 0, mockInfoFacade);

        // happy paths
        assertEquals(value1,                        subs.perform("{sysprop:TestSubstitutions.testSysprop-1}"));
        assertEquals(value1 + value2,               subs.perform("{sysprop:TestSubstitutions.testSysprop-1}{sysprop:TestSubstitutions.testSysprop-2}"));
        assertEquals("zippy",                       subs.perform("{sysprop:frobulator:zippy}"));

        // sad paths
        assertEquals("{sysprop:frobulator}",        subs.perform("{sysprop:frobulator}"));
        assertEquals("{sysprop:frobulator:}",       subs.perform("{sysprop:frobulator:}"));

        // again, probably not what was wanted
        assertEquals("frobulator",                  subs.perform("{sysprop::frobulator}"));
    }


    @Test
    public void testSequence() throws Exception  {
        Substitutions subs = new Substitutions(TEST_DATE, 123, mockInfoFacade);
        assertEquals("123", subs.perform("{sequence}"));
    }


    @Test
    public void testAccountId() throws Exception
    {
        mockInfoFacade.accountId = "123456789012";

        Substitutions subs = new Substitutions(TEST_DATE, 123, mockInfoFacade);
        assertEquals("123456789012", subs.perform("{aws:accountId}"));
    }


    @Test
    public void testEC2InstanceId() throws Exception
    {
        mockInfoFacade.ec2InstanceId = "i-12345678";

        Substitutions subs = new Substitutions(TEST_DATE, 123, mockInfoFacade);
        assertEquals("i-12345678", subs.perform("{ec2:instanceId}"));
    }


    @Test
    public void testEC2Region() throws Exception
    {
        mockInfoFacade.ec2Region = "us-east-1";

        Substitutions subs = new Substitutions(TEST_DATE, 123, mockInfoFacade);
        assertEquals("us-east-1", subs.perform("{ec2:region}"));
    }


    @Test
    public void testEC2InstanceTag() throws Exception
    {
        mockInfoFacade = new MockInfoFacade()
        {
            @Override
            public Map<String,String> retrieveEC2Tags(String instanceId)
            {
                assertEquals("passed correct instance ID", ec2InstanceId, instanceId);
                return ec2InstanceTags;
            }
        };

        mockInfoFacade.ec2InstanceId = "i-1234567890";
        mockInfoFacade.ec2InstanceTags.put("FOO", "bar");
        mockInfoFacade.ec2InstanceTags.put("ARGLE", "bargle");

        Substitutions subs = new Substitutions(TEST_DATE, 123, mockInfoFacade);
        assertEquals("bar unknown bargle", subs.perform("{ec2:tag:FOO} {ec2:tag:foo} {ec2:tag:ARGLE}"));
    }


    @Test
    public void testParameterStore() throws Exception
    {
        mockInfoFacade.parameterValues.put("foo",   "bar");
        mockInfoFacade.parameterValues.put("argle", "bargle");

        Substitutions subs = new Substitutions(TEST_DATE, 123, mockInfoFacade);
        assertEquals("bar bargle {ssm:biff} baz", subs.perform("{ssm:foo} {ssm:argle} {ssm:biff} {ssm:biff:baz}"));
    }


    @Test
    public void testBogusSubstitution() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0, mockInfoFacade);

        assertEquals("{bogus}", subs.perform("{bogus}"));
    }


    @Test
    public void testUnterminatedSubstitution() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0, mockInfoFacade);

        assertEquals("{date", subs.perform("{date"));
    }


    @Test
    public void testMultipleSubstitution() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0, mockInfoFacade);

        assertEquals(" 20170529 20170529 ", subs.perform(" {date} {date} "));
    }


    @Test
    public void testSubstitutionAfterFailure() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0, mockInfoFacade);

        // was succeeding if the bogus substitution was the first thing
        assertEquals(" 20170529 {bogus} 20170529", subs.perform(" {date} {bogus} {date}"));
    }
}
