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
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;


/**
 *  A simple servlet that reports its headers and query parameters back to the client.
 */
public class ExampleServlet
extends HttpServlet
{
    private static final long serialVersionUID = 1L;

    private Logger logger = Logger.getLogger(getClass());


    @Override
    public String getServletInfo()
    {
        return "Test servlet";
    }


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException
    {
        logger.info("invoked via GET");
        generateOutput(request, response);
    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException
    {
        logger.info("invoked via POST");
        generateOutput(request, response);
    }


    /**
     *  Common code to generate output.
     */
    private void generateOutput(HttpServletRequest request, HttpServletResponse response)
    throws ServletException, IOException
    {
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();

        out.println("Parameters:");
        for (Object key : request.getParameterMap().keySet())
        {
            List<String> value = Arrays.asList((String[])request.getParameterMap().get(key));
            out.format("   %-24s = %s\n", key, value);
        }
        out.println();

        out.println("Headers:");
        for (Enumeration<String> headerItx = request.getHeaderNames() ; headerItx.hasMoreElements() ; )
        {
            String headerName =  headerItx.nextElement();
            out.format("   %-24s = %s\n", headerName, request.getHeader(headerName));
        }
        out.println();

        out.println("Attributes:");
        for (Enumeration<String> attrItx = request.getAttributeNames() ; attrItx.hasMoreElements() ; )
        {
            String attrName =  attrItx.nextElement();
            out.format("   %-24s = %s\n", attrName, request.getAttribute(attrName));
        }
        out.println();

        out.close();
    }

}
