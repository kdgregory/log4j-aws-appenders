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
import java.util.TimeZone;

import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.StringUtil;

import com.kdgregory.logging.aws.common.Substitutions;


/**
 *  This tests substituions that don't involve AWS. Look at the aws-shared
 *  integration tests for those that do.
 */
public class TestSubstitutions
{
    // most tests create a Substitutions instance with this date
    private static Date TEST_DATE = new Date(1496082062000L);    // Mon May 29 14:21:02 EDT 2017

    // these are populated by @BeforeClass, using independent implementation
    // of the logic in Substittions
    private static String pid;
    private static String hostname;
    private static String startupTimestamp;

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
        Substitutions subs = new Substitutions(TEST_DATE, 0);
        assertEquals(null, subs.perform(null));
    }


    @Test
    public void testEmptyInput() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);
        assertEquals("", subs.perform(""));
    }


    @Test
    public void testDate() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);

        assertEquals("20170529", subs.perform("{date}"));
    }


    @Test
    public void testTimestamp() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);

        assertEquals("20170529182102", subs.perform("{timestamp}"));
    }


    @Test
    public void testHourlyTimestamp() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);

        assertEquals("20170529180000", subs.perform("{hourlyTimestamp}"));
    }


    @Test
    public void testStartupTimestamp() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);

        assertEquals(startupTimestamp, subs.perform("{startupTimestamp}"));
    }


    @Test
    public void testPid() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);

        assertEquals(pid, subs.perform("{pid}"));
    }


    @Test
    public void testHostname() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);

        assertEquals(hostname, subs.perform("{hostname}"));
    }


    // this test assumes that you're running on Linux and have the HOME variable
    // defined; if not, feel free to @Ignore
    @Test
    public void testEnvar() throws Exception  {
        Substitutions subs = new Substitutions(TEST_DATE, 0);

        // happy paths
        assertEquals(System.getenv("HOME"),     subs.perform("{env:HOME}"));
        assertEquals("zippy",                   subs.perform("{env:frobulator:zippy}"));

        // sad paths
        assertEquals("{env:frobulator}",        subs.perform("{env:frobulator}"));
        assertEquals("{env:frobulator:}",       subs.perform("{env:frobulator:}"));

        // this is probably not what the user wanted, but it's how defaults work
        assertEquals("frobulator",              subs.perform("{env::frobulator}"));
    }


    @Test
    public void testSysprop() throws Exception  {
        // contains characters that would be removed for most destinations
        String value = "this-is_/a test!";
        System.setProperty("TestSubstitutions.testSysprop", value);

        Substitutions subs = new Substitutions(TEST_DATE, 0);

        // happy paths
        assertEquals(value,                         subs.perform("{sysprop:TestSubstitutions.testSysprop}"));
        assertEquals("zippy",                       subs.perform("{sysprop:frobulator:zippy}"));

        // sad paths
        assertEquals("{sysprop:frobulator}",        subs.perform("{sysprop:frobulator}"));
        assertEquals("{sysprop:frobulator:}",       subs.perform("{sysprop:frobulator:}"));

        // again, probably not what was wanted
        assertEquals("frobulator",                  subs.perform("{sysprop::frobulator}"));
    }


    @Test
    public void testSequence() throws Exception  {
        Substitutions subs = new Substitutions(TEST_DATE, 123);
        assertEquals("123", subs.perform("{sequence}"));
    }


    @Test
    public void testBogusSubstitution() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);

        assertEquals("{bogus}", subs.perform("{bogus}"));
    }


    @Test
    public void testUnterminatedSubstitution() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);

        assertEquals("{date", subs.perform("{date"));
    }


    @Test
    public void testMultipleSubstitution() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);

        assertEquals("2017052920170529", subs.perform("{date}{date}"));
    }
}
