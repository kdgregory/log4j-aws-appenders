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

import java.util.UUID;

import org.junit.Test;
import static org.junit.Assert.*;

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
    public void testSimpleLookups() throws Exception
    {
        initialize("commonConfig");
        StrSubstitutor strsub = logger.getContext().getConfiguration().getStrSubstitutor();

        // these assertions just use regexes, rely on the underlying subtitutions tests for correctness
        assertRegex("\\d{14}",          strsub.replace("${awslogs:startupTimestamp}"));
        assertRegex("\\d{1,6}",         strsub.replace("${awslogs:pid}"));
        assertRegex("[A-Za-z0-9-.]+",   strsub.replace("${awslogs:hostname}"));
    }


    @Test
    public void testCompoundLookup() throws Exception
    {
        // this test uses a sysprop lookup because it's a different prefix from the Log4J2 form

        String propName = "testSyspropLookup" + UUID.randomUUID().toString();
        String propValue = "this is something";

        System.setProperty(propName, propValue);

        initialize("commonConfig");
        StrSubstitutor strsub = logger.getContext().getConfiguration().getStrSubstitutor();

        assertEquals("testing property that's defined",
                     propValue,
                     strsub.replace("${awslogs:sysprop:" + propName + "}"));

        assertEquals("testing property that's not defined",
                     "${awslogs:sysprop:" + propName + "x}",
                     strsub.replace("${awslogs:sysprop:" + propName + "x}"));

        assertEquals("testing property that has a default",
                     "defaultValue",
                     strsub.replace("${awslogs:sysprop:" + propName + "x:defaultValue}"));
    }
}
