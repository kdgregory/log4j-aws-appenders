// Copyright (c) Keith D Gregory, all rights reserved
package com.kdgregory.log4j.aws.internal.shared;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import com.amazonaws.util.EC2MetadataUtils;


/**
 *  Handles the standard substitution variables. You create a new instance whenever
 *  performing substitutions; instances are not thread-safe.
 */
public class Substitutions
{
    // we precompute all substitution values and cache them for future use; we don't
    // expect to be called in any sort of tight loop

    private String date;
    private String timestamp;
    private String hourlyTimestamp;
    private String startupTimestamp;
    private String pid;
    private String hostname;
    private String sequence;


    public Substitutions(Date curremtDate, int sequence)
    {
        RuntimeMXBean runtimeMx = ManagementFactory.getRuntimeMXBean();
        String vmName = runtimeMx.getName();

        pid = (vmName.indexOf('@') > 0)
            ? vmName.substring(0, vmName.indexOf('@'))
            : "unknown";

        hostname = (vmName.indexOf('@') > 0)
                 ? vmName.substring(vmName.indexOf('@') + 1, vmName.length())
                 : "unknown";

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        date = dateFormat.format(curremtDate);

        SimpleDateFormat timestampFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        timestampFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        timestamp = timestampFormat.format(curremtDate);
        hourlyTimestamp = timestamp.substring(0, 10) + "0000";  // yeah, it's a hack
        startupTimestamp = timestampFormat.format(new Date(runtimeMx.getStartTime()));

        this.sequence = String.valueOf(sequence);
    }


    /**
     *  Applies all substitutions. This is not particularly performant, but it
     *  won't be called frequently.
     */
    public String perform(String input)
    {
        if (input == null)
            return "";

        String output = input;
        do
        {
            input = output;
            output = substitute("{date}",            date,
                     substitute("{timestamp}",       timestamp,
                     substitute("{hourlyTimestamp}", hourlyTimestamp,
                     substitute("{startupTimestamp}", startupTimestamp,
                     substitute("{pid}",             pid,
                     substitute("{hostname}",        hostname,
                     substitute("{sequence}",        sequence,
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
     *  Performs simple subsitutions, where the tag fully describes the substitution.
     */
    private String substitute(String tag, String value, String input)
    {
        if (input == null)
            return "";

        if (value == null)
            return input;

        int index = input.indexOf(tag);
        if (index < 0)
            return input;

        return input.substring(0, index) + value + input.substring(index + tag.length(), input.length());
    }


    /**
     *  Substitutes the AWS account ID. This makes a call to AWS.
     */
    private String substituteAwsAccountId(String input)
    {
        String tag = "{aws:accountId}";
        int index = input.indexOf(tag);
        if (index < 0)
            return input;

        String accountId = Utils.retrieveAWSAccountId();
        return (accountId != null)
             ? substitute(tag, accountId, input)
             : input;
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

        String instanceId = EC2MetadataUtils.getInstanceId();
        if ((instanceId == null) || (instanceId.length() == 0))
            return input;

        return substitute(tag, instanceId, input);
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
        String propName = extractPropName("sysprop", input);
        if (propName == null)
            return input;

        return substitute("{" + "sysprop" + ":" + propName + "}", System.getProperty(propName), input);
    }


    /**
     *  Substitutes environment variables, where the variable depends on the tag.
     */
    private String substituteEnvar(String input)
    {
        String propName = extractPropName("env", input);
        if (propName == null)
            return input;

        return substitute("{" + "env" + ":" + propName + "}", System.getenv(propName), input);
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
