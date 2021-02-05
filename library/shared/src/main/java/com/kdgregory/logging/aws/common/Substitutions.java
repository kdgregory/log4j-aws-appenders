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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;

import com.kdgregory.logging.aws.facade.FacadeFactory;
import com.kdgregory.logging.aws.facade.InfoFacade;


/**
 *  Performs substitutions. Users create a new instance for every group of strings
 *  that needs substitutions applied (otherwise the timestamp would become stale).
 *  See docs for a complete explanation of how substitions work.
 *  <p>
 *  All values are lazily retrieved. This includes an <code>InfoFacade</code>, used
 *  for any substitutions that require knowledge of the AWS environment.
 *  <p>
 *  Instances are thread-safe, but not thread-optimized (concurrent lazy retrieves of
 *  the same value are possible).
 */
public class Substitutions
{
    // provides access to bits of information about deployment environment
    private InfoFacade infoFacade;

    // all substitutors are created by constructor, most lazily compute their value
    private SequenceSubstitutor sequenceSubstitutor;
    private DateSubstitutor dateSubstitutor;
    private TimestampSubstitutor timestampSubstitutor;
    private HourlyTimestampSubstitutor hourlyTimestampSubstitutor;
    private StartupTimestampSubstitutor startupTimestampSubstitutor;
    private PidSubstitutor pidSubstitutor;
    private HostnameSubstitutor hostnameSubstitutor;
    private UUIDSubstitutor uuidSubstitutor;
    private SyspropSubstitutor syspropSubstitutor;
    private EnvarSubstitutor envarSubstitutor;
    private AwsAccountIdSubstitutor awsAccountIdSubstitutor;
    private EC2InstanceIdSubstitutor ec2InstanceIdSubstitutor;
    private EC2RegionSubstitutor ec2RegionSubstitutor;
    private SSMSubstitutor ssmSubstitutor;


    /**
     *  Base constructor, which allows configuration of the <code>InfoFacade</code>
     *  instance. This is intended for testing.
     */
    public Substitutions(Date now, int sequence, InfoFacade infoFacade)
    {
        this.infoFacade = infoFacade;

        RuntimeMXBean runtimeMx = ManagementFactory.getRuntimeMXBean();

        sequenceSubstitutor = new SequenceSubstitutor(sequence);
        dateSubstitutor = new DateSubstitutor(now);
        timestampSubstitutor = new TimestampSubstitutor(now);
        hourlyTimestampSubstitutor = new HourlyTimestampSubstitutor(now);
        startupTimestampSubstitutor = new StartupTimestampSubstitutor(runtimeMx);
        pidSubstitutor = new PidSubstitutor(runtimeMx);
        hostnameSubstitutor = new HostnameSubstitutor(runtimeMx);
        uuidSubstitutor = new UUIDSubstitutor();
        syspropSubstitutor = new SyspropSubstitutor();
        envarSubstitutor = new EnvarSubstitutor();
        awsAccountIdSubstitutor = new AwsAccountIdSubstitutor();
        ec2InstanceIdSubstitutor = new EC2InstanceIdSubstitutor();
        ec2RegionSubstitutor = new EC2RegionSubstitutor();
        ssmSubstitutor = new SSMSubstitutor();
    }


    /**
     *  Standard constructor, which retrieves an <code>InfoFacade</code> if and
     *  when it is needed.
     */
    public Substitutions(Date now, int sequence)
    {
        this(now, sequence, null);
    }

//----------------------------------------------------------------------------
//  Public methods
//----------------------------------------------------------------------------

    /**
     *  Applies all substitutions. This is not particularly performant, but it
     *  won't be called frequently. If passed <code>null</code> returns same.
     */
    public String perform(String input)
    {
        if (input == null)
            return null;

        StringBuilder sb = new StringBuilder(512);
        for (String token : tokenize(input))
        {
            if (token.startsWith("{"))
            {
                sb.append(
                        sequenceSubstitutor.perform(
                        dateSubstitutor.perform(
                        timestampSubstitutor.perform(
                        hourlyTimestampSubstitutor.perform(
                        startupTimestampSubstitutor.perform(
                        pidSubstitutor.perform(
                        hostnameSubstitutor.perform(
                        uuidSubstitutor.perform(
                        syspropSubstitutor.perform(
                        envarSubstitutor.perform(
                        awsAccountIdSubstitutor.perform(
                        ec2InstanceIdSubstitutor.perform(
                        ec2RegionSubstitutor.perform(
                        ssmSubstitutor.perform(
                        token)))))))))))))));
            }
            else
            {
                sb.append(token);
            }
        }

        return sb.toString();
    }

//----------------------------------------------------------------------------
//  Substitutors
//----------------------------------------------------------------------------

    /**
     *  Contains all of the boilerplate for various substitutions.
     *  <p>
     *  Subclasses provide the tags that they recognize, either as a full tag
     *  (opening and closing braces) or as a leading component ("{tag:") for
     *  those tags that support embedded lookups. The {@link #perform} method
     *  tries to match each of the tags in turn (most subclasses only provide
     *  one, but some that support legacy tags provide multiple).
     *  <p>
     *  If a tag is matched, <code>perform()</code> next looks for a cached
     *  value. If one is set, then that value is returned.
     *  <p>
     *  If there's no cached value, <code>retrieveValue()</code> is called, and
     *  its result is returned. It will be provided with any embedded tags, and
     *  is permitted to cache the value.
     */
    private abstract class AbstractSubstitutor
    {
        private List<String> tags;
        protected String cachedValue;

        public AbstractSubstitutor(String... tags)
        {
            this.tags = Arrays.asList(tags);
        }

        public String perform(String input)
        {
            if (input == null)
                return "";

            String result = null;
            for (Iterator<String> itx = tags.iterator() ; itx.hasNext() && (result == null) ; )
            {
                String tag = itx.next();
                if (input.equals(tag))
                {
                    result = cachedValue != null
                           ? cachedValue
                           : retrieveValue(null);
                }
                else if (input.startsWith(tag))
                {
                    result = cachedValue != null
                           ? cachedValue
                           : trySubstitution(input, tag);
                }
            }

            return (result == null) ? input : result;
        }

        private String trySubstitution(String input, String tag)
        {
            String embeddedKey = input.replace(tag, "").replace("}", "");
            String defaultValue = "";
            int splitPoint = embeddedKey.indexOf(':');
            if (splitPoint >= 0)
            {
                defaultValue = embeddedKey.substring(splitPoint + 1);
                embeddedKey = embeddedKey.substring(0, splitPoint);
            }

            String result = retrieveValue(embeddedKey);
            if (result != null)
                return result;

            return defaultValue.isEmpty() ? null : defaultValue;
        }

        // some subclasses set the cached value in their constructor; rather than
        // force them to implement an abstract method, they'll use this (and the
        // null return should cause any tests to go red if they don't cache)
        protected String retrieveValue(String embeddedKey)
        {
            return null;
        }
    }


    private class SequenceSubstitutor
    extends AbstractSubstitutor
    {
        public SequenceSubstitutor(int sequence)
        {
            super("{sequence}");
            cachedValue = String.valueOf(sequence);
        }
    }


    private class DateSubstitutor
    extends AbstractSubstitutor
    {
        private Date now;

        public DateSubstitutor(Date now)
        {
            super("{date}");
            this.now = now;
        }

        @Override
        protected String retrieveValue(String ignored)
        {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            cachedValue = formatter.format(now);
            return cachedValue;
        }
    }


    private class TimestampSubstitutor
    extends AbstractSubstitutor
    {
        private Date now;

        public TimestampSubstitutor(Date now)
        {
            super("{timestamp}");
            this.now = now;
        }

        @Override
        protected String retrieveValue(String ignored)
        {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            cachedValue = formatter.format(now);
            return cachedValue;
        }
    }


    private class HourlyTimestampSubstitutor
    extends AbstractSubstitutor
    {
        private Date now;

        public HourlyTimestampSubstitutor(Date now)
        {
            super("{hourlyTimestamp}");
            this.now = now;
        }

        @Override
        protected String retrieveValue(String ignored)
        {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHH'0000'");
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            cachedValue = formatter.format(now);
            return cachedValue;
        }
    }


    private class StartupTimestampSubstitutor
    extends AbstractSubstitutor
    {
        private RuntimeMXBean runtimeMx;

        public StartupTimestampSubstitutor(RuntimeMXBean runtimeMx)
        {
            super("{startupTimestamp}");
            this.runtimeMx = runtimeMx;
        }

        @Override
        protected String retrieveValue(String ignored)
        {
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
            formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
            cachedValue = formatter.format(new Date(runtimeMx.getStartTime()));
            return cachedValue;
        }
    }


    private class PidSubstitutor
    extends AbstractSubstitutor
    {
        private RuntimeMXBean runtimeMx;

        public PidSubstitutor(RuntimeMXBean runtimeMx)
        {
            super("{pid}");
            this.runtimeMx = runtimeMx;
        }

        @Override
        protected String retrieveValue(String ignored)
        {
            String vmName = runtimeMx.getName();
            cachedValue = (vmName.indexOf('@') > 0)
                        ? vmName.substring(0, vmName.indexOf('@'))
                        : "unknown";
            return cachedValue;
        }
    }


    private class HostnameSubstitutor
    extends AbstractSubstitutor
    {
        private RuntimeMXBean runtimeMx;

        public HostnameSubstitutor(RuntimeMXBean runtimeMx)
        {
            super("{hostname}");
            this.runtimeMx = runtimeMx;
        }

        @Override
        protected String retrieveValue(String ignored)
        {
            String vmName = runtimeMx.getName();
            cachedValue = (vmName.indexOf('@') > 0)
                        ? vmName.substring(vmName.indexOf('@') + 1)
                        : "unknown";
            return cachedValue;
        }
    }


    private class UUIDSubstitutor
    extends AbstractSubstitutor
    {
        public UUIDSubstitutor()
        {
            super("{uuid}");
        }

        @Override
        protected String retrieveValue(String ignored)
        {
            return UUID.randomUUID().toString();
        }
    }


    private class SyspropSubstitutor
    extends AbstractSubstitutor
    {
        public SyspropSubstitutor()
        {
            super("{sysprop:");
        }

        @Override
        protected String retrieveValue(String name)
        {
            if ((name == null) || name.isEmpty())
                return null;

            return System.getProperty(name);
        }
    }


    private class EnvarSubstitutor
    extends AbstractSubstitutor
    {
        public EnvarSubstitutor()
        {
            super("{env:");
        }

        @Override
        protected String retrieveValue(String name)
        {
            if ((name == null) || name.isEmpty())
                return null;

            return System.getenv(name);
        }
    }


    private class AwsAccountIdSubstitutor
    extends AbstractSubstitutor
    {
        public AwsAccountIdSubstitutor()
        {
            super("{aws:accountId}");
        }

        @Override
        protected String retrieveValue(String ignored)
        {
            cachedValue = infoFacade().retrieveAccountId();
            return cachedValue;
        }
    }


    private class EC2InstanceIdSubstitutor
    extends AbstractSubstitutor
    {
        public EC2InstanceIdSubstitutor()
        {
            super("{ec2:instanceId}", "{instanceId}");
        }

        @Override
        protected String retrieveValue(String ignored)
        {
            cachedValue = infoFacade().retrieveEC2InstanceId();
            return cachedValue;
        }
    }


    private class EC2RegionSubstitutor
    extends AbstractSubstitutor
    {
        public EC2RegionSubstitutor()
        {
            super("{ec2:region}");
        }

        @Override
        protected String retrieveValue(String ignored)
        {
            cachedValue = infoFacade().retrieveEC2Region();
            return cachedValue;
        }
    }


    private class SSMSubstitutor
    extends AbstractSubstitutor
    {
        public SSMSubstitutor()
        {
            super("{ssm:");
        }

        @Override
        protected String retrieveValue(String name)
        {
            if ((name == null) || name.isEmpty())
                return null;

            return infoFacade().retrieveParameter(name);
        }
    }

//----------------------------------------------------------------------------
//  Other internals
//----------------------------------------------------------------------------

    /**
     *  Returns/creates the <code>InfoFacade</code>. Used only by substitutors
     *  that need access to the AWS environment.
     */
    private InfoFacade infoFacade()
    {
        if (infoFacade == null)
        {
            infoFacade = FacadeFactory.createFacade(InfoFacade.class);
        }
        return infoFacade;
    }


    /**
     *  Breaks the input string into components that are either substitutions or
     *  literal text.
     */
    private List<String> tokenize(String input)
    {
        List<String> result = new ArrayList<>();
        int ii = 0;
        while (ii < input.length())
        {
            int next = input.indexOf('{', ii);
            if (next < 0)
            {
                result.add(input.substring(ii));
                break;
            }
            else
            {
                result.add(input.substring(ii, next));
                ii = input.indexOf('}', next);
                if (ii < 0)
                {
                    result.add(input.substring(next));
                    break;
                }
                else
                {
                    ii++;  // because it's pointing at the closing brace now
                    result.add(input.substring(next, ii));
                }
            }
        }
        return result;
    }
}
