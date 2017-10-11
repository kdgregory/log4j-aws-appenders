// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.io.StringWriter;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.w3c.dom.Document;

import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.NDC;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.WriterAppender;

import net.sf.practicalxml.converter.JsonConverter;
import net.sf.practicalxml.junit.DomAsserts;
import net.sf.practicalxml.xpath.XPathWrapper;


// I know of a nice library for making XPath-based assertions against a DOM, so convert
// the generated JSON into XML ... sue me

public class TestJsonLayout
{
    private final static String TEST_MESSAGE = "test message";

    private Logger logger;
    private WriterAppender appender;
    private StringWriter writer;

    private String rawJson;
    private Document dom;


    private void initialize(String propsName)
    throws Exception
    {
        URL config = ClassLoader.getSystemResource(propsName);
        PropertyConfigurator.configure(config);

        logger = Logger.getLogger(getClass());

        Logger rootLogger = Logger.getRootLogger();
        appender = (WriterAppender)rootLogger.getAppender("default");

        writer = new StringWriter();
        appender.setWriter(writer);
    }


    private void captureLoggingOutput()
    {
        appender.close();
        rawJson = writer.toString();
        dom = JsonConverter.convertToXml(rawJson, "");
    }


    private void assertCommonElements(String message)
    throws Exception
    {
        String timestampAsString = new XPathWrapper("/data/timestamp").evaluateAsString(dom);
        assertFalse("timestamp missing", "".equals(timestampAsString));

        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        parser.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date timestamp = parser.parse(timestampAsString);

        assertTrue("timestamp > now - 1s", timestamp.getTime() > System.currentTimeMillis() - 1000);
        assertTrue("timestamp < now",      timestamp.getTime() < System.currentTimeMillis());

        DomAsserts.assertEquals("thread",  Thread.currentThread().getName(),            dom, "/data/thread");
        DomAsserts.assertEquals("logger",  "com.kdgregory.log4j.aws.TestJsonLayout",    dom, "/data/logger");
        DomAsserts.assertEquals("level",   "DEBUG",                                     dom, "/data/level");
        DomAsserts.assertEquals("message", message,                                     dom, "/data/message");
    }


    @Test
    public void testSimpleMessage() throws Exception
    {
        initialize("TestJsonLayout/defaultConfiguration.properties");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutput();
        assertCommonElements(TEST_MESSAGE);
    }


    @Test
    public void testException() throws Exception
    {
        initialize("TestJsonLayout/defaultConfiguration.properties");

        String exceptionMessage = "throw it out";
        Exception ex = new RuntimeException(exceptionMessage);
        logger.debug(TEST_MESSAGE, ex);

        captureLoggingOutput();
        assertCommonElements(TEST_MESSAGE);

        List<String> exceptionInfo = new XPathWrapper("/data/exception/data").evaluateAsStringList(dom);
        assertTrue("first array element contains exception message",    exceptionInfo.get(0).contains(exceptionMessage));
        assertTrue("second array element contains throwing class name", exceptionInfo.get(1).contains(this.getClass().getName()));
    }


    @Test
    public void testNDC() throws Exception
    {
        initialize("TestJsonLayout/defaultConfiguration.properties");

        NDC.push("frist");  // misspelling intentional
        NDC.push("second");

        logger.debug(TEST_MESSAGE);

        NDC.clear();

        captureLoggingOutput();
        assertCommonElements(TEST_MESSAGE);
        DomAsserts.assertEquals("ndc", "frist second", dom, "/data/ndc");
    }


    @Test
    public void testMDC() throws Exception
    {
        initialize("TestJsonLayout/defaultConfiguration.properties");

        MDC.put("foo", "bar");
        MDC.put("argle", "bargle");

        logger.debug(TEST_MESSAGE);

        MDC.clear();

        captureLoggingOutput();
        assertCommonElements(TEST_MESSAGE);

        DomAsserts.assertCount("children of mdc",   2,          dom, "/data/mdc/*");
        DomAsserts.assertEquals("mdc child 1",      "bar",      dom, "/data/mdc/foo");
        DomAsserts.assertEquals("mdc child 2",      "bargle",   dom, "/data/mdc/argle");
    }
}
