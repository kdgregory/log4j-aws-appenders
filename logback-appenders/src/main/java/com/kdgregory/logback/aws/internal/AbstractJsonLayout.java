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

package com.kdgregory.logback.aws.internal;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import com.kdgregory.logging.aws.common.Substitutions;
import com.kdgregory.logging.common.util.JsonConverter;

import ch.qos.logback.core.LayoutBase;


/**
 *  Common implementation for <code>JSONLayout</code> and <code>JSONAccessLayout</code>.
 */
public abstract class AbstractJsonLayout<E>
extends LayoutBase<E>
{
    // if enabled and supported, these will be not-null
    private String processId;
    private String hostname;
    private String instanceId;
    private Map<String,String> tags;

    private ThreadLocal<JsonConverter> converterTL = new ThreadLocal<JsonConverter>()
    {
        @Override
        protected JsonConverter initialValue()
        {
            return new JsonConverter();
        }
    };

//----------------------------------------------------------------------------
//  Configuration
//----------------------------------------------------------------------------

    private boolean appendNewlines;
    private boolean enableHostname;
    private boolean enableInstanceId;
    private String rawTags;


    public void setAppendNewlines(boolean value)
    {
        appendNewlines = value;
    }


    public boolean getAppendNewlines()
    {
        return appendNewlines;
    }


    public void setEnableInstanceId(boolean value)
    {
        enableInstanceId = value;
    }


    public boolean getEnableInstanceId()
    {
        return enableInstanceId;
    }


    public void setEnableHostname(boolean value)
    {
        enableHostname = value;
    }


    public boolean getEnableHostname()
    {
        return enableHostname;
    }


    public void setTags(String value)
    {
        rawTags = value;
    }


    public String getTags()
    {
        return rawTags;
    }

//----------------------------------------------------------------------------
//  Layout Overrides
//----------------------------------------------------------------------------

    @Override
    public void start()
    {
        Substitutions subs = new Substitutions(new Date(), 0);

        processId = subs.perform("{pid}");
        if ("unknown".equals(processId))
            processId = null;

        if (enableHostname)
        {
            hostname = subs.perform("{hostname}");
            if ("unknown".equals(hostname))
                hostname = null;
        }

        if (enableInstanceId)
        {
            instanceId = subs.perform("{instanceId}");
            if ("{instanceId}".equals(instanceId))
                instanceId = null;
        }

        if ((rawTags != null) && !rawTags.isEmpty())
        {
            tags = new TreeMap<String,String>();
            for (String tagdef : rawTags.split(","))
            {
                String[] splitdef = tagdef.split("=");
                if (splitdef.length == 2)
                {
                    tags.put(splitdef[0], subs.perform(splitdef[1]));
                }
                else
                {
                    throw new IllegalArgumentException("invalid tag definition: " + tagdef);
                }
            }
        }
        super.start();
    }


    @Override
    public void stop()
    {
        converterTL.remove();
        super.stop();
    }


//----------------------------------------------------------------------------
//  Helpers for subclass
//----------------------------------------------------------------------------

    /**
     *  Adds common attributes to the passed map and converts it to a JSON string.
     */
    protected String addCommonAttributesAndConvert(Map<String,Object> map)
    {

        if (processId != null)  map.put("processId", processId);
        if (hostname != null)   map.put("hostname", hostname);
        if (instanceId != null) map.put("instanceId", instanceId);

        if (tags != null)       map.put("tags",         tags);

        String json = converterTL.get().convert(map);
        if (getAppendNewlines())
        {
            json += "\n";
        }

        return json;
    }
}
