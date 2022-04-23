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

package com.kdgregory.logging.aws.facade.v2.internal;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import software.amazon.awssdk.http.apache.ProxyConfiguration;


/**
 *  Static utility methods for building clients. These are extracted from
 *  {@link ClientFactory} for separate testing.
 */
public class ClientUtils
{
    /**
     *  Parses a proxy URL and returns the client configuration object.
     *  Returns null if passed null or unable to parse.
     */
    protected static ProxyConfiguration parseProxyUrl(String proxyUrl)
    {
        if (proxyUrl == null)
            return null;

        Matcher matcher = Pattern.compile("([Hh][Tt][Tt][Pp][Ss]?)://(.*@)*([^:]+):(\\d{1,5})").matcher(proxyUrl);
        if (! matcher.matches())
            return null;

        try
        {
            String reconstructedUrl = matcher.group(1).toLowerCase() + "://" + matcher.group(3).toLowerCase() + ":" + Integer.parseInt(matcher.group(4));
            ProxyConfiguration.Builder builder = ProxyConfiguration.builder()
                                                 .endpoint(new URI(reconstructedUrl))
                                                 .addNonProxyHost("169.254.169.254")
                                                 .useSystemPropertyValues(Boolean.FALSE);
            if (matcher.group(2) != null)
            {
                String group2 = matcher.group(2).replaceAll("@$", "");
                String[] userpw = group2.split(":");
                builder.username(userpw[0]);
                if (userpw.length > 1)
                {
                    builder.password(userpw[1]);
                }
            }

            return builder.build();
        }
        catch (URISyntaxException ex)
        {
            throw new RuntimeException("invalid proxy URL: " + proxyUrl);
        }
    }
}
