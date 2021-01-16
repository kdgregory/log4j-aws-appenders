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

package com.kdgregory.logging.aws;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;
import static org.junit.Assert.*;

import net.sf.kdgcommons.lang.StringUtil;

import com.kdgregory.logging.aws.sns.SNSWriterConfig;


/**
 *  Verifies validation and coordinated-setter logic.
 */
public class TestSNSWriterConfig
{
    // this is valid; set fields to make invalid
    // note that a config can take both name and ARN; latter has preference

    private SNSWriterConfig config = new SNSWriterConfig()
                                     .setTopicName("Example_SNSName-123")
                                     .setTopicArn("arn:aws:sns:us-east-2:123456789012:Example_SNSName-123")
                                     .setSubject("Valid subject");

//----------------------------------------------------------------------------
//  Testcases
//----------------------------------------------------------------------------

    @Test
    public void testValidConfig() throws Exception
    {
        assertEquals("passed validation",
                     Collections.emptyList(),
                     config.validate());
    }


    @Test
    public void testMustSpecifyNameOrArn() throws Exception
    {
        config.setTopicName("").setTopicArn("");

        assertEquals("validation errors",
                     Arrays.asList("must specify either ARN or topic name"),
                     config.validate());
    }


    @Test
    public void testMustSpecifyNameNotArnIfAutoCreate() throws Exception
    {
        // default config specifies name
        config.setAutoCreate(true).setTopicArn("");

        assertEquals("default config passed validation",
                     Collections.emptyList(),
                     config.validate());

        config.setTopicArn("arn:aws:sns:us-east-2:123456789012:Example_SNSName-123");

        assertEquals("validation errors",
                     Arrays.asList("must not specify ARN if auto-create enabled"),
                     config.validate());
    }


    @Test
    public void testInvalidName() throws Exception
    {
        config.setTopicName("I'm invalid");

        assertEquals("validation errors",
                     Arrays.asList("invalid SNS topic name: I'm invalid"),
                     config.validate());
    }

    @Test
    public void testInvalidArn() throws Exception
    {
        config.setTopicArn("not_an_arn");

        assertEquals("validation errors",
                     Arrays.asList("invalid SNS topic ARN: not_an_arn"),
                     config.validate());
    }


    @Test
    public void testInvalidSubjectTooLong() throws Exception
    {
        config.setSubject(StringUtil.repeat('X', 101));

        assertEquals("validation errors",
                     Arrays.asList("invalid SNS subject: over 100 characters"),
                     config.validate());
    }


    @Test
    public void testInvalidSubjectNewline() throws Exception
    {
        config.setSubject("multi\nline");

        assertEquals("validation errors",
                     Arrays.asList("invalid SNS subject: may not contain control characters or newlines"),
                     config.validate());
    }


    @Test
    public void testInvalidSubjectStartsWithSpace() throws Exception
    {
        config.setSubject(" invalid");

        assertEquals("validation errors",
                     Arrays.asList("invalid SNS subject: begins with space"),
                     config.validate());
    }


    @Test
    public void testInvalidSubjectNotAscii() throws Exception
    {
        config.setSubject("\u00d8ber");

        assertEquals("validation errors",
                     Arrays.asList("invalid SNS subject: must contain ASCII characters only"),
                     config.validate());
    }


}
