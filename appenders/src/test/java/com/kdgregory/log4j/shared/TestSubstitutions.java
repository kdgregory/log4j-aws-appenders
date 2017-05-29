// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.shared;

import java.util.Date;

import org.junit.Test;
import static org.junit.Assert.*;


public class TestSubstitutions
{
    private static long TEST_TIMESTAMP = 1496082062000L;    // Mon May 29 14:21:02 EDT 2017


    @Test
    public void testDateSubstitution() throws Exception
    {
        Substitutions subs = new Substitutions(new Date(TEST_TIMESTAMP));

        assertEquals("20170529", subs.perform("{date}"));
    }


    @Test
    public void testTimestampSubstitution() throws Exception
    {
        Substitutions subs = new Substitutions(new Date(TEST_TIMESTAMP));

        assertEquals("20170529182102", subs.perform("{timestamp}"));
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
