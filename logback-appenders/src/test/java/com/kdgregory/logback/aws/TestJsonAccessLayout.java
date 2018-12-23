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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpSession;

import org.w3c.dom.Document;

import org.junit.Ignore;
import org.junit.Test;
import static org.junit.Assert.*;

import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import net.sf.kdgcommons.lang.StringUtil;
import net.sf.kdgcommons.test.NumericAsserts;
import net.sf.kdgcommons.test.StringAsserts;
import net.sf.practicalxml.converter.JsonConverter;
import net.sf.practicalxml.junit.DomAsserts;
import net.sf.practicalxml.xpath.XPathWrapper;

import ch.qos.logback.access.spi.AccessEvent;
import ch.qos.logback.access.spi.IAccessEvent;
import ch.qos.logback.access.spi.ServerAdapter;


public class TestJsonAccessLayout
{
    private final static String EXPECTED_REQUEST_METHOD = "GET";
    private final static long   EXPECTED_ELAPSED_TIME   = 40;
    private final static int    EXPECTED_STATUS_CODE    = 200;

    // these are assigned by constructEvent()
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private ServerAdapter serverAdapter;
    private IAccessEvent event;

    private String json;
    private Document dom;

//----------------------------------------------------------------------------
//  Support code
//----------------------------------------------------------------------------

    private void constructMocks(final String requestUri, final String queryString, final String responseContent, String... headers)
    throws Exception
    {
        final byte[] responseBytes = responseContent.getBytes("UTF-8");

        request = new MockHttpServletRequest(EXPECTED_REQUEST_METHOD, requestUri);
        response = new MockHttpServletResponse();

        // the mock request doesn't parse the query string, so we'll do it
        if (! StringUtil.isBlank(queryString))
        {
            request.setQueryString(queryString);
            for (String param : queryString.split("&"))
            {
                String[] paramKV = param.split("=");
                request.addParameter(paramKV[0], paramKV[1]);
            }
        }

        // same headers for both request and response; this is just a test
        for (String header : headers)
        {
                String[] headerKV = header.split("=");
                request.addHeader(headerKV[0], headerKV[1]);
                response.setHeader(headerKV[0], headerKV[1]);
        }

        serverAdapter = new ServerAdapter()
        {
            @Override
            public long getRequestTimestamp()
            {
                return System.currentTimeMillis() - EXPECTED_ELAPSED_TIME;
            }

            @Override
            public long getContentLength()
            {
                return responseBytes.length;
            }

            @Override
            public int getStatusCode()
            {
                return EXPECTED_STATUS_CODE;
            }

            @Override
            public Map<String,String> buildResponseHeaderMap()
            {
                Map<String,String> result = new HashMap<String,String>();
                for (String key : response.getHeaderNames())
                {
                    result.put(key, response.getHeader(key));   // doesn't properly handle multiple headers, but we don't use them here
                }
                return result;
            }
        };
    }


    private void applyLayoutAndParse(JsonAccessLayout layout)
    throws Exception
    {
        event = new AccessEvent(request, response, serverAdapter);
        layout.start();
        json = layout.doLayout(event);
        dom = JsonConverter.convertToXml(json, "");
    }


    private void assertCommonElements(String requestUri, String queryString, String content)
    throws Exception
    {
        String timestampAsString = new XPathWrapper("/data/timestamp").evaluateAsString(dom);
        assertFalse("timestamp missing", "".equals(timestampAsString));

        SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        parser.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date timestamp = parser.parse(timestampAsString);
        assertTrue("timestamp > now - 1s", timestamp.getTime() > System.currentTimeMillis() - 1000);
        assertTrue("timestamp < now + 1s", timestamp.getTime() < System.currentTimeMillis() + 1000);

        Number elapsedTime = new XPathWrapper("/data/elapsedTime").evaluateAsNumber(dom);
        NumericAsserts.assertInRange("elapsed time", EXPECTED_ELAPSED_TIME - 20, EXPECTED_ELAPSED_TIME + 20, elapsedTime.longValue());

        DomAsserts.assertEquals("request URI",  requestUri,             dom, "/data/requestURI");
        DomAsserts.assertEquals("protocol",     "HTTP/1.1",             dom, "/data/protocol");
        DomAsserts.assertEquals("status code",  "200",                  dom, "/data/statusCode");

        int expectedContentLength = content.getBytes("UTF-8").length;
        int actualContentLength = new XPathWrapper("/data/bytesSent").evaluateAsNumber(dom).intValue();
        assertEquals("content length", expectedContentLength, actualContentLength);

        // thread name is explicitly set by the valve, which doesn't happen here, so we
        // check for the default value
        DomAsserts.assertEquals("thread",  IAccessEvent.NA,             dom, "/data/thread");

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
    public void testDefaultConfiguration() throws Exception
    {
        JsonAccessLayout layout = new JsonAccessLayout();

        constructMocks("/example", "name=value", "success!");

        applyLayoutAndParse(layout);

        assertCommonElements("/example", "name=value", "success!");
        DomAsserts.assertEquals("remote IP",            "127.0.0.1",        dom, "/data/remoteIP");

        // the following must be explicitly enabled
        DomAsserts.assertCount("instance ID",           0,                  dom, "/data/instanceId");
        DomAsserts.assertCount("destination hostname",  0,                  dom, "/data/server");
        DomAsserts.assertCount("remote hostname",       0,                  dom, "/data/remoteHost");
        DomAsserts.assertCount("remote user",           0,                  dom, "/data/user");
        DomAsserts.assertCount("session ID",            0,                  dom, "/data/sessionId");
        DomAsserts.assertCount("request headers",       0,                  dom, "/data/requestHeaders");
        DomAsserts.assertCount("response headers",      0,                  dom, "/data/responseHeaders");
        DomAsserts.assertCount("cookies",               0,                  dom, "/data/cookies");
        DomAsserts.assertCount("query string",          0,                  dom, "/data/queryString");
        DomAsserts.assertCount("parameters",            0,                  dom, "/data/parameters");
        DomAsserts.assertCount("tags",                  0,                  dom, "/data/tags");
    }


    @Test
    public void testEnableQueryString() throws Exception
    {
        JsonAccessLayout layout = new JsonAccessLayout();
        layout.setEnableQueryString(true);

        constructMocks("/example", "name=value", "success!");

        applyLayoutAndParse(layout);

        DomAsserts.assertEquals("query string", "?name=value", dom, "/data/queryString");
    }


    @Test
    @Ignore("only run this on an EC2 instance")
    public void testEnableInstanceId() throws Exception
    {
        JsonAccessLayout layout = new JsonAccessLayout();
        layout.setEnableInstanceId(true);

        constructMocks("/example", "name=value", "success!");
        request.addHeader("Host", "www.example.com");

        applyLayoutAndParse(layout);

        String hostname = new XPathWrapper("/data/instanceId").evaluateAsString(dom);
        assertFalse("hostname present and non-blank", StringUtil.isBlank(hostname));
    }


    @Test
    public void testEnableHostnames() throws Exception
    {
        JsonAccessLayout layout = new JsonAccessLayout();
        layout.setEnableHostname(true);
        layout.setEnableRemoteHost(true);
        layout.setEnableServer(true);

        constructMocks("/example", "name=value", "success!");
        request.addHeader("Host", "www.example.com");

        applyLayoutAndParse(layout);

        // hostname comes from local management beans, we'll just verify that it exists
        String hostname = new XPathWrapper("/data/hostname").evaluateAsString(dom);
        assertFalse("hostname present and non-blank", StringUtil.isBlank(hostname));

        // the rest of these come from the request
        DomAsserts.assertEquals("remote IP",            "127.0.0.1",        dom, "/data/remoteIP");
        DomAsserts.assertEquals("remote hostname",      "localhost",        dom, "/data/remoteHost");
        DomAsserts.assertEquals("destination hostname", "www.example.com",  dom, "/data/server");
    }


    @Test
    public void testEnableRemoteUser() throws Exception
    {
        JsonAccessLayout layout = new JsonAccessLayout();
        layout.setEnableRemoteUser(true);

        constructMocks("/example", "name=value", "success!");
        request.setRemoteUser("example");

        applyLayoutAndParse(layout);

        DomAsserts.assertEquals("remote user",          "example",          dom, "/data/user");
    }


    @Test
    public void testEnableSessionId() throws Exception
    {
        JsonAccessLayout layout = new JsonAccessLayout();
        layout.setEnableSessionId(true);

        constructMocks("/example", "name=value", "success!");
        request.getSession();

        applyLayoutAndParse(layout);

        String sessionId = new XPathWrapper("/data/sessionId").evaluateAsString(dom);
        assertFalse("session ID present and not blank",  StringUtil.isBlank(sessionId));
    }


    @Test
    public void testEnableSessionIdWithNoSession() throws Exception
    {
        JsonAccessLayout layout = new JsonAccessLayout();
        layout.setEnableSessionId(true);

        constructMocks("/example", "name=value", "success!");

        // Logback attempts to get the session ID on the way out; if it isn't already set, Tomcat
        // then tries to add the session cookie to the response (but the mock doesn't do that)
        request = new MockHttpServletRequest()
        {
            @Override
            public HttpSession getSession()
            {
                throw new IllegalStateException("Cannot create a session after the response has been committed");
            }
        };

        applyLayoutAndParse(layout);

        DomAsserts.assertCount("session ID present",        1,          dom, "/data/sessionId");
        DomAsserts.assertEquals("session ID is blank",      "",         dom, "/data/sessionId");
    }


    @Test
    public void testEnableHeadersExplicitWhitelist() throws Exception
    {
        JsonAccessLayout layout = new JsonAccessLayout();
        layout.setEnableRequestHeaders(true);
        layout.setEnableResponseHeaders(true);
        layout.setIncludeHeaders("foo,baz");
        layout.setExcludeHeaders("foo,bar");

        constructMocks("/example", "name=value", "success!",
                       "Argle=OK", "Foo=me neither", "bar=nor I", "baz=biff");

        // override one response header so that we know we're getting different serializations
        response.setHeader("baz", "spiff");

        applyLayoutAndParse(layout);

        DomAsserts.assertCount("has request headers object",        1,                  dom, "/data/requestHeaders");
        DomAsserts.assertCount("number of request headers",         1,                  dom, "/data/requestHeaders/*");

        DomAsserts.assertCount("has response headers object",       1,                  dom, "/data/responseHeaders");
        DomAsserts.assertCount("number of response headers",        1,                  dom, "/data/responseHeaders/*");

        DomAsserts.assertEquals("whitelisted request header",       "biff",             dom, "/data/requestHeaders/baz");
        DomAsserts.assertEquals("whitelisted response header",      "spiff",            dom, "/data/responseHeaders/baz");

        // the rest of the asserts are implied by the counts above, but I'll make them explicit anyway

        DomAsserts.assertCount("non-whitelisted request header" ,   0,                  dom, "/data/requestHeaders/Argle");
        DomAsserts.assertCount("non-whitelisted response header",   0,                  dom, "/data/responseHeaders/Argle");

        // this validates that blacklist takes precedence over whitelist
        DomAsserts.assertCount("blacklisted request header #1",     0,                  dom, "/data/requestHeaders/Foo");
        DomAsserts.assertCount("blacklisted response header #1",    0,                  dom, "/data/responseHeaders/Foo");

        // this isn't on the whitelist, so blacklist shouldn't matter
        DomAsserts.assertCount("blacklisted request header #2",     0,                  dom, "/data/requestHeaders/bar");
        DomAsserts.assertCount("blacklisted response header #2",    0,                  dom, "/data/responseHeaders/bar");
    }


    @Test
    public void testEnableHeadersWhitelistAll() throws Exception
    {
        JsonAccessLayout layout = new JsonAccessLayout();
        layout.setEnableRequestHeaders(true);
        layout.setEnableResponseHeaders(true);
        layout.setIncludeHeaders("*");
        layout.setExcludeHeaders("foo,bar");

        constructMocks("/example", "name=value", "success!",
                       "Argle=OK", "Foo=not coming back", "bar=nor I", "baz=biff");

        // override one response header so that we know we're getting different serializations
        response.setHeader("baz", "spiff");

        applyLayoutAndParse(layout);

        DomAsserts.assertCount("has request headers object",            1,                  dom, "/data/requestHeaders");
        DomAsserts.assertCount("number of request headers",             2,                  dom, "/data/requestHeaders/*");

        DomAsserts.assertCount("has response headers object",           1,                  dom, "/data/responseHeaders");
        DomAsserts.assertCount("number of response headers",            2,                  dom, "/data/responseHeaders/*");

        DomAsserts.assertEquals("non-blacklisted request header #1",    "OK",               dom, "/data/requestHeaders/Argle");
        DomAsserts.assertEquals("non-blacklisted response header #1",   "OK",               dom, "/data/responseHeaders/Argle");

        DomAsserts.assertEquals("non-blacklisted request header #2",    "biff",             dom, "/data/requestHeaders/baz");
        DomAsserts.assertEquals("non-blacklisted response header #2",   "spiff",            dom, "/data/responseHeaders/baz");
    }


    @Test
    public void testEnableParametersExplicitWhitelist() throws Exception
    {
        // note mixed (and different) case between whitelist and actual params

        JsonAccessLayout layout = new JsonAccessLayout();
        layout.setEnableParameters(true);
        layout.setIncludeParameters("argle,Zippy");
        layout.setExcludeParameters("foo,baz");

        constructMocks("/example", "Argle=bargle&zippy=griffy&Foo=bar&less=more", "success!");

        applyLayoutAndParse(layout);

        DomAsserts.assertCount("has parameters object",             1,                  dom, "/data/parameters");
        DomAsserts.assertCount("number of parameters",              2,                  dom, "/data/parameters/*");

        DomAsserts.assertEquals("whitelisted parameter #1",         "bargle",           dom, "/data/parameters/Argle");
        DomAsserts.assertEquals("whitelisted parameter #2",         "griffy",           dom, "/data/parameters/zippy");
        DomAsserts.assertCount("non-whitelisted parameter",         0,                  dom, "/data/parameters/less");
        DomAsserts.assertCount("blacklisted parameter",             0,                  dom, "/data/parameters/foo");
    }


    @Test
    public void testEnableParametersWhitelistAll() throws Exception
    {
        JsonAccessLayout layout = new JsonAccessLayout();
        layout.setEnableParameters(true);
        layout.setIncludeParameters("*");
        layout.setExcludeParameters("foo,baz");

        constructMocks("/example", "Argle=bargle&foo=bar&less=more", "success!");

        applyLayoutAndParse(layout);

        DomAsserts.assertCount("has parameters object",             1,                  dom, "/data/parameters");
        DomAsserts.assertCount("number of parameters",              2,                  dom, "/data/parameters/*");

        DomAsserts.assertEquals("non-blacklisted parameter #1",     "bargle",           dom, "/data/parameters/Argle");
        DomAsserts.assertEquals("non-blacklisted parameter #2",     "more",             dom, "/data/parameters/less");
        DomAsserts.assertCount("blacklisted parameter",             0,                  dom, "/data/parameters/foo");
    }


    @Test
    public void testParametersAndHeaderHaveDistinctWhitelists() throws Exception
    {
        JsonAccessLayout layout = new JsonAccessLayout();
        layout.setEnableRequestHeaders(true);
        layout.setEnableResponseHeaders(true);
        layout.setIncludeHeaders("argle");
        layout.setExcludeHeaders("foo");
        layout.setEnableParameters(true);
        layout.setIncludeParameters("baz");
        layout.setExcludeParameters("Argle");

        constructMocks("/example", "Argle=bargle&Foo=bar&baz=biff", "success!",
                       "Argle=OK", "Foo=bar", "baz=biff");

        applyLayoutAndParse(layout);

        DomAsserts.assertCount("has parameters object",             1,                  dom, "/data/parameters");
        DomAsserts.assertCount("number of parameters",              1,                  dom, "/data/parameters/*");
        DomAsserts.assertEquals("whitelisted parameter",            "biff",             dom, "/data/parameters/baz");

        DomAsserts.assertCount("has requestHeaders object",         1,                  dom, "/data/requestHeaders");
        DomAsserts.assertCount("number of request headers",         1,                  dom, "/data/requestHeaders/*");
        DomAsserts.assertEquals("whitelisted request header",       "OK",               dom, "/data/requestHeaders/Argle");

        DomAsserts.assertCount("has responseHeaders object",        1,                  dom, "/data/responseHeaders");
        DomAsserts.assertCount("number of response headers",        1,                  dom, "/data/responseHeaders/*");
        DomAsserts.assertEquals("whitelisted response header",      "OK",               dom, "/data/responseHeaders/Argle");
    }


    @Test
    public void testEnableCookiesExplicitWhitelist() throws Exception
    {
        // note mixed (and different) case between whitelist, blacklist and actual cookie

        JsonAccessLayout layout = new JsonAccessLayout();
        layout.setEnableCookies(true);
        layout.setIncludeCookies("Argle,Foo");
        layout.setExcludeCookies("foo,baz");

        constructMocks("/example", "", "success!");

        Cookie[] cookies = new Cookie[]
        {
            new Cookie("argle", "bargle"),
            new Cookie("foo", "bar"),
            new Cookie("baz", "biff"),
            new Cookie("zippy", "griffy")
        };
        cookies[0].setDomain(".example.com");
        cookies[0].setPath("/example");
        cookies[0].setMaxAge(64);
        cookies[0].setComment("blah blah blah");
        cookies[0].setVersion(123);
        cookies[0].setSecure(true);
        cookies[0].setHttpOnly(true);
        request.setCookies(cookies);

        applyLayoutAndParse(layout);

        DomAsserts.assertCount("has cookies object",                1,                  dom, "/data/cookies");
        DomAsserts.assertCount("number of cookies",                 1,                  dom, "/data/cookies/*");

        DomAsserts.assertCount("whitelisted cookie",                1,                  dom, "/data/cookies/data/name[text() = 'argle']");
        DomAsserts.assertCount("non-whitelisted cookie",            0,                  dom, "/data/cookies/data/name[text() = 'zippy']");
        DomAsserts.assertCount("blacklisted cookie #1",             0,                  dom, "/data/cookies/data/name[text() = 'foo']");
        DomAsserts.assertCount("blacklisted cookie #2",             0,                  dom, "/data/cookies/data/name[text() = 'baz']");

        DomAsserts.assertEquals("whitelisted cookie value",         "bargle",           dom, "/data/cookies/data/name[text() = 'argle']/../value");
        DomAsserts.assertEquals("whitelisted cookie domain",        ".example.com",     dom, "/data/cookies/data/name[text() = 'argle']/../domain");
        DomAsserts.assertEquals("whitelisted cookie path",          "/example",         dom, "/data/cookies/data/name[text() = 'argle']/../path");
        DomAsserts.assertEquals("whitelisted cookie max age",       "64",               dom, "/data/cookies/data/name[text() = 'argle']/../maxAge");
        DomAsserts.assertEquals("whitelisted cookie comment",       "blah blah blah",   dom, "/data/cookies/data/name[text() = 'argle']/../comment");
        DomAsserts.assertEquals("whitelisted cookie version",       "123",              dom, "/data/cookies/data/name[text() = 'argle']/../version");
        DomAsserts.assertEquals("whitelisted cookie isSecure",      "true",             dom, "/data/cookies/data/name[text() = 'argle']/../isSecure");
        DomAsserts.assertEquals("whitelisted cookie isHttpOnly",    "true",             dom, "/data/cookies/data/name[text() = 'argle']/../isHttpOnly");
    }


    @Test
    public void testEnableCookiesWhitelistAll() throws Exception
    {
        JsonAccessLayout layout = new JsonAccessLayout();
        layout.setEnableCookies(true);
        layout.setIncludeCookies("*");
        layout.setExcludeCookies("foo,baz");

        constructMocks("/example", "", "success!");

        // for this test we just care about presence, not content of the child objects
        request.setCookies(
            new Cookie("argle", "bargle"),
            new Cookie("foo", "bar"),
            new Cookie("baz", "biff"),
            new Cookie("zippy", "griffy")
        );

        applyLayoutAndParse(layout);

        DomAsserts.assertCount("has cookies object",                1,                  dom, "/data/cookies");
        DomAsserts.assertCount("number of cookies",                 2,                  dom, "/data/cookies/*");

        DomAsserts.assertCount("non-blacklisted cookie #1",         1,                  dom, "/data/cookies/data/name[text() = 'argle']");
        DomAsserts.assertCount("non-blacklisted cookie #2",         1,                  dom, "/data/cookies/data/name[text() = 'zippy']");
        DomAsserts.assertCount("blacklisted cookie #1",             0,                  dom, "/data/cookies/data/name[text() = 'foo']");
        DomAsserts.assertCount("blacklisted cookie #2",             0,                  dom, "/data/cookies/data/name[text() = 'baz']");
    }


    @Test
    public void testEnableCookiesWithNoCookies() throws Exception
    {
        JsonAccessLayout layout = new JsonAccessLayout();
        layout.setEnableCookies(true);
        layout.setIncludeCookies("*");

        constructMocks("/example", "", "success!");

        applyLayoutAndParse(layout);

        DomAsserts.assertCount("has cookies object",                1,                  dom, "/data/cookies");
        DomAsserts.assertCount("number of cookies",                 0,                  dom, "/data/cookies/*");
    }


    @Test
    public void testTags() throws Exception
    {
        JsonAccessLayout layout = new JsonAccessLayout();
        layout.setTags("foo=bar,process={pid}");

        constructMocks("/example", "", "success!");

        applyLayoutAndParse(layout);

        DomAsserts.assertCount("has tags object",                   1,                  dom, "/data/tags");
        DomAsserts.assertCount("number of tags",                    2,                  dom, "/data/tags/*");
        DomAsserts.assertEquals("explicit tag",                     "bar",              dom, "/data/tags/foo");
        StringAsserts.assertRegex("substituted tag",                "\\d+",             new XPathWrapper("/data/tags/process").evaluateAsString(dom));
    }
}
