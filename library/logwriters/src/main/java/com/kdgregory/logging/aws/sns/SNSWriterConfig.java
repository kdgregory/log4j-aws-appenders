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

package com.kdgregory.logging.aws.sns;

import java.util.ArrayList;
import java.util.List;

import com.kdgregory.logging.aws.internal.AbstractWriterConfig;


/**
 *  Configuration for SNSLogWriter.
 *  <p>
 *  Note: the setters for name, ARN, and subject transparently replace empty
 *  or blank values with null. This is a flag that indicates that the value
 *  should not be used.
 */
public class SNSWriterConfig
extends AbstractWriterConfig<SNSWriterConfig>
{
    private String  topicName;
    private String  topicArn;
    private String  subject;
    private boolean autoCreate;


    /**
     *  Default constructor. This is used almost everywhere.
     */
    public SNSWriterConfig()
    {
        super();
        super.setBatchDelay(1);
    }


    /**
     *  Copy constructor. This is used for some tests.
     */
    public SNSWriterConfig(SNSWriterConfig source)
    {
        super(source);
        this.topicName = source.topicName;
        this.topicArn = source.topicArn;
        this.subject = source.subject;
        this.autoCreate = source.autoCreate;
    }

//----------------------------------------------------------------------------
//  Accessors
//----------------------------------------------------------------------------

    public String getTopicName()
    {
        return topicName;
    }

    public SNSWriterConfig setTopicName(String value)
    {
        topicName = (value != null) && value.trim().isEmpty() ? null : value;
        return this;
    }


    public String getTopicArn()
    {
        return topicArn;
    }

    public SNSWriterConfig setTopicArn(String value)
    {
        topicArn = (value != null) && value.trim().isEmpty() ? null : value;
        return this;
    }


    public String getSubject()
    {
        return subject;
    }

    public SNSWriterConfig setSubject(String value)
    {
        subject = value;
        return this;
    }


    public boolean getAutoCreate()
    {
        return autoCreate;
    }

    public SNSWriterConfig setAutoCreate(boolean value)
    {
        autoCreate = value;
        return this;
    }


    @Override
    public SNSWriterConfig setBatchDelay(long value)
    {
        // SNS writer uses a fixed batch delay; this call is a no-op
        return this;
    }

//----------------------------------------------------------------------------
//  Other public methods
//----------------------------------------------------------------------------

    /**
     *  Validates the configuration, returning a list of any validation errors.
     *  An empty list indicates a valid config.
     */
    public List<String> validate()
    {
        List<String> result = new ArrayList<>();

        if ((topicName == null) && (topicArn == null))
        {
            result.add("must specify either ARN or topic name");
        }

        if ((topicName != null) && !topicName.matches(SNSConstants.TOPIC_NAME_REGEX))
        {
            result.add("invalid SNS topic name: " + topicName);
        }

        if ((topicArn != null) && !topicArn.matches(SNSConstants.TOPIC_ARN_REGEX))
        {
            result.add("invalid SNS topic ARN: " + topicArn);
        }

        if ((subject != null) && (subject.length() > 100))
        {
            result.add("invalid SNS subject: over 100 characters");
        }

        if ((subject != null) && (subject.charAt(0) == ' '))
        {
            result.add("invalid SNS subject: begins with space");
        }

        if (subject != null)
        {
            for (int ii = 0 ; ii < subject.length() ; ii++)
            {
                if (subject.charAt(ii) > 127)
                {
                    result.add("invalid SNS subject: must contain ASCII characters only");
                    break;
                }
                if ((subject.charAt(ii) < 32) || (subject.charAt(ii) == 127))
                {
                    result.add("invalid SNS subject: may not contain control characters or newlines");
                    break;
                }
            }
        }

        if (autoCreate && (topicArn != null))
        {
            result.add("must not specify ARN if auto-create enabled");
        }

        return result;
    }
}
