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
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.w3c.dom.Document;

import org.junit.Test;

import static org.junit.Assert.*;

import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import net.sf.kdgcommons.lang.StringUtil;
import net.sf.kdgcommons.test.StringAsserts;
import net.sf.practicalxml.converter.JsonConverter;
import net.sf.practicalxml.junit.DomAsserts;
import net.sf.practicalxml.xpath.XPathWrapper;

import com.amazonaws.util.XpathUtils;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;


/**
 *  This test exercises features of JsonLayout that either depend on Logback 1.3
 *  or require an actual AWS connection.
 */
public class JsonLayoutIntegrationTest
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
        URL config = ClassLoader.getSystemResource("JsonLayoutIntegrationTest/" + propsName + ".xml");
        assertNotNull("was able to retrieve config", config);

        LoggerContext context = (LoggerContext)LoggerFactory.getILoggerFactory();
        context.reset();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(config);

        logger = context.getLogger(getClass());
        appender = (ConsoleAppender<ILoggingEvent>)logger.getAppender("TEST");

        out = new ByteArrayOutputStream();
        appender.setOutputStream(out);
    }


    private void captureLoggingOutput()
    throws Exception
    {
        appender.stop();
        rawJson = new String(out.toByteArray(), StandardCharsets.UTF_8);
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
        DomAsserts.assertEquals("thread",  Thread.currentThread().getName(),                        dom, "/data/thread");
        DomAsserts.assertEquals("logger",  "com.kdgregory.logback.aws.JsonLayoutIntegrationTest",   dom, "/data/logger");
        DomAsserts.assertEquals("level",   "DEBUG",                                                 dom, "/data/level");
        DomAsserts.assertEquals("message", message,                                                 dom, "/data/message");

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
    public void testDefaults() throws Exception
    {
        initialize("testDefaults");

        logger.makeLoggingEventBuilder(Level.DEBUG)
              .addKeyValue("argle", "bargle")
              .addKeyValue("foo", Integer.valueOf(123))
              .log(TEST_MESSAGE);

        captureLoggingOutputAndParse();
        assertCommonElements(TEST_MESSAGE);

        String hostname = new XPathWrapper("/data/hostname").evaluateAsString(dom);
        assertFalse("hostname", StringUtil.isBlank(hostname));

        DomAsserts.assertCount("no exception",  0,  dom, "/data/exception");
        DomAsserts.assertCount("no NDC",        0,  dom, "/data/ndc");
        DomAsserts.assertCount("no MDC",        0,  dom, "/data/mdc");
        DomAsserts.assertCount("no location",   0,  dom, "/data/locationInfo");
        DomAsserts.assertCount("no instanceId", 0,  dom, "/data/instanceId");
        DomAsserts.assertCount("no tags",       0,  dom, "/data/tags");
        DomAsserts.assertCount("no extra",      0,  dom, "/data/extra");
    }


    @Test
    public void testKeyValueEnabled() throws Exception
    {
        initialize("testKeyValueEnabled");

        logger.makeLoggingEventBuilder(Level.DEBUG)
              .addKeyValue("argle", "bargle")
              .addKeyValue("foo", Integer.valueOf(123))
              .log(TEST_MESSAGE);

        captureLoggingOutputAndParse();
        assertCommonElements(TEST_MESSAGE);

        String hostname = new XPathWrapper("/data/hostname").evaluateAsString(dom);
        assertFalse("hostname", StringUtil.isBlank(hostname));

        DomAsserts.assertCount("no exception",  0,  dom, "/data/exception");
        DomAsserts.assertCount("no NDC",        0,  dom, "/data/ndc");
        DomAsserts.assertCount("no MDC",        0,  dom, "/data/mdc");
        DomAsserts.assertCount("no location",   0,  dom, "/data/locationInfo");
        DomAsserts.assertCount("no instanceId", 0,  dom, "/data/instanceId");
        DomAsserts.assertCount("no tags",       0,  dom, "/data/tags");

        DomAsserts.assertEquals("extra 1",      "bargle",   dom, "/data/extra/argle");
        DomAsserts.assertEquals("extra 2",      "123",      dom, "/data/extra/foo");
    }


    @Test
    public void testTagsWithSubstitutions() throws Exception
    {
        initialize("testTagsWithSubstitutions");

        logger.debug(TEST_MESSAGE);

        captureLoggingOutputAndParse();
        assertCommonElements(TEST_MESSAGE);

        String hostname = new XPathWrapper("/data/hostname").evaluateAsString(dom);
        assertFalse("hostname", StringUtil.isBlank(hostname));

        DomAsserts.assertCount("no exception",  0,  dom, "/data/exception");
        DomAsserts.assertCount("no NDC",        0,  dom, "/data/ndc");
        DomAsserts.assertCount("no MDC",        0,  dom, "/data/mdc");
        DomAsserts.assertCount("no location",   0,  dom, "/data/locationInfo");
        DomAsserts.assertCount("no instanceId", 0,  dom, "/data/instanceId");

        DomAsserts.assertCount("tags",          1,  dom, "/data/tags");

        String actualTag = XpathUtils.asString("/data/tags/account", dom);
        StringAsserts.assertRegex("tag value (was: " + actualTag + ")", "\\d{12}", actualTag);
    }
}
