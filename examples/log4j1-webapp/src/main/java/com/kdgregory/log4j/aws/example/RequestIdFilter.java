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

package com.kdgregory.log4j.aws.example;

import java.io.IOException;
import java.util.UUID;

import javax.servlet.DispatcherType;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.apache.log4j.MDC;

/**
 *  A servlet filter that associates a unique identifier with the request, and
 *  stores that identifier in the mapped diagnostic context to allow correlation
 *  of log messages associated with the same request.
 *  <p>
 *  If you're using a micro-service architecture, this filter could retrieve the
 *  unique identifier from a request header (creating it if needed) to trace a
 *  request through the various services involved in its processing.
 */
public class RequestIdFilter
implements Filter
{
    public final static String REQUEST_IDENTIFIER_KEY = "requestUUID";

    @Override
    public void init(FilterConfig config) throws ServletException
    {
        // nothing needs to happen here
    }


    @Override
    public void destroy()
    {
        // nothing needs to happen here
    }


    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
    throws IOException, ServletException
    {
        // we only want to create a new ID for requests coming from the client; if the
        // request is a forward or include then we will re-use whatever is already set

        if (request.getDispatcherType() == DispatcherType.REQUEST)
        {
            String requestIdentifer = UUID.randomUUID().toString();
            MDC.put(REQUEST_IDENTIFIER_KEY, requestIdentifer);
        }

        chain.doFilter(request, response);

        // we don't want to leave the value in the MDC for requests that don't use this filter
        // a better alternative would be to separate the request ID creation and MDC management,
        // and completely clear the MDC on the way out of a request invocation

        if (request.getDispatcherType() == DispatcherType.REQUEST)
        {
            MDC.remove(REQUEST_IDENTIFIER_KEY);
        }
    }

}
