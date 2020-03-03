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

package com.kdgregory.logging.aws.common;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.amazonaws.util.EC2MetadataUtils;

import com.kdgregory.logging.aws.internal.Utils;


/**
 *  Handles the standard substitution variables. Standard usage is to create a
 *  new instance whenever you need one; the current timestamp would otherwise
 *  become stale.
 *  <p>
 *  Values are lazily retrieved. Instances are thread-safe, but not thread-optimized
 *  (concurrent lazy retrieves of the same value are possible).
 */
public class Substitutions
{
    // these are set by constructor
    private Date currentDate;
    private String sequence;
    private RuntimeMXBean runtimeMx;

    // these are lazily retrieved; volatile as a habit
    private volatile String date;
    private volatile String timestamp;
    private volatile String hourlyTimestamp;
    private volatile String startupTimestamp;
    private volatile String pid;
    private volatile String hostname;
    private volatile String accountId;
    private volatile String instanceId;


    public Substitutions(Date currentDate, int sequence)
    {
        this.currentDate = currentDate;
        this.sequence = String.valueOf(sequence);
        this.runtimeMx = ManagementFactory.getRuntimeMXBean();
    }


    /**
     *  Applies all substitutions. This is not particularly performant, but it
     *  won't be called frequently. If passed <code>null</code> returns same.
     */
    public String perform(String input)
    {
        if (input == null)
            return null;

        String output = input;
        do
        {
            input = output;
            output = substitute("{sequence}", sequence,
                     substituteDate(
                     substituteTimestamp(
                     substituteHourlyTimestamp(
                     substituteStartupTimestamp(
                     substitutePid(
                     substituteHostname(
                     substituteAwsAccountId(
                     substituteEC2InstanceId(
                     substituteEC2Region(
                     substituteSysprop(
                     substituteEnvar(
                     input))))))))))));
        }
        while (! output.equals(input));
        return output;
    }
    
    
    /**
     *  This function handles the actual replacement; it should only be called
     *  if you know that there's a valud substitution.
     */
    private String substitute(String input, int start, String tag, String value)
    {
        return input.substring(0, start) + value + input.substring(start + tag.length(), input.length());
    }

    // all of the following methods take the input string as their last parameter
    // so that they can be chained together

    private String substitute(String tag, String value, String input)
    {
        if (input == null)
            return "";

        if (value == null)
            return input;

        int index = input.indexOf(tag);
        if (index < 0)
            return input;

        return substitute(input, index, tag, value);
    }


    private String substituteDate(String input)
    {
        String tag = "{date}";
        
        int index = input.indexOf(tag);
        if (index < 0)
            return input;

        if (date == null)
        {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            date = formatter.format(currentDate);
        }

        return substitute(input, index, tag, date);
    }


    private String substituteTimestamp(String input)
    {
        String tag = "{timestamp}";
        
        int index = input.indexOf(tag);
        if (index < 0)
            return input;

        if (timestamp == null)
        {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            timestamp = formatter.format(currentDate);
        }

        return substitute(input, index, tag, timestamp);
    }


    private String substituteHourlyTimestamp(String input)
    {
        String tag = "{hourlyTimestamp}";
        
        int index = input.indexOf(tag);
        if (index < 0)
            return input;

        if (hourlyTimestamp == null)
        {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHH'0000'");
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            hourlyTimestamp = formatter.format(currentDate);
        }

        return substitute(input, index, tag, hourlyTimestamp);
    }


    private String substituteStartupTimestamp(String input)
    {
        String tag = "{startupTimestamp}";
        
        int index = input.indexOf(tag);
        if (index < 0)
            return input;

        if (startupTimestamp == null)
        {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            startupTimestamp = formatter.format(new Date(runtimeMx.getStartTime()));
        }

        return substitute(input, index, tag, startupTimestamp);
    }


    private String substitutePid(String input)
    {
        String tag = "{pid}";
        
        int index = input.indexOf(tag);
        if (index < 0)
            return input;

        if (pid == null)
        {
            String vmName = runtimeMx.getName();
            pid = (vmName.indexOf('@') > 0)
                ? vmName.substring(0, vmName.indexOf('@'))
                : "unknown";
        }

        return substitute(input, index, tag, pid);
    }


    private String substituteHostname(String input)
    {
        String tag = "{hostname}";
        
        int index = input.indexOf(tag);
        if (index < 0)
            return input;

        if (hostname == null)
        {
            String vmName = runtimeMx.getName();
            hostname = (vmName.indexOf('@') > 0)
                     ? vmName.substring(vmName.indexOf('@') + 1, vmName.length())
                     : "unknown";
        }

        return substitute(input, index, tag, hostname);
    }


    private String substituteAwsAccountId(String input)
    {
        String tag = "{aws:accountId}";

        int index = input.indexOf(tag);
        if (index < 0)
            return input;

        if (accountId == null)
        {
            accountId = Utils.retrieveAWSAccountId();
            accountId = (accountId != null)
                      ? accountId
                      : "unknown-account";
        }

        return substitute(input, index, tag, accountId);
    }


    /**
     *  Substitutes the EC2 instance ID. If not running on EC2 we won't be able
     *  to retrieve instance metadata (and it takes a long time to learn that,
     *  waiting for a timeout) so this isn't handled as a "simple" substitution.
     */
    private String substituteEC2InstanceId(String input)
    {
        String tag = "{ec2:instanceId}";
        int index = input.indexOf(tag);
        if (index < 0)
        {
            tag = "{instanceId}";
            index = input.indexOf(tag);
            if (index < 0)
                return input;
        }

        if (instanceId == null)
        {
            instanceId = EC2MetadataUtils.getInstanceId();
            if ((instanceId == null) || (instanceId.length() == 0))
            {
                instanceId = "unknown-instance";
            }
        }

        return substitute(input, index, tag, instanceId);
    }


    /**
     *  Substitutes the EC2 regsion. If not running on EC2 we won't be able
     *  to retrieve instance metadata (and it takes a long time to learn that,
     *  waiting for a timeout) so this isn't handled as a "simple" substitution.
     */
    private String substituteEC2Region(String input)
    {
        String tag = "{ec2:region}";
        int index = input.indexOf(tag);
        if (index < 0)
            return input;

        String region = EC2MetadataUtils.getEC2InstanceRegion();
        if ((region == null) || (region.length() == 0))
            return input;

        return substitute(tag, region, input);
    }

    /**
     *  Substitutes system properties, where the property depends on the tag.
     */
    private String substituteSysprop(String input)
    {
        String rawPropName = extractPropName("sysprop", input);
        if (rawPropName == null)
            return input;

        String[] parts = rawPropName.split(":");
        String propValue = parts[0].length() == 0
                         ? null
                         : System.getProperty(parts[0]);
        if ((propValue == null) && (parts.length == 2))
            propValue = parts[1];

        return substitute("{" + "sysprop" + ":" + rawPropName + "}", propValue, input);
    }


    /**
     *  Substitutes environment variables, where the variable depends on the tag.
     */
    private String substituteEnvar(String input)
    {
        String rawPropName = extractPropName("env", input);
        if (rawPropName == null)
            return input;

        String[] parts = rawPropName.split(":");
        String propValue = parts[0].length() == 0
                         ? null
                         : System.getenv(parts[0]);
        if ((propValue == null) && (parts.length == 2))
            propValue = parts[1];

        return substitute("{" + "env" + ":" + rawPropName + "}", propValue, input);
    }


    /**
     *  Extracts the property name for a colon-delimited tag, null if unable to
     *  do so.
     */
    private String extractPropName(String tagType, String input)
    {
        String tagForm = "{" + tagType + ":";
        int index1 = input.indexOf(tagForm);
        if (index1 < 0)
            return null;

        int index2 = input.indexOf("}", index1);
        if (index2 < 0)
            return null;

        return input.substring(index1 + tagForm.length(), index2);
    }
}
