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

package com.kdgregory.log4j2.aws;

import java.util.Date;
import java.util.HashMap;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.lookup.StrLookup;

import com.kdgregory.logging.aws.common.Substitutions;


/**
 *  Allows Log4J2 to use library-specific substitutions.
 */
@Plugin(category = "Lookup", name = "awslogs")
public class Lookups
implements StrLookup
{
    // translation table from lookup keys to substitution keys
    private static HashMap<String,String> lookup2sub = new HashMap<>();
    static
    {
        lookup2sub.put("startupTimestamp",  "{startupTimestamp}");
        lookup2sub.put("pid",               "{pid}");
        lookup2sub.put("hostname",          "{hostname}");
        lookup2sub.put("awsAccountId",      "{aws:accountId}");
        lookup2sub.put("ec2InstanceId",     "{ec2:instanceId}");
        lookup2sub.put("ec2Region",         "{ec2:region}");
    }

    // none of these lookups will change over the life of the JVM, so we can create
    // one instance and use it forever
    private Substitutions substitutions = new Substitutions(new Date(), 0);


    @Override
    public String lookup(String key)
    {
        String sub = lookup2sub.get(key);
        if (sub == null)
            return null;

        return substitutions.perform(sub);
    }


    @Override
    public String lookup(LogEvent event, String key)
    {
        return lookup(key);
    }
}
