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

import com.kdgregory.logging.aws.internal.facade.FacadeFactory;
import com.kdgregory.logging.aws.internal.facade.InfoFacade;


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

        String output = input;
        do
        {
            input = output;
            output = sequenceSubstitutor.perform(
                     dateSubstitutor.perform(
                     timestampSubstitutor.perform(
                     hourlyTimestampSubstitutor.perform(
                     startupTimestampSubstitutor.perform(
                     pidSubstitutor.perform(
                     hostnameSubstitutor.perform(
                     syspropSubstitutor.perform(
                     envarSubstitutor.perform(
                     awsAccountIdSubstitutor.perform(
                     ec2InstanceIdSubstitutor.perform(
                     ec2RegionSubstitutor.perform(
                     ssmSubstitutor.perform(
                     input)))))))))))));
        }
        while (! output.equals(input));
        return output;
    }

//----------------------------------------------------------------------------
//  Substitutors
//----------------------------------------------------------------------------

    /**
     *  Contains all of the boilerplate for various substitutions.
     *  <p>
     *  Construct with either the full tag ("{foo}") or (for tags with embedded
     *  keys) the leading part of the tag ("{foo:").
     *  <p>
     *  The subclass must implement {@link #retrieveValue}, which is called with
     *  any embedded tag or default (either or both of which may be null). It is
     *  responsible for retrieving the value. If unable to retrieve the value,
     *  it may return the default.
     *  <p>
     *  The subclass can also choose to cache retrieved values, to handle the
     *  (unlikely) case where the same substitution is performed multiple times.
     *  The cache variable is stored in the superclass (to avoid boilerplate),
     *  and the subclass is responsible for setting it.
     */
    private abstract class AbstractSubstitutor
    {
        private String[] tags;
        protected String cachedValue;

        public AbstractSubstitutor(String... tags)
        {
            this.tags = tags;
        }

        public String perform(String input)
        {
            if (input == null)
                return "";

            for (String tag : tags)
            {
                input = trySubstitution(input, tag);
            }

            return input;
        }

        private String trySubstitution(String input, String tag)
        {
            int start = input.indexOf(tag);
            if (start < 0)
                return input;

            int end = input.indexOf("}", start) + 1;

            String value = cachedValue;
            if (value == null)
            {
                String actualTag = input.substring(start, end);
                String embeddedKey = actualTag.replace(tag, "").replace("}", "");
                String defaultValue = null;
                int splitPoint = embeddedKey.indexOf(':');
                if (splitPoint >= 0)
                {
                    defaultValue = embeddedKey.substring(splitPoint + 1);
                    embeddedKey = embeddedKey.substring(0, splitPoint);
                }

                value = retrieveValue(embeddedKey);
                if ((value == null) || value.isEmpty())
                    value = defaultValue;
            }

            if ((value == null) || value.isEmpty())
                return input;

            return input.substring(0, start) + value + input.substring(end);
        }

        protected String retrieveValue(String embeddedKey)
        {
            // this won't be called if cachedValue is null
            return cachedValue;
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
}
