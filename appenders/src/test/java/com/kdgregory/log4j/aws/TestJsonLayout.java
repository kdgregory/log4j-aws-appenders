// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws;

import java.io.StringWriter;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.w3c.dom.Document;

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.NDC;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.WriterAppender;

// I know of a nice library for making XPath-based assertions against a DOM, so convert
// the generated JSON into XML ... sue me
import net.sf.practicalxml.converter.JsonConverter;
import net.sf.practicalxml.junit.DomAsserts;
import net.sf.practicalxml.xpath.XPathWrapper;



public class TestJsonLayout
{
    private final static String TEST_MESSAGE = "test message";

    private Logger logger;
    private WriterAppender appender;
    private StringWriter writer;

    private String rawJson;
    private Document dom;

//----------------------------------------------------------------------------
//  Support functions
//----------------------------------------------------------------------------

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
        DomAsserts.assertEquals("thread",  Thread.currentThread().getName(),            dom, "/data/thread");
        DomAsserts.assertEquals("logger",  "com.kdgregory.log4j.aws.TestJsonLayout",    dom, "/data/logger");
        DomAsserts.assertEquals("level",   "DEBUG",                                     dom, "/data/level");
        DomAsserts.assertEquals("message", message,                                     dom, "/data/message");

        String timestampAsString = new XPathWrapper("/data/timestamp").evaluateAsString(dom);
        assertFalse("timestamp missing", "".equals(timestampAsString));

        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        parser.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date timestamp = parser.parse(timestampAsString);
        assertTrue("timestamp > now - 2s", timestamp.getTime() > System.currentTimeMillis() - 2000);
        assertTrue("timestamp < now",      timestamp.getTime() < System.currentTimeMillis());

        String processId = new XPathWrapper("/data/processId").evaluateAsString(dom);
        try
        {
            Integer.parseInt(processId);
        }
        catch (NumberFormatException ex)
        {
            fail("process ID was not a number: " + processId);
        }
    }

//----------------------------------------------------------------------------
//  Test cases
//----------------------------------------------------------------------------

    @Test
    public void testSimpleMessage() throws Exception
    {
        initialize("TestJsonLayout/default.properties");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutput();
        assertCommonElements(TEST_MESSAGE);

        DomAsserts.assertCount("no exception",  0,  dom, "/data/exception");
        DomAsserts.assertCount("no NDC",        0,  dom, "/data/ndc");
        DomAsserts.assertCount("no MDC",        0,  dom, "/data/mdc");
        DomAsserts.assertCount("no location",   0,  dom, "/data/locationInfo");
        DomAsserts.assertCount("no hostname",   0,  dom, "/data/hostname");
        DomAsserts.assertCount("no instanceId", 0,  dom, "/data/instanceId");
        DomAsserts.assertCount("no tags",       0,  dom, "/data/tags");
    }


    @Test
    public void testException() throws Exception
    {
        initialize("TestJsonLayout/default.properties");

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
        initialize("TestJsonLayout/default.properties");

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
        initialize("TestJsonLayout/default.properties");

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


    @Test
    public void testLocation() throws Exception
    {
        initialize("TestJsonLayout/testLocation.properties");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutput();
        assertCommonElements(TEST_MESSAGE);

        DomAsserts.assertCount("location present",  1,                                          dom, "/data/locationInfo");
        DomAsserts.assertEquals("className",        "com.kdgregory.log4j.aws.TestJsonLayout",   dom, "/data/locationInfo/className");
        DomAsserts.assertEquals("methodName",       "testLocation",                             dom, "/data/locationInfo/methodName");
        DomAsserts.assertEquals("fileName",         "TestJsonLayout.java",                      dom, "/data/locationInfo/fileName");

        String lineNumber = new XPathWrapper("/data/locationInfo/lineNumber").evaluateAsString(dom);
        assertFalse("lineNumber", lineNumber.isEmpty());
    }


    @Test
    public void testHostname() throws Exception
    {
        initialize("TestJsonLayout/testHostname.properties");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutput();
        assertCommonElements(TEST_MESSAGE);

        String hostname = new XPathWrapper("/data/hostname").evaluateAsString(dom);
        assertFalse("hostname should be set", hostname.isEmpty());
    }


    @Test
    @Ignore
    // don't run this test unless you're on an EC2 instance
    public void testInstanceId() throws Exception
    {
        initialize("TestJsonLayout/testInstanceId.properties");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutput();
        assertCommonElements(TEST_MESSAGE);

        String instanceId = new XPathWrapper("/data/instanceId").evaluateAsString(dom);
        assertTrue("instance ID starts with i- (was: " + instanceId + ")",
                   instanceId.startsWith("i-"));
    }


    @Test
    public void testTags() throws Exception
    {
        initialize("TestJsonLayout/testTags.properties");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutput();
        assertCommonElements(TEST_MESSAGE);

        DomAsserts.assertCount("tags present",  2,          dom, "/data/tags/*");
        DomAsserts.assertEquals("explicit tag", "bargle",   dom, "/data/tags/argle");

        String dateTag = new XPathWrapper("/data/tags/foo").evaluateAsString(dom);
        assertTrue("substituted tag (was: " + dateTag + ")", dateTag.startsWith("20") && (dateTag.length() == 8));
    }


    @Test
    public void testEmptyTags() throws Exception
    {
        initialize("TestJsonLayout/testEmptyTags.properties");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutput();
        assertCommonElements(TEST_MESSAGE);

        DomAsserts.assertCount("tags not present",  0, dom, "/data/tags/*");
    }
}
