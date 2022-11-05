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

package com.kdgregory.logging.aws.cloudwatch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.kdgregory.logging.aws.internal.AbstractWriterConfig;


/**
 *  Configuration for CloudWatchLogWriter.
 */
public class CloudWatchWriterConfig
extends AbstractWriterConfig<CloudWatchWriterConfig>
{
    public final static String          DEFAULT_LOG_STREAM_NAME     = "{startupTimestamp}";
    public final static Integer         DEFAULT_RETENTION_PERIOD    = null; // unlimited
    public final static boolean         DEFAULT_DEDICATED_WRITER    = true;


    private String                      logGroupName;
    private String                      logStreamName               = DEFAULT_LOG_STREAM_NAME;
    private Integer                     retentionPeriod             = DEFAULT_RETENTION_PERIOD;
    private boolean                     dedicatedWriter             = DEFAULT_DEDICATED_WRITER;


    public String getLogGroupName()
    {
        return logGroupName;
    }

    public CloudWatchWriterConfig setLogGroupName(String value)
    {
        logGroupName = value;
        return this;
    }


    public String getLogStreamName()
    {
        return logStreamName;
    }

    public CloudWatchWriterConfig setLogStreamName(String value)
    {
        logStreamName = value;
        return this;
    }


    public Integer getRetentionPeriod()
    {
        return retentionPeriod;
    }

    public CloudWatchWriterConfig setRetentionPeriod(Integer value)
    {
        retentionPeriod = value;
        return this;
    }


    public boolean getDedicatedWriter()
    {
        return dedicatedWriter;
    }

    public CloudWatchWriterConfig setDedicatedWriter(boolean value)
    {
        dedicatedWriter = value;
        return this;
    }


    /**
     *  Validates the configuration, returning a list of any validation errors.
     *  An empty list indicates a valid config.
     */
    public List<String> validate()
    {
        List<String> result = new ArrayList<>();

        if (logGroupName == null)
        {
            result.add("missing log group name");
        }
        else if (logGroupName.isEmpty())
        {
            result.add("blank log group name");
        }
        else if (! Pattern.matches(CloudWatchConstants.ALLOWED_GROUP_NAME_REGEX, logGroupName))
        {
            result.add("invalid log group name: " + logGroupName);
        }

        if (logStreamName == null)
        {
            result.add("missing log stream name");
        }
        else if (logStreamName.isEmpty())
        {
            result.add("blank log stream name");
        }
        else if (! Pattern.matches(CloudWatchConstants.ALLOWED_STREAM_NAME_REGEX, logStreamName))
        {
            result.add("invalid log stream name: " + logStreamName);
        }

        try
        {
            CloudWatchConstants.validateRetentionPeriod(retentionPeriod);
        }
        catch (IllegalArgumentException ex)
        {
            result.add(ex.getMessage());
        }

        return result;
    }
}
