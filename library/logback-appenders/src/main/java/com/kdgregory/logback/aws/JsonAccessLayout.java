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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import ch.qos.logback.access.spi.IAccessEvent;

import com.kdgregory.logback.aws.internal.AbstractJsonLayout;


/**
 *  Formats an <code>IAccessEvent</code> as a JSON string, with additional parameters.
 *  <p>
 *  The JSON object always contains the following properties. Most come from the event,
 *  but some are extracted from the request itself (which is provided by the event).
 *  <ul>
 *  <li> <code>timestamp</code>:        the date/time that the message was logged, in ISO-8601 UTC format.
 *  <li> <code>hostname</code>:         the name of the host running the app-server, extracted from
 *                                      <code>RuntimeMxBean</code> (may not be available on all platforms).
 *  <li> <code>processId</code>:        the process ID of the app-server.
 *  <li> <code>thread</code>:           the name of the thread that processed the request.
 *  <li> <code>elapsedTime</code>:      the elapsed time for request processing, in milliseconds.
 *  <li> <code>protocol</code>:         the name and version of the request protocol (eg, "HTTP/1.1").
 *  <li> <code>requestMethod</code>:    the HTTP request method (eg, "GET").
 *  <li> <code>requestURI</code>:       the requested resource, excluding host, port, or query string.
 *  <li> <code>statusCode</code>:       the response status code.
 *  <li> <code>bytesSent</code>:        the number of bytes (content-length) of the response.
 *  <li> <code>remoteIP</code>:         the IP address of the originating server.
 *  <li> <code>forwardedFor</code>:     the value of the <code>X-Forwarded-For</code> header, if it exists.
 *                                      Used to identify source IP when running behind a load balancer.
 *  </ul>
 *  <p>
 *  The following properties are potentially expensive to compute, may leak secrets, or significantly
 *  increase the size of the generated JSON, so will only appear if specifically enabled:
 *  <ul>
 *  <li> <code>instanceId</code>:       the EC2 instance ID of the machine where the logger is running.
 *                                      WARNING: do not enable this elsewhere, as the operation to retrieve
 *                                      this value may take a long time (and return nothing).
 *  <li> <code>cookies</code>:          a list of cookie objects, with all attributes (name, value, domain,
 *                                      max age, &c). These are subject to whitelisting and blacklisting
 *                                      via {@link #includeCookies} and {@link #excludeCookies}.
 *  <li> <code>parameters</code>:       the request parameters, if any. These are subject to whitelisting and blacklisting
 *                                      via {@link #includeParameters} and {@link #excludeParameters}.
 *  <li> <code>queryString</code>:      the query string, if any.
 *  <li> <code>remoteHost</code>:       the remote hostname. This may involve a DNS lookup.
 *  <li> <code>requestHeaders</code>:   the request headers. These are subject to whitelisting and blacklisting
 *                                      via {@link #includeHeaders} and {@link #excludeHeaders}.
 *  <li> <code>responseHeaders</code>:  the response headers. These are subject to whitelisting and blacklisting
 *                                      via {@link #includeHeaders} and {@link #excludeHeaders}.
 *  <li> <code>server</code>:           the destination server name. This is normally extracted from the
 *                                      <code>Host</code> request header, but may involve a DNS lookup (see
 *                                      J2EE docs for more info).
 *  <li> <code>sessionId</code>:        the session ID. This may not be available, which will trigger an
 *                                      ignored exception on retrieval.
 *  <li> <code>user</code>:             the remote user, if one exists.
 *  </ul>
 *  <p>
 *  Lastly, you can define a set of user tags, which are written as a child object with
 *  the key <code>tags</code>. These are intended to provide program-level information,
 *  in the case where multiple programs send their logs to the same stream. They are set
 *  as a single comma-separate string, which may contain substitution values (example:
 *  <code>appserver-name=Fribble,startedAt={startupTimestamp}</code>).
 *  <p>
 *  WARNING: you should not rely on the order in which elements are output. Any apparent
 *  ordering is an implementation artifact and subject to change without notice.
 */
public class JsonAccessLayout
extends AbstractJsonLayout<IAccessEvent>
{

//----------------------------------------------------------------------------
//  Configuration
//----------------------------------------------------------------------------

    private boolean enableServer;
    private boolean enableRemoteHost;
    private boolean enableRemoteUser;
    private boolean enableSessionId;
    private boolean enableRequestHeaders;
    private boolean enableResponseHeaders;
    private boolean enableCookies;
    private boolean enableParameters;
    private boolean enableQueryString;

    private String includeHeaders;
    private String excludeHeaders;

    private boolean whitelistAllHeaders = false;
    private Set<String> headerWhitelist = Collections.emptySet();
    private Set<String> headerBlacklist = Collections.emptySet();

    private String includeCookies;
    private String excludeCookies;

    private boolean whitelistAllCookies = false;
    private Set<String> cookieWhitelist = Collections.emptySet();
    private Set<String> cookieBlacklist = Collections.emptySet();

    private String includeParameters;
    private String excludeParameters;

    private boolean whitelistAllParameters = false;
    private Set<String> parameterWhitelist = Collections.emptySet();
    private Set<String> parameterBlacklist = Collections.emptySet();


    /**
     *  Optionally enables inserting the hostname of the destination server, taken
     *  from the HTTP request.
     */
    public void setEnableServer(boolean value)
    {
        enableServer = value;
    }


    public boolean getEnableServer()
    {
        return enableServer;
    }


    /**
     *  Optionally enables looking up the hostname of the remote host. This
     *  is likely to be an expensive operation.
     */
    public void setEnableRemoteHost(boolean value)
    {
        enableRemoteHost = value;
    }


    public boolean getEnableRemoteHost()
    {
        return enableRemoteHost;
    }


    /**
     *  Enables retrieving the remote user's name, if it's known.
     */
    public void setEnableRemoteUser(boolean value)
    {
        enableRemoteUser = value;
    }


    public boolean getEnableRemoteUser()
    {
        return enableRemoteUser;
    }


    /**
     *  Optionally enables storing the session ID in the result.
     */
    public void setEnableSessionId(boolean value)
    {
        enableSessionId = value;
    }


    public boolean getEnableSessionId()
    {
        return enableSessionId;
    }


    /**
     *  Enables storing request headers in the output. This must be used along with
     *  either <code>includeHeaders</code> or <code>excludeHeaders</code> to avoid
     *  accidentally leaking secrets.
     */
    public void setEnableRequestHeaders(boolean value)
    {
        enableRequestHeaders = value;
    }


    public boolean getEnableRequestHeaders()
    {
        return enableRequestHeaders;
    }


    /**
     *  Enables storing request headers in the output. This must be used along with
     *  either <code>includeHeaders</code> and <code>excludeHeaders</code> to avoid
     *  accidentally leaking secrets.
     */
    public void setEnableResponseHeaders(boolean value)
    {
        enableResponseHeaders = value;
    }


    public boolean getEnableResponseHeaders()
    {
        return enableResponseHeaders;
    }


    /**
     *  Sets the list of headers that will be included in the output. This is a comma-separated
     *  list of names or the special value "*". If given a list of names, only those names will
     *  be included; "*" includes all headers. Note that <code>excludeHeaders</code> still
     *  applies; even if a header is explicitly whitelisted it may still be omitted.
     */
    public void setIncludeHeaders(String value)
    {
        includeHeaders = value;
        if ("*".equals(value))
        {
            whitelistAllHeaders = true;
        }
        else
        {
            headerWhitelist = new HashSet<String>();
            for (String headerName : value.split(","))
            {
                headerWhitelist.add(headerName.toLowerCase());
            }
        }
    }


    public String getIncludeHeaders()
    {
        return includeHeaders;
    }


    /**
     *  Sets the list of headers that will be explicitly excluded from the output. This is
     *  a comma-separated list of names, and takes precedence over <code>includeHeaders</code>.
     */
    public void setExcludeHeaders(String value)
    {
        excludeHeaders = value;
        headerBlacklist = new HashSet<String>();
        for (String headerName : value.split(","))
        {
            headerBlacklist.add(headerName.toLowerCase());
        }
    }


    public String getExcludeHeaders()
    {
        return excludeHeaders;
    }


    /**
     *  Enables storing request parameters in the output. This must be used along with
     *  either <code>includeParameters</code> and <code>excludeParameters</code> to
     *  avoid accidentally leaking secrets.
     */
    public void setEnableParameters(boolean value)
    {
        enableParameters = value;
    }


    public boolean getEnableParameters()
    {
        return enableParameters;
    }


    /**
     *  Sets the list of parameters that will be included in the output. This is a comma-separated
     *  list of names, or the special value "*". If given a list of names, only those names will
     *  be included; "*" includes all parameters. Note that <code>excludeParameters</code> still
     *  applies; even if a parameter is explicitly whitelisted it may still be omitted.
     */
    public void setIncludeParameters(String value)
    {
        includeParameters = value;
        if ("*".equals(value))
        {
            whitelistAllParameters = true;
        }
        else
        {
            parameterWhitelist = new HashSet<String>();
            for (String paramName : value.split(","))
            {
                parameterWhitelist.add(paramName.toLowerCase());
            }
        }
    }


    public String getIncludeParameters()
    {
        return includeParameters;
    }


    /**
     *  Sets the list of parameters that will be explicitly excluded from the output. This is
     *  a comma-separated list of names, and takes precedence over <code>includeParameters</code>.
     */
    public void setExcludeParameters(String value)
    {
        excludeParameters = value;
        parameterBlacklist = new HashSet<String>(Arrays.asList(value.split(",")));
    }


    public String getExcludeParameters()
    {
        return excludeParameters;
    }


    /**
     *  Enables storing request cookies in the output. You must also configure,
     *  <code>includeCookies</code> and (optionally) <code>excludeCookies</code>
     *  to avoid accidentally leaking secrets.
     */
    public void setEnableCookies(boolean value)
    {
        enableCookies = value;
    }


    public boolean getEnableCookies()
    {
        return enableCookies;
    }


    /**
     *  Sets the list of cookies that will be included in the output. This is a comma-separated
     *  list of names, or the special value "*". If given a list of names, only those names will
     *  be included; "*" includes all cookies. Note that <code>excludeCookies</code> takes
     *  precedence; even if a cookie is explicitly whitelisted it may still be omitted.
     */
    public void setIncludeCookies(String value)
    {
        includeCookies = value;
        if ("*".equals(value))
        {
            whitelistAllCookies = true;
        }
        else
        {
            cookieWhitelist = new HashSet<String>();
            for (String cookieName : value.split(","))
            {
                cookieWhitelist.add(cookieName.toLowerCase());
            }
        }
    }


    public String getIncludeCookies()
    {
        return includeCookies;
    }


    /**
     *  Sets the list of cookies that will be explicitly excluded from the output. This is a
     *  comma-separated list of names, and takes precedence over <code>includeCookies</code>.
     */
    public void setExcludeCookies(String value)
    {
        excludeCookies = value;
        cookieBlacklist = new HashSet<String>(Arrays.asList(value.split(",")));
    }


    public String getExcludeCookies()
    {
        return excludeCookies;
    }


    /**
     *  Enables storing the unparsed request query string in the output. This is
     *  not as useful as storing the request parameters, and may leak secrets.
     */
    public void setEnableQueryString(boolean value)
    {
        enableQueryString = value;
    }


    public boolean getEnableQueryString()
    {
        return enableQueryString;
    }

//----------------------------------------------------------------------------
//  Layout Overrides
//----------------------------------------------------------------------------

    @Override
    public String doLayout(IAccessEvent event)
    {
        Map<String,Object> map = new TreeMap<String,Object>();
        map.put("timestamp",            new Date(event.getTimeStamp()));
        map.put("thread",               event.getThreadName());
        map.put("elapsedTime",          event.getElapsedTime());
        map.put("protocol",             event.getProtocol());
        map.put("requestMethod",        event.getMethod());
        map.put("requestURI",           event.getRequestURI());
        map.put("statusCode",           event.getStatusCode());
        map.put("bytesSent",            event.getContentLength());
        map.put("remoteIP",             event.getRemoteAddr());

        if (enableQueryString)
            map.put("queryString",      event.getQueryString());

        if (enableRemoteHost)
            map.put("remoteHost",       event.getRemoteHost());

        if (enableServer)
            map.put("server",           event.getServerName());

        if (enableRemoteUser)
            map.put("user",             event.getRemoteUser());

        if (enableSessionId)
        {
            try
            {
                map.put("sessionId",    event.getSessionID());
            }
            catch (Exception ex) // Tomcat is IllegalStateException; other servers might differ
            {
                map.put("sessionId",    "");
            }
        }

        if (enableRequestHeaders)
            map.put("requestHeaders",   applyFilters(event.getRequestHeaderMap(), whitelistAllHeaders, headerWhitelist, headerBlacklist));

        if (enableResponseHeaders)
            map.put("responseHeaders",  applyFilters(event.getResponseHeaderMap(), whitelistAllHeaders, headerWhitelist, headerBlacklist));

        if (enableCookies)
            map.put("cookies",          getCookies(event.getRequest()));

        if (enableParameters)
            map.put("parameters",       applyFilters(event.getRequestParameterMap(), whitelistAllParameters, parameterWhitelist, parameterBlacklist));

        String forwardedFor = event.getRequestHeader("x-forwarded-for");
        if (forwardedFor != null)
            map.put("forwardedFor",     forwardedFor);

        return addCommonAttributesAndConvert(map);
    }

//----------------------------------------------------------------------------
//  Internals
//----------------------------------------------------------------------------

    private List<Map<String,Object>> getCookies(HttpServletRequest request)
    {
        Cookie[] cookies = request.getCookies();
        if (cookies == null)
            return Collections.emptyList();

        List<Map<String,Object>> result = new ArrayList<Map<String,Object>>();
        for (Cookie cookie : cookies)
        {
            if (! include(cookie.getName(), whitelistAllCookies, cookieWhitelist, cookieBlacklist))
                continue;

            Map<String,Object> entry = new TreeMap<String,Object>();
            entry.put("name",       cookie.getName());
            entry.put("domain",     cookie.getDomain());
            entry.put("path",       cookie.getPath());
            entry.put("value",      cookie.getValue());
            entry.put("maxAge",     cookie.getMaxAge());
            entry.put("comment",    cookie.getComment());
            entry.put("version",    cookie.getVersion());
            entry.put("isSecure",   cookie.getSecure());
            entry.put("isHttpOnly", cookie.isHttpOnly());
            result.add(entry);
        }
        return result;
    }

    private static Map<String,Object> applyFilters(Map<String,?> source, boolean whitelistAll, Set<String> whitelist, Set<String> blacklist)
    {
        Map<String,Object> result = new TreeMap<String,Object>();
        for (Map.Entry<String,?> entry : source.entrySet())
        {
            String key = entry.getKey();
            if (include(key, whitelistAll, whitelist, blacklist))
                result.put(key, flatten(entry.getValue()));
        }
        return result;
    }


    private static boolean include(String name, boolean whitelistAll, Set<String> whitelist, Set<String> blacklist)
    {
        name = name.toLowerCase();
        boolean whitelisted = whitelistAll || whitelist.contains(name);
        boolean blacklisted = blacklist.contains(name);
        return whitelisted && ! blacklisted;
    }


    private static Object flatten(Object value)
    {
        if (value instanceof Object[])
        {
            Object[] array = (Object[])value;
            if (array.length == 1)
                value = array[0];
            else
                value = Arrays.asList(array);
        }
        return String.valueOf(value);
    }
}
