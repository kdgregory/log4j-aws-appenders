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

package com.kdgregory.log4j.aws;

import java.io.StringWriter;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import org.w3c.dom.Document;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.apache.log4j.NDC;
import org.apache.log4j.PropertyConfigurator;
import org.apache.log4j.WriterAppender;
import org.apache.log4j.helpers.LogLog;

import net.sf.kdgcommons.lang.StringUtil;
import static net.sf.kdgcommons.test.StringAsserts.*;

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
        URL config = ClassLoader.getSystemResource("TestJsonLayout/" + propsName + ".properties");
        PropertyConfigurator.configure(config);

        logger = Logger.getLogger(getClass());

        Logger rootLogger = Logger.getRootLogger();
        appender = (WriterAppender)rootLogger.getAppender("test");

        writer = new StringWriter();
        appender.setWriter(writer);
    }


    private void captureLoggingOutput()
    {
        appender.close();
        rawJson = writer.toString();
    }


    private void captureLoggingOutputAndParse()
    {
        captureLoggingOutput();

        // I have a nice library for working with XML, so that's the way I'll test
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
//  Setup/teardown
//----------------------------------------------------------------------------

    @Before
    public void setUp()
    {
        LogLog.setQuietMode(true);
    }


    @After
    public void tearDown()
    {
        LogLog.setQuietMode(false);
    }

//----------------------------------------------------------------------------
//  Test cases
//----------------------------------------------------------------------------

    @Test
    public void testSimpleMessage() throws Exception
    {
        initialize("default");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutputAndParse();
        assertCommonElements(TEST_MESSAGE);

        String hostname = new XPathWrapper("/data/hostname").evaluateAsString(dom);
        assertFalse("hostname present", StringUtil.isBlank(hostname));

        DomAsserts.assertCount("no exception",  0,  dom, "/data/exception");
        DomAsserts.assertCount("no NDC",        0,  dom, "/data/ndc");
        DomAsserts.assertCount("no MDC",        0,  dom, "/data/mdc");
        DomAsserts.assertCount("no location",   0,  dom, "/data/locationInfo");
        DomAsserts.assertCount("no instanceId", 0,  dom, "/data/instanceId");
        DomAsserts.assertCount("no tags",       0,  dom, "/data/tags");
    }


    @Test
    public void testException() throws Exception
    {
        initialize("default");

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

        for (String entry : entries)
        {
            assertFalse("entry contains tab character (was: " + entry + ")", entry.contains("\t"));
        }
    }


    @Test
    public void testNDC() throws Exception
    {
        initialize("default");

        NDC.push("frist");  // misspelling intentional
        NDC.push("second");

        logger.debug(TEST_MESSAGE);

        NDC.clear();

        captureLoggingOutputAndParse();
        assertCommonElements(TEST_MESSAGE);
        DomAsserts.assertEquals("ndc", "frist second", dom, "/data/ndc");
    }


    @Test
    public void testMDC() throws Exception
    {
        initialize("default");

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
        initialize("testLocation");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutputAndParse();
        assertCommonElements(TEST_MESSAGE);

        DomAsserts.assertCount("location present",  1,                                          dom, "/data/locationInfo");
        DomAsserts.assertEquals("className",        "com.kdgregory.log4j.aws.TestJsonLayout",   dom, "/data/locationInfo/className");
        DomAsserts.assertEquals("methodName",       "testLocation",                             dom, "/data/locationInfo/methodName");
        DomAsserts.assertEquals("fileName",         "TestJsonLayout.java",                      dom, "/data/locationInfo/fileName");

        String lineNumber = new XPathWrapper("/data/locationInfo/lineNumber").evaluateAsString(dom);
        assertFalse("lineNumber", lineNumber.isEmpty());
    }


    @Test
    public void testDisableHostname() throws Exception
    {
        initialize("testDisableHostname");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutputAndParse();
        assertCommonElements(TEST_MESSAGE);

        DomAsserts.assertCount("no hostname element", 0, dom, new XPathWrapper("/data/hostname"));
    }


    @Test
    @Ignore("this test should only be run on an EC2 instance")
    public void testInstanceId() throws Exception
    {
        initialize("testInstanceId");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutputAndParse();
        assertCommonElements(TEST_MESSAGE);

        String instanceId = new XPathWrapper("/data/instanceId").evaluateAsString(dom);
        assertTrue("instance ID starts with i- (was: " + instanceId + ")",
                   instanceId.startsWith("i-"));
    }


    @Test
    @Ignore("this test should only be run if you have AWS credentials")
    public void testAccountId() throws Exception
    {
        initialize("testAccountId");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutputAndParse();
        assertCommonElements(TEST_MESSAGE);

        String accountId = new XPathWrapper("/data/accountId").evaluateAsString(dom);
        assertRegex("\\d{12}", accountId);
    }


    @Test
    public void testTags() throws Exception
    {
        initialize("testTags");

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
        initialize("testEmptyTags");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutputAndParse();
        assertCommonElements(TEST_MESSAGE);

        DomAsserts.assertCount("tags not present",  0, dom, "/data/tags/*");
    }


    @Test
    public void testNoAppendNewlines() throws Exception
    {
        initialize("default");

        logger.debug(TEST_MESSAGE);
        logger.debug(TEST_MESSAGE);

        captureLoggingOutput();

        assertFalse("output does not contain a newline", rawJson.contains("\n"));
    }


    @Test
    public void testAppendNewlines() throws Exception
    {
        initialize("testAppendNewlines");

        logger.debug(TEST_MESSAGE);
        logger.debug(TEST_MESSAGE);

        captureLoggingOutput();

        assertTrue("output contains a newline between records", rawJson.contains("}\n{"));
        assertTrue("output ands with a newline", rawJson.endsWith("}\n"));
    }
}
