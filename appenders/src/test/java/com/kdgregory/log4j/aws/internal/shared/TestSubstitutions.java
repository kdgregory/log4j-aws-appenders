// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.shared;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.PropertyConfigurator;

import net.sf.kdgcommons.test.StringAsserts;

import com.amazonaws.util.EC2MetadataUtils;

import com.kdgregory.log4j.aws.internal.shared.Substitutions;


public class TestSubstitutions
{
    private static Date TEST_DATE = new Date(1496082062000L);    // Mon May 29 14:21:02 EDT 2017
    
    @Before
    public void setUp() throws Exception
    {
        // since we make AWS calls within this test, we need to configure a logger in
        // case those calls go awry; otherwise, Log4J will grab whatever it finds
        
        URL config = ClassLoader.getSystemResource("log4j.properties");
        PropertyConfigurator.configure(config);
    }


    @Test
    public void testNullInput() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);
        assertEquals("", subs.perform(null));
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

        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));

        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        String expected = formatter.format(new Date(runtimeMxBean.getStartTime()));

        assertEquals(expected, subs.perform("{startupTimestamp}"));
    }


    @Test
    public void testPid() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);

        // rather than duplicate implementation, I'll just assert that we got something like a PID
        StringAsserts.assertRegex("[0-9]+", subs.perform("{pid}"));
    }


    @Test
    public void testHostname() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);

        // rather than duplicate implementation, I'll just assert that we got something
        // more-or-less correct that wasn't "unknown" and didn't have punctuation
        StringAsserts.assertRegex("[A-Za-z][A-Za-z0-9_-]*", subs.perform("{hostname}"));
        assertFalse("wasn't unknown", subs.perform("{hostname}").equals("unknown"));
    }


    @Test
    public void testAWSAccountId() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);

        StringAsserts.assertRegex("[0-9]+", subs.perform("{aws:accountId}"));
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


    // this test assumes that you're running on Linux and have the HOME variable
    // defined; if not, feel free to @Ignore
    @Test
    public void testEnvar() throws Exception  {
        Substitutions subs = new Substitutions(TEST_DATE, 0);
        assertEquals(System.getenv("HOME"), subs.perform("{env:HOME}"));
        assertEquals("{env:frobulator}", subs.perform("{env:frobulator}"));
    }


    @Test
    public void testSysprop() throws Exception  {
        // contains characters that would be removed for most destinations
        String value = "this-is_/a test!";
        System.setProperty("TestSubstitutions.testSysprop", value);

        Substitutions subs = new Substitutions(TEST_DATE, 0);
        assertEquals(value,                  subs.perform("{sysprop:TestSubstitutions.testSysprop}"));
        assertEquals("{sysprop:frobulator}", subs.perform("{sysprop:frobulator}"));
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

        assertEquals("{date", subs.perform("{date"));
    }


    @Test
    public void testUnterminatedSubstitution() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);

        assertEquals("{bogus}", subs.perform("{bogus}"));
    }


    @Test
    public void testMultipleSubstitution() throws Exception
    {
        Substitutions subs = new Substitutions(TEST_DATE, 0);

        assertEquals("2017052920170529", subs.perform("{date}{date}"));
    }
}
