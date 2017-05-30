// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.shared;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.test.StringAsserts;


public class TestSubstitutions
{
    private static long TEST_TIMESTAMP = 1496082062000L;    // Mon May 29 14:21:02 EDT 2017


    @Test
    public void testDate() throws Exception
    {
        Substitutions subs = new Substitutions(new Date(TEST_TIMESTAMP));

        assertEquals("20170529", subs.perform("{date}"));
    }


    @Test
    public void testTimestamp() throws Exception
    {
        Substitutions subs = new Substitutions(new Date(TEST_TIMESTAMP));

        assertEquals("20170529182102", subs.perform("{timestamp}"));
    }


    @Test
    public void testStartupTimestamp() throws Exception
    {
        Substitutions subs = new Substitutions();

        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"));

        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();
        String expected = formatter.format(new Date(runtimeMxBean.getStartTime()));

        assertEquals(expected, subs.perform("{startupTimestamp}"));
    }


    @Test
    public void testPid() throws Exception
    {
        Substitutions subs = new Substitutions();

        // rather than duplicate implementation, I'll just assert that we got something like a PID
        StringAsserts.assertRegex("[0-9]+", subs.perform("{pid}"));
    }


    @Test
    public void testHostname() throws Exception
    {
        Substitutions subs = new Substitutions();

        // rather than duplicate implementation, I'll just assert that we got something
        // more-or-less correct that wasn't "unknown" and didn't have punctuation
        StringAsserts.assertRegex("[A-Za-z][A-Za-z0-9_-]*", subs.perform("{hostname}"));
        assertFalse("wasn't unknown", subs.perform("{hostname}").equals("unknown"));
    }


    @Test
    public void testBogusSubstitution() throws Exception
    {
        Substitutions subs = new Substitutions(new Date(TEST_TIMESTAMP));

        assertEquals("{bogus}", subs.perform("{bogus}"));
    }


    @Test
    public void testMultipleSubstitution() throws Exception
    {
        Substitutions subs = new Substitutions(new Date(TEST_TIMESTAMP));

        assertEquals("2017052920170529", subs.perform("{date}{date}"));
    }


}
