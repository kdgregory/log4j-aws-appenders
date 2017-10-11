// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.shared;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.collections.MapBuilder;


public class TestJsonConverter
{
    @Test
    public void testEmptyMap() throws Exception
    {
        String json = JsonConverter.convert(Collections.<String,Object>emptyMap());
        assertEquals("{}", json);
    }


    @Test
    public void testSimpleString() throws Exception
    {
        String json = JsonConverter.convert(new MapBuilder<String,Object>(new TreeMap<String,Object>())
                                            .put("foo", "bar")
                                            .toMap());
        assertEquals("{\"foo\":\"bar\"}", json);
    }


    @Test
    public void testTwoStrings() throws Exception
    {
        String json = JsonConverter.convert(new MapBuilder<String,Object>(new TreeMap<String,Object>())
                                            .put("foo", "bar")
                                            .put("argle", "bargle")
                                            .toMap());
        assertEquals("{\"argle\":\"bargle\",\"foo\":\"bar\"}", json);
    }


    @Test
    public void testEscapedStrings() throws Exception
    {
        String json = JsonConverter.convert(new MapBuilder<String,Object>(new TreeMap<String,Object>())
                                            .put("f\u00f6\u00f6\u0001", "\"\n\r\t\u0007\u0019\f\\")
                                            .toMap());
        assertEquals("{\"f\u00f6\u00f6\":\"\\\"\\n\\r\\t\\b\\f\\\\\"}", json);
    }



    @Test
    public void testNumber() throws Exception
    {
        String json = JsonConverter.convert(new MapBuilder<String,Object>(new TreeMap<String,Object>())
                                            .put("foo", Integer.valueOf(123))
                                            .toMap());
        assertEquals("{\"foo\":123}", json);
    }


    @Test
    public void testBoolean() throws Exception
    {
        String json = JsonConverter.convert(new MapBuilder<String,Object>(new TreeMap<String,Object>())
                                            .put("bar", Boolean.TRUE)
                                            .put("baz", Boolean.FALSE)
                                            .toMap());
        assertEquals("{\"bar\":true,\"baz\":false}", json);
    }


    @Test
    public void testDate() throws Exception
    {
        Date d = new Date(1507764490000L);
        String json = JsonConverter.convert(new MapBuilder<String,Object>(new TreeMap<String,Object>())
                                            .put("foo", d)
                                            .toMap());
        assertEquals("{\"foo\":\"2017-10-11T23:28:10+00:00\"}", json);
    }


    @Test
    public void testNull() throws Exception
    {
        String json = JsonConverter.convert(new MapBuilder<String,Object>(new TreeMap<String,Object>())
                                            .put("bar", null)
                                            .toMap());
        assertEquals("{\"bar\":null}", json);
    }


    @Test
    public void testArray() throws Exception
    {
        String json = JsonConverter.convert(new MapBuilder<String,Object>(new TreeMap<String,Object>())
                                            .put("foo", new String[] {"bar", "123", null})
                                            .toMap());
        assertEquals("{\"foo\":[\"bar\",\"123\",null]}", json);
    }


    @Test
    public void testList() throws Exception
    {
        String json = JsonConverter.convert(new MapBuilder<String,Object>(new TreeMap<String,Object>())
                                            .put("foo", Arrays.asList("bar", 123, null))
                                            .toMap());
        assertEquals("{\"foo\":[\"bar\",123,null]}", json);
    }


    @Test
    public void testSet() throws Exception
    {
        Set<String> s = new TreeSet<String>();
        s.add("argle");
        s.add("bargle");

        String json = JsonConverter.convert(new MapBuilder<String,Object>(new TreeMap<String,Object>())
                                            .put("foo", s)
                                            .toMap());
        assertEquals("{\"foo\":[\"argle\",\"bargle\"]}", json);
    }


    @Test
    public void testMap() throws Exception
    {
        String json = JsonConverter.convert(new MapBuilder<String,Object>(new TreeMap<String,Object>())
                                            .put("foo", new MapBuilder<String,Object>(new TreeMap<String,Object>())
                                                        .put("bar", "baz")
                                                        .toMap())
                                            .toMap());
        assertEquals("{\"foo\":{\"bar\":\"baz\"}}", json);
    }


    @Test
    public void testBogus() throws Exception
    {
        String json = JsonConverter.convert(new MapBuilder<String,Object>(new TreeMap<String,Object>())
                                            .put("foo", String.class)
                                            .toMap());
        assertEquals("{\"foo\":\"" + String.class.toString() + "\"}", json);
    }
}
