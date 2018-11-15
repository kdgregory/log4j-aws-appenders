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

package com.kdgregory.logback.aws;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.w3c.dom.Document;

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

// I know of a nice library for making XPath-based assertions against a DOM, so convert
// the generated JSON into XML ... sue me
import net.sf.practicalxml.converter.JsonConverter;
import net.sf.practicalxml.junit.DomAsserts;
import net.sf.practicalxml.xpath.XPathWrapper;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;


public class TestJsonLayout
{
    private final static String TEST_MESSAGE = "test message";

    private Logger logger;
    private ConsoleAppender<ILoggingEvent> appender;
    private ByteArrayOutputStream out;

    private String rawJson;
    private Document dom;

//----------------------------------------------------------------------------
//  Support functions
//----------------------------------------------------------------------------

    /**
     *  Loads the configuration for a single test. Note that the appender is
     *  a ConsoleAppender; to capture output we change its output stream.
     */
    private void initialize(String propsName)
    throws Exception
    {
        URL config = ClassLoader.getSystemResource(propsName);
        assertNotNull("was able to retrieve config", config);

        LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
        context.reset();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(config);

        logger = context.getLogger(getClass());
        appender = (ConsoleAppender<ILoggingEvent>)logger.getAppender("test");

        out = new ByteArrayOutputStream();
        appender.setOutputStream(out);
    }

    private void captureLoggingOutput()
    throws Exception
    {
        appender.stop();
        rawJson = new String(out.toByteArray(), "UTF-8");
    }


    private void captureLoggingOutputAndParse()
    throws Exception
    {
        captureLoggingOutput();
        dom = JsonConverter.convertToXml(rawJson, "");
    }


    private void assertCommonElements(String message)
    throws Exception
    {
        DomAsserts.assertEquals("thread",  Thread.currentThread().getName(),            dom, "/data/thread");
        DomAsserts.assertEquals("logger",  "com.kdgregory.logback.aws.TestJsonLayout",  dom, "/data/logger");
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
        initialize("TestJsonLayout/default.xml");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutputAndParse();
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
        initialize("TestJsonLayout/default.xml");

        String innerMessage = "I'm not worthy";
        String outerMessage = "throw it out";
        Exception ex = new RuntimeException(outerMessage, new IllegalArgumentException(innerMessage));
        logger.debug(TEST_MESSAGE, ex);

        captureLoggingOutputAndParse();
        assertCommonElements(TEST_MESSAGE);

        List<String> entries = new XPathWrapper("/data/exception/data").evaluateAsStringList(dom);
        assertTrue("first array element contains exception message",    entries.get(0).contains(outerMessage));
        assertTrue("second array element contains throwing class name", entries.get(1).contains(this.getClass().getName()));

        String causeEntry = null;
        for (String entry : entries)
        {
            if (entry.toLowerCase().contains("caused by"))
            {
                causeEntry = entry;
                break;
            }
        }
        assertNotNull("trace includes cause", causeEntry);
        assertTrue("cause includes inner exception class", causeEntry.contains(IllegalArgumentException.class.getName()));
        assertTrue("cause includes inner exception message", causeEntry.contains(innerMessage));
    }


    @Test
    public void testMDC() throws Exception
    {
        initialize("TestJsonLayout/default.xml");

        MDC.put("foo", "bar");
        MDC.put("argle", "bargle");

        logger.debug(TEST_MESSAGE);

        MDC.clear();

        captureLoggingOutputAndParse();
        assertCommonElements(TEST_MESSAGE);

        DomAsserts.assertCount("children of mdc",   2,          dom, "/data/mdc/*");
        DomAsserts.assertEquals("mdc child 1",      "bar",      dom, "/data/mdc/foo");
        DomAsserts.assertEquals("mdc child 2",      "bargle",   dom, "/data/mdc/argle");
    }


    @Test
    public void testLocation() throws Exception
    {
        initialize("TestJsonLayout/testLocation.xml");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutputAndParse();
        assertCommonElements(TEST_MESSAGE);

        DomAsserts.assertCount("location present",  1,                                          dom, "/data/locationInfo");
        DomAsserts.assertEquals("className",        "com.kdgregory.logback.aws.TestJsonLayout", dom, "/data/locationInfo/className");
        DomAsserts.assertEquals("methodName",       "testLocation",                             dom, "/data/locationInfo/methodName");
        DomAsserts.assertEquals("fileName",         "TestJsonLayout.java",                      dom, "/data/locationInfo/fileName");

        String lineNumber = new XPathWrapper("/data/locationInfo/lineNumber").evaluateAsString(dom);
        assertFalse("lineNumber", lineNumber.isEmpty());
    }


    @Test
    public void testHostname() throws Exception
    {
        initialize("TestJsonLayout/testHostname.xml");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutputAndParse();
        assertCommonElements(TEST_MESSAGE);

        String hostname = new XPathWrapper("/data/hostname").evaluateAsString(dom);
        assertFalse("hostname should be set", hostname.isEmpty());
    }


    @Test
    @Ignore("this test should only be run on an EC2 instance")
    public void testInstanceId() throws Exception
    {
        initialize("TestJsonLayout/testInstanceId.xml");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutputAndParse();
        assertCommonElements(TEST_MESSAGE);

        String instanceId = new XPathWrapper("/data/instanceId").evaluateAsString(dom);
        assertTrue("instance ID starts with i- (was: " + instanceId + ")",
                   instanceId.startsWith("i-"));
    }


    @Test
    public void testTags() throws Exception
    {
        initialize("TestJsonLayout/testTags.xml");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutputAndParse();
        assertCommonElements(TEST_MESSAGE);

        DomAsserts.assertCount("tags present",  2,          dom, "/data/tags/*");
        DomAsserts.assertEquals("explicit tag", "bargle",   dom, "/data/tags/argle");

        String dateTag = new XPathWrapper("/data/tags/foo").evaluateAsString(dom);
        assertTrue("substituted tag (was: " + dateTag + ")", dateTag.startsWith("20") && (dateTag.length() == 8));
    }


    @Test
    public void testEmptyTags() throws Exception
    {
        // this is a somewhat bogus test, because Logback completely ignores an empty property,
        // but it's useful to ensure that nothing breaks

        initialize("TestJsonLayout/testEmptyTags.xml");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutputAndParse();
        assertCommonElements(TEST_MESSAGE);

        DomAsserts.assertCount("tags not present",  0, dom, "/data/tag");
    }


    @Test
    public void testNoAppendNewlines() throws Exception
    {
        initialize("TestJsonLayout/default.xml");

        logger.debug(TEST_MESSAGE);
        logger.debug(TEST_MESSAGE);

        captureLoggingOutput();

        assertFalse("output does not contain a newline", rawJson.contains("\n"));
    }


    @Test
    public void testAppendNewlines() throws Exception
    {
        initialize("TestJsonLayout/testAppendNewlines.xml");

        logger.debug(TEST_MESSAGE);
        logger.debug(TEST_MESSAGE);

        captureLoggingOutput();

        assertTrue("output contains a newline between records", rawJson.contains("}\n{"));
        assertTrue("output ands with a newline", rawJson.endsWith("}\n"));
    }
}
