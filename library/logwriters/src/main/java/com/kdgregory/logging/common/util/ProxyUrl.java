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

package com.kdgregory.logging.common.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 *  Retrieves and parses a proxy URL. This is shared functionality for facade
 *  client factories.
 *  <p>
 *  The proxy URL is specified via environment variable, {@link #PROXY_ENVAR}.
 *  It's uniquely named: given the current state of SDK proxy handling, I did
 *  not want to reuse one of the "standard" envars.
 */
public class ProxyUrl
{
    public final static String  PROXY_ENVAR = "COM_KDGREGORY_LOGGING_PROXY_URL";

    private boolean isValid;
    private String scheme;
    private String hostname;
    private int port;
    private String username;
    private String password;


    /**
     *  Constructs an instance based on the environment variable, if it's set.
     */
    public ProxyUrl()
    {
        this(System.getenv(PROXY_ENVAR));
    }


    /**
     *  Constructs an instance from the passed string.
     */
    public ProxyUrl(String value)
    {
        if ((value == null) || (value.trim().length() == 0))
            return;

        Matcher matcher = Pattern.compile("([Hh][Tt][Tt][Pp][Ss]?)://(.*@)*([^:]+):(\\d{1,5})").matcher(value);
        isValid = matcher.matches();
        if (! isValid)
            return;

        scheme = matcher.group(1).toLowerCase();
        hostname = matcher.group(3).toLowerCase();
        port = Integer.parseInt(matcher.group(4));

        if (matcher.group(2) != null)
        {
            String[] userpw = matcher.group(2).replaceAll("@$", "").split(":");
            username = userpw[0];
            password = userpw.length > 1 ? userpw[1] : null;
        }
    }


    public boolean isValid()
    {
        return isValid;
    }


    public String getScheme()
    {
        return isValid ? scheme : null;
    }


    public String getHostname()
    {
        return isValid ? hostname : null;
    }


    public int getPort()
    {
        return port;
    }


    public String getUsername()
    {
        return isValid ? username : null;
    }


    public String getPassword()
    {
        return isValid ? password : null;
    }
}
