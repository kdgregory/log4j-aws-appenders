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

import org.junit.Ignore;
import org.junit.Test;

import org.apache.logging.log4j.core.lookup.StrSubstitutor;

import static net.sf.kdgcommons.test.StringAsserts.*;

import com.kdgregory.log4j2.testhelpers.TestableCloudWatchAppender;


public class TestLookups
extends AbstractUnitTest<TestableCloudWatchAppender>
{
    public TestLookups()
    {
        super("TestLookups/", null);
    }


    @Test
    public void testLocalLookups() throws Exception
    {
        initialize("commonConfig");

        StrSubstitutor strsub = logger.getContext().getConfiguration().getStrSubstitutor();

        // these assertions just use regexes, rely on the underlying subtitutions tests for correctness
        assertRegex("\\d{14}",          strsub.replace("${awslogs:startupTimestamp}"));
        assertRegex("\\d{1,5}",         strsub.replace("${awslogs:pid}"));
        assertRegex("[A-Za-z0-9-.]+",   strsub.replace("${awslogs:hostname}"));
    }


    @Test @Ignore
    // this test can only be run on an EC2 instance
    public void testAWSLookups() throws Exception
    {
        initialize("commonConfig");

        StrSubstitutor strsub = logger.getContext().getConfiguration().getStrSubstitutor();

        // these assertions just use regexes, rely on the underlying subtitutions tests for correctness
        assertRegex("\\d{12}",          strsub.replace("${awslogs:awsAccountId}"));
        assertRegex("i-[0-9a-f]+",      strsub.replace("${awslogs:ec2InstanceId}"));
        assertRegex("..-.+-[0-9]",      strsub.replace("${awslogs:ec2Region}"));
    }

}
